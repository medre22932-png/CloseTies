package com.closeties.app.ui.ladder

import com.closeties.app.domain.model.TrackedContact

data class LadderUiState(
    val contacts: List<TrackedContact> = emptyList(),
    val totalStakes: Int = 0,
    val eligibleCount: Int = 0,
    val cooldownCount: Int = 0,
    val selectedContactForMove: TrackedContact? = null,
    val testDrawWinner: TrackedContact? = null,
    val isPerformingDraw: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val notificationHour: Int = 10,
    val notificationMinute: Int = 0,
    val statusMessage: String? = null
)
