package com.example.domain

import com.example.data.model.Camp
import com.example.data.model.Committee
import com.example.data.model.ExpensePaymentStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.IncomeRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class CommitteeSummary(
    val committee: Committee,
    val allocatedBudget: Double,
    val amountSpent: Double,
    val paidAmount: Double,
    val pendingExpenses: Double,
    val remainingBudget: Double,
    val utilizationPct: Double,
    val isOverspent: Boolean,
    val expenseCount: Int
)

data class DailyExpensePoint(
    val dayLabel: String,
    val dateMillis: Long,
    val amount: Double
)

data class CategoryBreakdown(
    val category: String,
    val amount: Double
)

data class DashboardSummary(
    val totalBudget: Double,
    val totalAllocatedToCommittees: Double,
    val totalFundsReceived: Double,
    val totalPromisedFunds: Double,
    val outstandingContributions: Double,
    val totalExpensesCommitted: Double,
    val totalExpensesPaid: Double,
    val remainingCashBalance: Double,
    val remainingBudget: Double,
    val budgetRemainingPct: Double,
    val pendingApprovalAmount: Double,
    val pendingApprovalCount: Int,
    val outstandingPayments: Double,
    val totalParticipantContributions: Double,
    val costPerParticipant: Double,
    val overspentCommittees: List<CommitteeSummary>,
    val committeeSummaries: List<CommitteeSummary>,
    val dailyExpenseTrend: List<DailyExpensePoint>,
    val incomeByCategory: List<CategoryBreakdown>,
    val expenseByCategory: List<CategoryBreakdown>,
    val recentExpenses: List<ExpenseRecord>,
    val upcomingPayments: List<ExpenseRecord>,
    val verifiedExpensesCount: Int,
    val pendingVerificationCount: Int
)

data class CostIntelligenceReport(
    val totalCostPerParticipant: Double,
    val foodCostPerParticipant: Double,
    val travelCostPerParticipant: Double,
    val medicalExpenditure: Double,
    val dailyAverageExpenditure: Double,
    val projectedFinalExpenditure: Double,
    val budgetUtilizedPct: Double,
    val campTotalDays: Int,
    val campElapsedDays: Int,
    val overspendingAlertCount: Int,
    val committeeSummaries: List<CommitteeSummary>
)

object FinanceCalculator {

    private val committedStatuses = setOf(
        ExpensePaymentStatus.APPROVED,
        ExpensePaymentStatus.PARTIALLY_PAID,
        ExpensePaymentStatus.PAID
    )

    private val pendingStatuses = setOf(
        ExpensePaymentStatus.SUBMITTED,
        ExpensePaymentStatus.PENDING_APPROVAL
    )

    fun computeDashboard(
        camp: Camp?,
        committees: List<Committee>,
        incomes: List<IncomeRecord>,
        expenses: List<ExpenseRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): DashboardSummary {
        val totalBudget = camp?.approvedBudget ?: 0.0
        val participantCount = (camp?.participantCount ?: 0).coerceAtLeast(1)

        // Never count unpaid promised contributions as received funds
        val totalFundsReceived = incomes.sumOf { it.receivedAmount.coerceAtLeast(0.0) }
        val totalPromisedFunds = incomes.sumOf { it.promisedAmount.coerceAtLeast(0.0) }
        val outstandingContributions = incomes.sumOf { it.outstandingAmount }

        val totalParticipantContributions = incomes
            .filter {
                val cat = it.category.lowercase(Locale.ROOT)
                cat.contains("student") || cat.contains("staff") || cat.contains("participant")
            }
            .sumOf { it.receivedAmount.coerceAtLeast(0.0) }

        val committedExpensesList = expenses.filter { it.status in committedStatuses }
        val pendingExpensesList = expenses.filter { it.status in pendingStatuses }

        val totalExpensesCommitted = committedExpensesList.sumOf { it.amount.coerceAtLeast(0.0) }
        val totalExpensesPaid = committedExpensesList.sumOf {
            when (it.status) {
                ExpensePaymentStatus.PAID -> if (it.paidAmount > 0.0) it.paidAmount else it.amount
                ExpensePaymentStatus.PARTIALLY_PAID -> it.paidAmount.coerceIn(0.0, it.amount)
                else -> it.paidAmount.coerceIn(0.0, it.amount)
            }
        }

        val pendingApprovalAmount = pendingExpensesList.sumOf { it.amount.coerceAtLeast(0.0) }
        val pendingApprovalCount = pendingExpensesList.size

        val outstandingPayments = committedExpensesList.sumOf { exp ->
            val paid = when (exp.status) {
                ExpensePaymentStatus.PAID -> if (exp.paidAmount > 0.0) exp.paidAmount else exp.amount
                else -> exp.paidAmount.coerceIn(0.0, exp.amount)
            }
            (exp.amount - paid).coerceAtLeast(0.0)
        }

        val remainingCashBalance = totalFundsReceived - totalExpensesPaid
        val remainingBudget = totalBudget - totalExpensesCommitted
        val budgetRemainingPct = if (totalBudget > 0.0) {
            ((totalBudget - totalExpensesCommitted) / totalBudget * 100.0).coerceIn(-999.0, 100.0)
        } else 0.0

        val costPerParticipant = if ((camp?.participantCount ?: 0) > 0) {
            totalExpensesCommitted / participantCount
        } else 0.0

        val committeeSummaries = committees.map { committee ->
            val commExpenses = expenses.filter { it.committeeId == committee.id }
            val spent = commExpenses.filter { it.status in committedStatuses }.sumOf { it.amount }
            val paid = commExpenses.filter { it.status in committedStatuses }.sumOf {
                if (it.status == ExpensePaymentStatus.PAID && it.paidAmount == 0.0) it.amount else it.paidAmount
            }
            val pending = commExpenses.filter { it.status in pendingStatuses }.sumOf { it.amount }
            val remaining = committee.allocatedBudget - spent
            val utilPct = if (committee.allocatedBudget > 0.0) {
                (spent / committee.allocatedBudget) * 100.0
            } else if (spent > 0.0) 100.0 else 0.0
            CommitteeSummary(
                committee = committee,
                allocatedBudget = committee.allocatedBudget,
                amountSpent = spent,
                paidAmount = paid,
                pendingExpenses = pending,
                remainingBudget = remaining,
                utilizationPct = utilPct,
                isOverspent = spent > committee.allocatedBudget || (spent + pending) > committee.allocatedBudget,
                expenseCount = commExpenses.size
            )
        }

        val overspentCommittees = committeeSummaries.filter { it.amountSpent > it.allocatedBudget }
        val totalAllocated = committees.sumOf { it.allocatedBudget }

        val dayFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyTrend = expenses
            .filter { it.status != ExpensePaymentStatus.REJECTED && it.status != ExpensePaymentStatus.DRAFT }
            .groupBy { dayStartMillis(it.dateMillis) }
            .toSortedMap()
            .entries
            .toList()
            .takeLast(10)
            .map { (dayMillis, list) ->
                DailyExpensePoint(
                    dayLabel = dayFmt.format(Date(dayMillis)),
                    dateMillis = dayMillis,
                    amount = list.sumOf { it.amount }
                )
            }

        val incomeByCat = incomes
            .groupBy { it.category.ifBlank { "Other" } }
            .map { (cat, list) -> CategoryBreakdown(cat, list.sumOf { it.receivedAmount }) }
            .sortedByDescending { it.amount }

        val expenseByCat = committedExpensesList
            .groupBy { it.category.ifBlank { "Other" } }
            .map { (cat, list) -> CategoryBreakdown(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        val upcomingPayments = expenses
            .filter {
                it.status == ExpensePaymentStatus.APPROVED ||
                    it.status == ExpensePaymentStatus.PARTIALLY_PAID ||
                    it.status in pendingStatuses
            }
            .sortedBy { it.dateMillis }
            .take(6)

        val verifiedCount = committedExpensesList.count { it.receiptVerified }
        val pendingVerifyCount = committedExpensesList.count { !it.receiptVerified }

        return DashboardSummary(
            totalBudget = totalBudget,
            totalAllocatedToCommittees = totalAllocated,
            totalFundsReceived = totalFundsReceived,
            totalPromisedFunds = totalPromisedFunds,
            outstandingContributions = outstandingContributions,
            totalExpensesCommitted = totalExpensesCommitted,
            totalExpensesPaid = totalExpensesPaid,
            remainingCashBalance = remainingCashBalance,
            remainingBudget = remainingBudget,
            budgetRemainingPct = budgetRemainingPct,
            pendingApprovalAmount = pendingApprovalAmount,
            pendingApprovalCount = pendingApprovalCount,
            outstandingPayments = outstandingPayments,
            totalParticipantContributions = totalParticipantContributions,
            costPerParticipant = costPerParticipant,
            overspentCommittees = overspentCommittees,
            committeeSummaries = committeeSummaries,
            dailyExpenseTrend = dailyTrend,
            incomeByCategory = incomeByCat,
            expenseByCategory = expenseByCat,
            recentExpenses = expenses.sortedByDescending { it.dateMillis }.take(8),
            upcomingPayments = upcomingPayments,
            verifiedExpensesCount = verifiedCount,
            pendingVerificationCount = pendingVerifyCount
        )
    }

    fun computeCostIntelligence(
        camp: Camp?,
        summary: DashboardSummary,
        expenses: List<ExpenseRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): CostIntelligenceReport {
        val participants = (camp?.participantCount ?: 0).coerceAtLeast(1)
        val committed = expenses.filter { it.status in committedStatuses }

        val foodSpent = committed.filter {
            it.committeeName.contains("food", ignoreCase = true) ||
                it.category.contains("grocer", ignoreCase = true) ||
                it.category.contains("catering", ignoreCase = true)
        }.sumOf { it.amount }

        val travelSpent = committed.filter {
            it.committeeName.contains("travel", ignoreCase = true) ||
                it.category.contains("transport", ignoreCase = true) ||
                it.category.contains("bus", ignoreCase = true)
        }.sumOf { it.amount }

        val medicalSpent = committed.filter {
            it.committeeName.contains("medical", ignoreCase = true) ||
                it.category.contains("medic", ignoreCase = true) ||
                it.category.contains("first aid", ignoreCase = true)
        }.sumOf { it.amount }

        val dayMs = 86_400_000L
        val startMs = camp?.startDateMillis?.takeIf { it > 0L } ?: nowMillis
        val endMs = camp?.endDateMillis?.takeIf { it >= startMs } ?: (startMs + 6 * dayMs)
        val totalDays = max(1, (((endMs - startMs) / dayMs).toInt() + 1))

        val distinctExpenseDays = committed.map { dayStartMillis(it.dateMillis) }.distinct().size
        val elapsedByClock = if (nowMillis >= startMs) {
            (((nowMillis - startMs) / dayMs).toInt() + 1).coerceIn(1, totalDays)
        } else {
            max(1, distinctExpenseDays)
        }
        val elapsedDays = max(elapsedByClock, max(1, distinctExpenseDays)).coerceAtMost(totalDays)

        val dailyAvg = if (elapsedDays > 0) summary.totalExpensesCommitted / elapsedDays else 0.0
        val projected = if (summary.totalExpensesCommitted > 0.0) {
            dailyAvg * totalDays
        } else 0.0

        val utilizedPct = if (summary.totalBudget > 0.0) {
            (summary.totalExpensesCommitted / summary.totalBudget) * 100.0
        } else 0.0

        return CostIntelligenceReport(
            totalCostPerParticipant = if ((camp?.participantCount ?: 0) > 0) summary.totalExpensesCommitted / participants else 0.0,
            foodCostPerParticipant = if ((camp?.participantCount ?: 0) > 0) foodSpent / participants else 0.0,
            travelCostPerParticipant = if ((camp?.participantCount ?: 0) > 0) travelSpent / participants else 0.0,
            medicalExpenditure = medicalSpent,
            dailyAverageExpenditure = dailyAvg,
            projectedFinalExpenditure = projected,
            budgetUtilizedPct = utilizedPct,
            campTotalDays = totalDays,
            campElapsedDays = elapsedDays,
            overspendingAlertCount = summary.overspentCommittees.size,
            committeeSummaries = summary.committeeSummaries
        )
    }

    private fun dayStartMillis(millis: Long): Long = (millis / 86_400_000L) * 86_400_000L

    fun formatInr(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        return format.format(amount)
    }

    fun formatCompactInr(amount: Double): String {
        return when {
            kotlin.math.abs(amount) >= 10000000.0 -> String.format(Locale("en", "IN"), "₹%.2fCr", amount / 10000000.0)
            kotlin.math.abs(amount) >= 100000.0 -> String.format(Locale("en", "IN"), "₹%.2fL", amount / 100000.0)
            kotlin.math.abs(amount) >= 1000.0 -> String.format(Locale("en", "IN"), "₹%.1fK", amount / 1000.0)
            else -> formatInr(amount)
        }
    }
}
