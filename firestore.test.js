const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const TREASURER_UID = "treasurer_uid_002";
const LEADER_UID = "leader_uid_003";
const AUDITOR_UID = "auditor_uid_004";
const OUTSIDER_UID = "outsider_uid_999";
const CAMP_ID = "camp_rural_2026";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCampPayload(ownerId = ADMIN_UID, memberUids = [ADMIN_UID, TREASURER_UID, LEADER_UID, AUDITOR_UID]) {
  return {
    ownerId,
    memberUids,
    name: "Rural Health & Education Camp 2026",
    institution: "NSS Unit - Madurai",
    location: "Melur Block, Madurai",
    startDateMillis: 1794700800000,
    endDateMillis: 1795305600000,
    participantCount: 60,
    coordinatorName: "Dr. Aravind",
    financeLeaderName: "Karthik S.",
    approvedBudget: 120000,
    status: "ACTIVE",
    approvalLimit: 5000,
    requireIndependentApproval: true,
    isDemoData: false,
    incomeCategories: ["Student Contributions", "Donations"],
    expenseCategories: ["Food", "Travel", "Medical"],
    treasurerDeclaration: "All accounts verified.",
    auditorRemarks: "",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

async function seedCampWithRoles() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const members = [ADMIN_UID, TREASURER_UID, LEADER_UID, AUDITOR_UID];
    await db.collection("camps").doc(CAMP_ID).set({
      ...validCampPayload(ADMIN_UID, members),
      createdAt: new Date(Date.now() - 60000),
      updatedAt: new Date(Date.now() - 60000),
    });

    const roleDefs = [
      { uid: ADMIN_UID, name: "Admin User", role: "SYSTEM_ADMIN" },
      { uid: TREASURER_UID, name: "Treasurer User", role: "TREASURER" },
      { uid: LEADER_UID, name: "Leader User", role: "COMMITTEE_LEADER" },
      { uid: AUDITOR_UID, name: "Auditor User", role: "AUDITOR" },
    ];
    for (const r of roleDefs) {
      await db.collection("camps").doc(CAMP_ID).collection("members").doc(r.uid).set({
        campId: CAMP_ID,
        ownerId: ADMIN_UID,
        memberUids: members,
        memberUid: r.uid,
        displayName: r.name,
        email: `${r.uid}@example.com`,
        role: r.role,
        assignedCommitteeIds: ["comm_food"],
        isActive: true,
        isDemoData: false,
        createdAt: new Date(Date.now() - 60000),
        updatedAt: new Date(Date.now() - 60000),
      });
    }
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write camps or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("camps").get());
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("camps").doc(CAMP_ID).set(validCampPayload()));
});

test("User profile: owner can create/update own profile; cross-user access denied", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Admin",
      email: "admin@campnidhi.org",
      preferredLanguage: "en",
      themeMode: "LIGHT",
      activeCampId: CAMP_ID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(outsiderDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(
    outsiderDb.collection("users").doc(ADMIN_UID).update({ displayName: "Hacked", updatedAt: pastTimestamp() })
  );
});

test("Camp creation & client query alignment: owner can create and query with array-contains", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(adminDb.collection("camps").doc(CAMP_ID).set(validCampPayload()));

  // Matching filter query succeeds
  await assertSucceeds(
    adminDb.collection("camps").where("memberUids", "array-contains", ADMIN_UID).get()
  );

  // Unfiltered list query fails (rules are not filters)
  await assertFails(adminDb.collection("camps").get());

  // Outsider cannot read camp
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsiderDb.collection("camps").doc(CAMP_ID).get());
  await assertFails(
    outsiderDb.collection("camps").where("memberUids", "array-contains", ADMIN_UID).get()
  );
});

test("Dirty Dozen: rejects ghost field, invalid ID, negative budget, and spoofing ownerId", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Ghost field rejected
  await assertFails(
    adminDb.collection("camps").doc(CAMP_ID).set({
      ...validCampPayload(),
      ghostAdminOverride: true,
    })
  );

  // Negative budget rejected
  await assertFails(
    adminDb.collection("camps").doc(CAMP_ID).set({
      ...validCampPayload(),
      approvedBudget: -500,
    })
  );

  // Identity spoofing rejected
  await assertFails(
    adminDb.collection("camps").doc(CAMP_ID).set({
      ...validCampPayload(OUTSIDER_UID, [OUTSIDER_UID]),
    })
  );

  // Invalid doc ID rejected
  await assertFails(
    adminDb.collection("camps").doc("bad id with spaces!").set(validCampPayload())
  );
});

test("Role-based expense workflow & independent approval enforcement", async () => {
  await seedCampWithRoles();

  const members = [ADMIN_UID, TREASURER_UID, LEADER_UID, AUDITOR_UID];
  const leaderDb = testEnv.authenticatedContext(LEADER_UID).firestore();
  const treasurerDb = testEnv.authenticatedContext(TREASURER_UID).firestore();
  const auditorDb = testEnv.authenticatedContext(AUDITOR_UID).firestore();

  const baseExpense = {
    campId: CAMP_ID,
    ownerId: ADMIN_UID,
    memberUids: members,
    expenseCode: "EXP-001",
    dateMillis: 1794700800000,
    committeeId: "comm_food",
    committeeName: "Food Committee",
    category: "Groceries",
    description: "Rice and lentils for 60 participants",
    vendorName: "Sri Murugan Traders",
    amount: 4500,
    paidAmount: 0,
    paymentMethod: "UPI",
    paymentStatus: "SUBMITTED",
    receiptVerified: false,
    submittedByUid: LEADER_UID,
    submittedByName: "Leader User",
    isDemoData: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };

  // Committee leader cannot create pre-approved expense
  await assertFails(
    leaderDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_bad").set({
      ...baseExpense,
      paymentStatus: "APPROVED",
    })
  );

  // Committee leader CAN submit expense
  await assertSucceeds(
    leaderDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_001").set(baseExpense)
  );

  // Committee leader CANNOT self-approve
  await assertFails(
    leaderDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_001").update({
      paymentStatus: "APPROVED",
      approvedByUid: LEADER_UID,
      approvedByName: "Leader User",
      updatedAt: pastTimestamp(),
    })
  );

  // Auditor CANNOT change expense amount or status
  await assertFails(
    auditorDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_001").update({
      amount: 10,
      updatedAt: pastTimestamp(),
    })
  );

  // Auditor CAN verify receipt & add remarks
  await assertSucceeds(
    auditorDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_001").update({
      receiptVerified: true,
      receiptVerifiedBy: "Auditor User",
      remarks: "Bill matches vendor GST receipt.",
      updatedAt: pastTimestamp(),
    })
  );

  // Treasurer CAN approve another member's submitted expense
  await assertSucceeds(
    treasurerDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_001").update({
      paymentStatus: "APPROVED",
      approvedByUid: TREASURER_UID,
      approvedByName: "Treasurer User",
      updatedAt: pastTimestamp(),
    })
  );

  // Treasurer CANNOT self-approve when requireIndependentApproval == true
  await assertSucceeds(
    treasurerDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_self").set({
      ...baseExpense,
      expenseCode: "EXP-002",
      submittedByUid: TREASURER_UID,
      submittedByName: "Treasurer User",
      paymentStatus: "SUBMITTED",
    })
  );
  await assertFails(
    treasurerDb.collection("camps").doc(CAMP_ID).collection("expenses").doc("exp_self").update({
      paymentStatus: "APPROVED",
      approvedByUid: TREASURER_UID,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Audit logs are strictly append-only (cannot be updated or deleted for real data)", async () => {
  await seedCampWithRoles();
  const members = [ADMIN_UID, TREASURER_UID, LEADER_UID, AUDITOR_UID];
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  const logRef = adminDb.collection("camps").doc(CAMP_ID).collection("audit_logs").doc("log_001");
  await assertSucceeds(
    logRef.set({
      campId: CAMP_ID,
      ownerId: ADMIN_UID,
      memberUids: members,
      entityType: "EXPENSE",
      entityId: "exp_001",
      entityCode: "EXP-001",
      action: "CREATE",
      summary: "Created expense EXP-001",
      actorUid: ADMIN_UID,
      actorName: "Admin User",
      actorRole: "SYSTEM_ADMIN",
      isDemoData: false,
      createdAt: pastTimestamp(),
    })
  );

  await assertFails(logRef.update({ summary: "Tampered" }));
  await assertFails(logRef.delete());
});

test("Modules 1-6: Primary Admin vs Co-Admin pending/grant/expiry, immutable bills, faculty & viewer isolation", async () => {
  await seedCampWithRoles();

  const primaryAdminDb = testEnv.authenticatedContext("primary_uid", {
    email: "2501721049055@mcc.edu.in",
  }).firestore();
  const coAdminDb = testEnv.authenticatedContext("coadmin_uid", {
    email: "2501721049029@mcc.edu.in",
  }).firestore();
  const facultyDb = testEnv.authenticatedContext("faculty_uid", {
    email: "faculty01@mcc.edu.in",
  }).firestore();
  const viewerDb = testEnv.authenticatedContext("viewer_uid", {
    email: "guestviewer@mcc.edu.in",
  }).firestore();

  // 1. Co-Admin has ZERO camp access by default before grant
  await assertFails(coAdminDb.collection("camps").doc(CAMP_ID).get());
  await assertSucceeds(primaryAdminDb.collection("admin_settings").doc("global_security").get());

  // Co-Admin cannot grant themselves access
  await assertFails(
    coAdminDb.collection("temporary_access_grants").doc("coadmin_active_grant").set({
      targetEmail: "2501721049029@mcc.edu.in",
      status: "APPROVED",
      revoked: false,
      allowedModules: ["DASHBOARD", "BILL_VERIFICATION"],
      startsAtMillis: Date.now() - 1000,
      expiresAtMillis: Date.now() + 600000,
    })
  );

  // Primary Admin grants temporary Co-Admin BILL_VERIFICATION (read-only)
  await assertSucceeds(
    primaryAdminDb.collection("temporary_access_grants").doc("coadmin_active_grant").set({
      id: "coadmin_active_grant",
      targetEmail: "2501721049029@mcc.edu.in",
      status: "APPROVED",
      revoked: false,
      allowedModules: ["BILL_VERIFICATION"],
      canRead: true,
      canEdit: false,
      canApprove: false,
      financialLimit: 2000,
      startsAtMillis: Date.now() - 5000,
      expiresAtMillis: Date.now() + 600000,
    })
  );

  // Co-Admin CAN now read camp context, CANNOT edit committees/incomes when canEdit=false
  await assertSucceeds(coAdminDb.collection("camps").doc(CAMP_ID).get());
  await assertFails(
    coAdminDb.collection("camps").doc(CAMP_ID).collection("committees").doc("c_bad").set({
      campId: CAMP_ID,
      ownerId: ADMIN_UID,
      memberUids: [ADMIN_UID],
      name: "Unauthorized Committee",
      allocatedBudget: 1000,
      isDefault: false,
      isDemoData: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // 2. Original bill preservation: immutable=true on create, update always forbidden, Primary Admin exclusive bill verification
  const leaderDb = testEnv.authenticatedContext(LEADER_UID).firestore();
  await assertSucceeds(
    leaderDb.collection("bill_originals").doc("bill_001").set({
      billVerificationId: "BV-1001",
      uploaderUid: LEADER_UID,
      memberUids: [ADMIN_UID, LEADER_UID],
      immutable: true,
      isDemoData: false,
      createdAt: pastTimestamp(),
    })
  );
  await assertFails(leaderDb.collection("bill_originals").doc("bill_001").update({ fileName: "tampered.png" }));

  await assertSucceeds(
    leaderDb.collection("bill_extractions").doc("bill_001").set({
      billOriginalId: "bill_001",
      submittedByUid: LEADER_UID,
      memberUids: [ADMIN_UID, LEADER_UID],
      verificationStatus: "SUBMITTED",
      grandTotal: 1500,
      isDemoData: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Non-Primary Admin CANNOT verify a bill
  await assertFails(
    leaderDb.collection("bill_extractions").doc("bill_001").update({
      verificationStatus: "VERIFIED",
      updatedAt: pastTimestamp(),
    })
  );
  // Primary Admin CAN verify the bill
  await assertSucceeds(
    primaryAdminDb.collection("bill_extractions").doc("bill_001").update({
      verificationStatus: "VERIFIED",
      updatedAt: pastTimestamp(),
    })
  );

  // 3. Unapproved viewer has zero access to camps, bills, or casework
  await assertFails(viewerDb.collection("camps").doc(CAMP_ID).get());
  await assertFails(viewerDb.collection("bill_extractions").doc("bill_001").get());
  await assertFails(facultyDb.collection("camps").doc(CAMP_ID).get());
});

