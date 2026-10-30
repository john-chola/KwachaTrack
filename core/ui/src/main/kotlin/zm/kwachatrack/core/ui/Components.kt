package zm.kwachatrack.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.Direction
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.money.Kwacha
import java.time.Instant
import java.time.format.DateTimeFormatter

fun categoryIcon(iconKey: String): ImageVector =
    when (iconKey) {
        "food" -> Icons.Filled.Restaurant
        "transport" -> Icons.Filled.DirectionsBus
        "utilities" -> Icons.Filled.Bolt
        "airtime" -> Icons.Filled.PhoneAndroid
        "rent" -> Icons.Filled.Home
        "family" -> Icons.Filled.FamilyRestroom
        "business" -> Icons.Filled.Storefront
        "fees" -> Icons.AutoMirrored.Filled.ReceiptLong
        "income" -> Icons.Filled.Payments
        "bank" -> Icons.Filled.AccountBalance
        else -> Icons.Filled.MoreHoriz
    }

@Composable
fun CategoryBadge(
    category: Category?,
    modifier: Modifier = Modifier,
) {
    val colour = Color(category?.colour ?: 0xFFBDBDBD)
    Box(
        modifier = modifier.size(40.dp).background(colour.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: the category name is always shown next to the badge.
        Icon(categoryIcon(category?.iconKey ?: "other"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MoneyText(
    ngwee: Long,
    direction: Direction? = null,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val (prefix, colour) =
        when (direction) {
            Direction.IN -> "+" to MoneyColours.income
            Direction.OUT -> "−" to MoneyColours.spending
            null -> "" to MaterialTheme.colorScheme.onSurface
        }
    val text = prefix + Kwacha.format(ngwee)
    val spoken =
        (
            if (direction == Direction.IN) {
                "in "
            } else if (direction == Direction.OUT) {
                "out "
            } else {
                ""
            }
        ) + Kwacha.format(ngwee)
    Text(
        text,
        modifier = modifier.semantics { contentDescription = spoken },
        color = colour,
        style = style,
        fontWeight = FontWeight.SemiBold,
    )
}

private val TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.counterparty ?: transaction.type.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${category?.name ?: "Uncategorised"} · ${transaction.provider.displayName} · " +
                    TIME.format(Instant.ofEpochMilli(transaction.timestamp).atZone(LUSAKA)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(transaction.amountNgwee, transaction.type.direction)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
