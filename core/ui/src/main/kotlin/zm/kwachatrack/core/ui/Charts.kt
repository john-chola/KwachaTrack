package zm.kwachatrack.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zm.kwachatrack.core.domain.money.Kwacha

/*
 * Small Canvas charts. The design doc suggested Vico; these avoid a dependency whose API changes
 * between major versions, and each chart carries a spoken summary for TalkBack.
 */

data class Slice(
    val label: String,
    val valueNgwee: Long,
    val colour: Color,
)

@Composable
fun DonutChart(
    slices: List<Slice>,
    centreLabel: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.valueNgwee }.coerceAtLeast(1)
    val trackColour = MaterialTheme.colorScheme.surfaceVariant
    val summary = slices.joinToString { "${it.label} ${Kwacha.format(it.valueNgwee)}" }
    Row(modifier.semantics { contentDescription = "Spending by category: $summary" }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(trackColour, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                slices.forEach { s ->
                    val sweep = 360f * s.valueNgwee / total
                    drawArc(s.colour, start, (sweep - 1f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
            Text(centreLabel, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(6).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))) { drawRect(s.colour) }
                    Spacer(Modifier.width(6.dp))
                    Text("${s.label} · ${Kwacha.formatCompact(s.valueNgwee)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

data class BarGroup(
    val label: String,
    val incomeNgwee: Long,
    val spendingNgwee: Long,
)

@Composable
fun IncomeSpendingBars(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
) {
    val income = MoneyColours.income
    val spending = MoneyColours.spending
    val max = groups.maxOfOrNull { maxOf(it.incomeNgwee, it.spendingNgwee) }?.coerceAtLeast(1) ?: 1
    val summary = groups.joinToString { "${it.label}: in ${Kwacha.format(it.incomeNgwee)}, out ${Kwacha.format(it.spendingNgwee)}" }
    Column(modifier.semantics { contentDescription = "Monthly income and spending. $summary" }) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / groups.size.coerceAtLeast(1)
            val bar = slot * 0.3f
            val radius = CornerRadius(4.dp.toPx())
            groups.forEachIndexed { i, g ->
                val x = i * slot + slot * 0.18f
                val hIn = size.height * g.incomeNgwee / max
                val hOut = size.height * g.spendingNgwee / max
                drawRoundRect(income, Offset(x, size.height - hIn), Size(bar, hIn), radius)
                drawRoundRect(spending, Offset(x + bar + 2.dp.toPx(), size.height - hOut), Size(bar, hOut), radius)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach { g ->
                Text(
                    g.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(income, "Money in")
            Legend(spending, "Money out")
        }
    }
}

@Composable
private fun Legend(
    colour: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRect(colour) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BalanceLine(
    points: List<Long>,
    modifier: Modifier = Modifier,
) {
    val colour = MaterialTheme.colorScheme.primary
    val summary =
        if (points.isEmpty()) "No balance data" else "Balance went from ${Kwacha.format(points.first())} to ${Kwacha.format(points.last())}"
    Canvas(modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = summary }) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val range = (points.max() - min).coerceAtLeast(1)
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - size.height * (v - min) / range
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colour, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
