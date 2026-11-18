package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Camp
import com.example.data.model.CampStatus
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import com.example.data.model.PaymentMethod
import com.example.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CampNidhiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createCamp_recordIncomeAndExpense_approveAndPay_succeedsForOwner() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID()}@test.com")
        val repo = CampNidhiRepository(firestore, auth)

        val campId = withTimeout(TIMEOUT_MS) {
            repo.createCampWithDefaults(
                Camp(
                    name = "Test Rural Camp",
                    institution = "NSS Unit",
                    location = "Madurai",
                    startDateMillis = System.currentTimeMillis(),
                    endDateMillis = System.currentTimeMillis() + 5 * 86_400_000L,
                    participantCount = 40,
                    approvedBudget = 80000.0,
                    status = CampStatus.ACTIVE.name,
                    requireIndependentApproval = false
                )
            )
        }
        assertTrue(campId.isNotBlank())

        val camps = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCamps().first { list -> list.any { it.id == campId } }
        }
        val camp = camps.first { it.id == campId }

        val committees = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCommittees(campId).first { it.isNotEmpty() }
        }
        assertTrue(committees.size >= 11)
        val foodCommittee = committees.first { it.name.contains("Food") }

        val incomeId = withTimeout(TIMEOUT_MS) {
            repo.saveIncome(
                camp = camp,
                income = IncomeRecord(
                    dateMillis = System.currentTimeMillis(),
                    category = "Student Contributions",
                    source = "Batch Fee",
                    promisedAmount = 20000.0,
                    receivedAmount = 15000.0,
                    paymentMethod = PaymentMethod.UPI.name
                ),
                actorRole = UserRole.SYSTEM_ADMIN
            )
        }
        assertTrue(incomeId.isNotBlank())

        val expenseId = withTimeout(TIMEOUT_MS) {
            repo.saveExpense(
                camp = camp,
                expense = ExpenseRecord(
                    dateMillis = System.currentTimeMillis(),
                    committeeId = foodCommittee.id,
                    committeeName = foodCommittee.name,
                    category = "Groceries",
                    description = "Rice bags",
                    vendorName = "Kaveri Stores",
                    amount = 4500.0,
                    paymentMethod = PaymentMethod.UPI.name,
                    paymentStatus = ExpensePaymentStatus.SUBMITTED.name
                ),
                existingExpense = null,
                actorRole = UserRole.SYSTEM_ADMIN
            )
        }
        assertTrue(expenseId.isNotBlank())

        val expenses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExpenses(campId).first { list -> list.any { it.id == expenseId } }
        }
        val savedExpense = expenses.first { it.id == expenseId }

        withTimeout(TIMEOUT_MS) {
            repo.approveOrRejectExpense(camp, savedExpense, true, "Verified", UserRole.SYSTEM_ADMIN)
        }
        withTimeout(TIMEOUT_MS) {
            repo.recordExpensePayment(
                camp = camp,
                expense = savedExpense.copy(paymentStatus = ExpensePaymentStatus.APPROVED.name),
                paidAmountNow = 4500.0,
                paymentMethod = PaymentMethod.UPI,
                paymentReference = "UPI-123",
                sourceIncomeCode = "INC-101",
                actorRole = UserRole.SYSTEM_ADMIN
            )
        }

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAuditLogs(campId).first { it.size >= 3 }
        }
        assertTrue(logs.isNotEmpty())
    }

    @Test
    fun observeCamps_crossUserAccess_isolatedByRules() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = CampNidhiRepository(firestore, auth)
        val campId = withTimeout(TIMEOUT_MS) {
            aliceRepo.createCampWithDefaults(
                Camp(
                    name = "Alice Private Camp",
                    participantCount = 25,
                    approvedBudget = 50000.0
                ),
                createDefaultCommittees = false
            )
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = CampNidhiRepository(firestore, auth)
        val bobCamps = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeCamps().first()
        }
        assertTrue(bobCamps.none { it.id == campId && aliceUid.isNotEmpty() })
    }

    @Test
    fun observeCamps_unauthenticatedUser_failsWithIllegalStateOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CampNidhiRepository(firestore, auth)
        var threw = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCamps().first()
            }
        } catch (e: Exception) {
            threw = true
            val fsEx = e.unwrapFirestoreException()
            assertTrue(fsEx == null || fsEx.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        assertTrue(threw)
    }

    @Test
    fun directUnauthorizedRead_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        var code: FirebaseFirestoreException.Code? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("camps").document("non_existent_camp").get()
                    .let { com.google.android.gms.tasks.Tasks.await(it) }
            }
        } catch (e: Exception) {
            code = e.unwrapFirestoreException()?.code
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, code)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
