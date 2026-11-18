package com.example.domain

import com.example.data.model.Camp
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCalculatorTest {

    @Test
    fun `promised unpaid income is never counted as funds received`() {
        val camp = Camp(id = "c1", name = "Rural Camp", participantCount = 50, approvedBudget = 50_000.0)
        val incomes = listOf(
            IncomeRecord(
                id = "i1",
                category = "Student Contributions",
                promisedAmount = 10_000.0,
                receivedAmount = 4_000.0
            ),
            IncomeRecord(
                id = "i2",
                category = "Sponsorship",
                promisedAmount = 15_000.0,
                receivedAmount = 0.0
            )
        )

        val summary = FinanceCalculator.computeDashboard(camp, emptyList(), incomes, emptyList())

        assertEquals(25_000.0, summary.totalPromisedFunds, 0.01)
        assertEquals(4_000.0, summary.totalFundsReceived, 0.01)
        assertEquals(4_000.0, summary.totalParticipantContributions, 0.01)
        assertEquals(21_000.0, summary.outstandingContributions, 0.01)
        assertEquals(4_000.0, summary.remainingCashBalance, 0.01)
    }

    @Test
    fun `committee utilization, pending approvals, outstanding payments, and overspending alerts update accurately`() {
        val camp = Camp(
            id = "c1",
            name = "NSS Village Camp",
            participantCount = 40,
            approvedBudget = 40_000.0,
            startDateMillis = 1_700_000_000_000L,
            endDateMillis = 1_700_432_000_000L
        )
        val food = Committee(id = "food", name = "Food Committee", allocatedBudget = 10_000.0)
        val travel = Committee(id = "travel", name = "Travel Committee", allocatedBudget = 8_000.0)
        val medical = Committee(id = "med", name = "Medical Committee", allocatedBudget = 5_000.0)

        val incomes = listOf(
            IncomeRecord(
                id = "inc1",
                category = "Institutional Funding",
                promisedAmount = 30_000.0,
                receivedAmount = 30_000.0
            )
        )

        val expenses = listOf(
            ExpenseRecord(
                id = "e1",
                committeeId = "food",
                committeeName = "Food Committee",
                amount = 12_000.0,
                paidAmount = 7_000.0,
                paymentStatus = ExpensePaymentStatus.PARTIALLY_PAID.name,
                dateMillis = 1_700_000_000_000L
            ),
            ExpenseRecord(
                id = "e2",
                committeeId = "travel",
                committeeName = "Travel Committee",
                amount = 4_000.0,
                paidAmount = 0.0,
                paymentStatus = ExpensePaymentStatus.PENDING_APPROVAL.name,
                dateMillis = 1_700_086_400_000L
            ),
            ExpenseRecord(
                id = "e3",
                committeeId = "med",
                committeeName = "Medical Committee",
                amount = 2_000.0,
                paidAmount = 2_000.0,
                paymentStatus = ExpensePaymentStatus.PAID.name,
                dateMillis = 1_700_172_800_000L
            ),
            ExpenseRecord(
                id = "e4",
                committeeId = "travel",
                committeeName = "Travel Committee",
                amount = 999.0,
                paidAmount = 0.0,
                paymentStatus = ExpensePaymentStatus.REJECTED.name,
                dateMillis = 1_700_172_800_000L
            )
        )

        val summary = FinanceCalculator.computeDashboard(camp, listOf(food, travel, medical), incomes, expenses)

        assertEquals(14_000.0, summary.totalExpensesCommitted, 0.01) // 12k + 2k (excludes pending & rejected)
        assertEquals(9_000.0, summary.totalExpensesPaid, 0.01) // 7k + 2k
        assertEquals(21_000.0, summary.remainingCashBalance, 0.01) // 30k - 9k
        assertEquals(1, summary.pendingApprovalCount)
        assertEquals(4_000.0, summary.pendingApprovalAmount, 0.01)
        assertEquals(5_000.0, summary.outstandingPayments, 0.01) // 12k - 7k
        assertEquals(350.0, summary.costPerParticipant, 0.01) // 14,000 / 40
        assertEquals(1, summary.overspentCommittees.size)
        assertEquals("food", summary.overspentCommittees.first().committee.id)

        val costReport = FinanceCalculator.computeCostIntelligence(camp, summary, expenses)
        assertEquals(350.0, costReport.totalCostPerParticipant, 0.01)
        assertEquals(300.0, costReport.foodCostPerParticipant, 0.01) // 12,000 / 40
        assertEquals(2_000.0, costReport.medicalExpenditure, 0.01)
        assertEquals(1, costReport.overspendingAlertCount)
    }
}
