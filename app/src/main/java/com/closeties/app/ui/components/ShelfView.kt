package com.closeties.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closeties.app.domain.model.TrackedContact
import com.closeties.app.domain.usecase.CalculateCooldownUseCase

@Composable
fun ShelfView(
    shelfLevel: Int,
    contacts: List<TrackedContact>,
    totalPoolStakes: Int,
    onMoveShelfClick: (TrackedContact) -> Unit,
    onDeleteClick: (TrackedContact) -> Unit,
    onClearCooldownClick: ((TrackedContact) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val calculator = remember { CalculateCooldownUseCase() }
    val shelfTitle = "Shelf $shelfLevel"

    val stakeCount = shelfLevel
    val estFrequency = if (totalPoolStakes > 0) {
        calculator.calculateExpectedFrequencyDays(totalPoolStakes, stakeCount)
    } else {
        0
    }

    val subtitle = if (estFrequency > 0) {
        "$stakeCount ${if (stakeCount == 1) "Stake" else "Stakes"} • Est. every $estFrequency ${if (estFrequency == 1) "day" else "days"}"
    } else {
        "$stakeCount ${if (stakeCount == 1) "Stake" else "Stakes"}"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = (6 - shelfLevel).dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Shelf Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$shelfLevel",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = shelfTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Contact Count Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${contacts.size}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contact Cards or Empty Placeholder
            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No contacts on this shelf",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    contacts.forEach { contact ->
                        ContactCard(
                            contact = contact,
                            totalPoolStakes = totalPoolStakes,
                            onMoveShelfClick = onMoveShelfClick,
                            onDeleteClick = onDeleteClick,
                            onClearCooldownClick = onClearCooldownClick
                        )
                    }
                }
            }
        }
    }
}
