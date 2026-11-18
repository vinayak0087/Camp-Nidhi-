package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CommitteeSummary
import com.example.domain.DailyExpensePoint
import com.example.domain.FinanceCalculator
import com.example.ui.theme.ForestGreenSecondary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusDangerCrimson
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.WarmGoldBright
import kotlin.math.max

@Composable
fun IncomeVsExpenseChart(
    title: String,
    fundsReceived: Double,
    promisedFunds: Double,
    expensesCommitted: Double,
    expensesPaid: Double
) {
    val maxVal = max(1.0, maxOf(fundsReceived, promisedFunds, expensesCommitted, expensesPaid))
    val receivedColor = ForestGreenSecondary
    val promisedColor = WarmGoldBright
    val committedColor = NavyPrimary
    val paidColor = Color(0xFF1D4ED8)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            val bars = listOf(
                Triple("Promised Funds", promisedFunds, promisedColor),
                Triple("Received Funds", fundsReceived, receivedColor),
                Triple("Approved Expenses", expensesCommitted, committedColor),
                Triple("Paid Out", expensesPaid, paidColor)
            )

            bars.forEach { (label, amount, barColor) ->
                val ratio = (amount / maxVal).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        Text(
                            FinanceCalculator.formatInr(amount),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily)
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        drawRoundRect(
                            color = trackColor,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                        )
                        if (ratio > 0f) {
                            drawRoundRect(
                                color = barColor,
                                size = Size(size.width * ratio, size.height),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitteeUtilizationChart(
    title: String,
    summaries: List<CommitteeSummary>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summaries.isEmpty()) {
                Text(
                    "No committees configured yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                summaries.take(8).forEach { cs ->
                    val pct = (cs.utilizationPct / 100.0).toFloat().coerceIn(0f, 1f)
                    val barColor = when {
                        cs.amountSpent > cs.allocatedBudget -> StatusDangerCrimson
                        cs.utilizationPct >= 85.0 -> StatusPendingAmber
                        else -> parseHexColorOrDefault(cs.committee.colorHex, ForestGreenSecondary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(barColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cs.committee.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${FinanceCalculator.formatCompactInr(cs.amountSpent)} / ${FinanceCalculator.formatCompactInr(cs.allocatedBudget)} (${cs.utilizationPct.toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cs.amountSpent > cs.allocatedBudget) StatusDangerCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { pct },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyExpenseTrendChart(
    title: String,
    points: List<DailyExpensePoint>
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) {
                Text(
                    "Record camp expenses to view daily spend velocity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxAmt = max(1.0, points.maxOf { it.amount })
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(top = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    // Horizontal guide lines
                    for (i in 0..2) {
                        val y = h * (i / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val stepX = if (points.size > 1) w / (points.size - 1) else w
                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size > 1) idx * stepX else w / 2f
                        val y = h - ((pt.amount / maxAmt).toFloat() * (h - 14.dp.toPx())) - 7.dp.toPx()
                        Offset(x, y)
                    }

                    if (coords.size >= 2) {
                        val fillPath = Path().apply {
                            moveTo(coords.first().x, h)
                            coords.forEach { lineTo(it.x, it.y) }
                            lineTo(coords.last().x, h)
                            close()
                        }
                        drawPath(fillPath, color = fillColor)

                        val strokePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            coords.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    coords.forEach { pt ->
                        drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = pt)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { pt ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(pt.dayLabel, style = MaterialTheme.typography.labelSmall)
                            Text(
                                FinanceCalculator.formatCompactInr(pt.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
