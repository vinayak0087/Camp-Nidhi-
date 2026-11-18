package com.example.domain

import com.example.data.model.AdminGovernanceConfig
import com.example.data.model.BillExtraction
import com.example.data.model.Camp
import com.example.data.model.ExpenseRecord
import com.example.data.model.FacultyProfile
import com.example.data.model.InformationShare
import com.example.data.model.TemporaryAccessGrant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GovernanceAndAnomalyTest {

    @Test
    fun `primary admin and co-admin identities are strictly distinguished`() {
        assertTrue(AdminGovernanceConfig.isPrimaryAdminEmail("2501721049055@mcc.edu.in"))
        assertTrue(AdminGovernanceConfig.isPrimaryAdminEmail(" 2501721049055@MCC.EDU.IN "))
        assertFalse(AdminGovernanceConfig.isPrimaryAdminEmail("2501721049029@mcc.edu.in"))

        assertTrue(AdminGovernanceConfig.isCoAdminEmail("2501721049029@mcc.edu.in"))
        assertFalse(AdminGovernanceConfig.isCoAdminEmail("2501721049055@mcc.edu.in"))
        assertFalse(AdminGovernanceConfig.isCoAdminEmail("viewer@example.com"))
    }

    @Test
    fun `co-admin temporary access grant auto-locks on expiry or revocation`() {
        val now = 1_750_000_000_000L
        val activeGrant = TemporaryAccessGrant(
            status = "APPROVED",
            revoked = false,
            startsAtMillis = now - 60_000L,
            expiresAtMillis = now + 60_000L
        )
        assertTrue(activeGrant.isCurrentlyValid(now))

        val expiredGrant = activeGrant.copy(expiresAtMillis = now - 1L)
        assertFalse(expiredGrant.isCurrentlyValid(now))

        val revokedGrant = activeGrant.copy(revoked = true)
        assertFalse(revokedGrant.isCurrentlyValid(now))

        val pendingGrant = activeGrant.copy(status = "PENDING")
        assertFalse(pendingGrant.isCurrentlyValid(now))
    }

    @Test
    fun `faculty profile and information share honor enable, revocation, and expiry windows`() {
        val now = 1_750_000_000_000L
        val disabledFaculty = FacultyProfile(
            id = "faculty_01",
            assignedEmail = "faculty1@mcc.edu.in",
            isEnabled = false,
            status = "DISABLED"
        )
        assertFalse(disabledFaculty.isActiveNow(now))

        val activeFaculty = disabledFaculty.copy(
            isEnabled = true,
            status = "APPROVED",
            expiresAtMillis = now + 3_600_000L
        )
        assertTrue(activeFaculty.isActiveNow(now))
        assertFalse(activeFaculty.copy(expiresAtMillis = now - 10L).isActiveNow(now))

        val activeShare = InformationShare(
            id = "share_abc",
            recipientEmail = "guest@mcc.edu.in",
            status = "APPROVED",
            revoked = false,
            expiresAtMillis = now + 3_600_000L
        )
        assertTrue(activeShare.isActiveNow(now))
        assertFalse(activeShare.copy(revoked = true).isActiveNow(now))
        assertFalse(activeShare.copy(expiresAtMillis = now - 5L).isActiveNow(now))
    }

    @Test
    fun `bill anomaly detector flags arithmetic mismatches, duplicates, low confidence, and high amounts`() {
        val camp = Camp(id = "c1", name = "Rural Camp", approvalLimit = 5_000.0)
        val existingBills = listOf(
            BillExtraction(
                id = "b_old",
                billVerificationId = "BV-1001-XYZ",
                vendorName = "Annapurna Traders",
                billNumber = "INV-900",
                billDate = "12/05/2026",
                grandTotal = 2_400.0,
                fileHashSha256 = "hash_abc_123"
            )
        )
        val existingExpenses = listOf(
            ExpenseRecord(id = "e1", amount = 1_000.0)
        )

        val warnings = BillAnomalyDetector.analyze(
            vendorName = "Annapurna Traders",
            billNumber = "INV-900",
            billDate = "12/05/2026",
            quantity = 10.0,
            unitPrice = 200.0, // 2000 expected vs 2500 subtotal
            subtotal = 2_500.0,
            gstAmount = 100.0,
            discount = 0.0,
            grandTotal = 6_000.0, // 2600 expected vs 6000 grand total + > 5000 limit
            confidenceLevel = "LOW",
            uncertainFields = listOf("grandTotal"),
            fileHashSha256 = "hash_abc_123",
            camp = camp,
            existingBills = existingBills,
            existingExpenses = existingExpenses
        )

        assertTrue(warnings.any { it.contains("Low-confidence OCR", ignoreCase = true) })
        assertTrue(warnings.any { it.contains("Arithmetic mismatch", ignoreCase = true) })
        assertTrue(warnings.any { it.contains("Duplicate document file", ignoreCase = true) })
        assertTrue(warnings.any { it.contains("Duplicate invoice number", ignoreCase = true) })
        assertTrue(warnings.any { it.contains("exceeds camp approval limit", ignoreCase = true) })
    }
}
