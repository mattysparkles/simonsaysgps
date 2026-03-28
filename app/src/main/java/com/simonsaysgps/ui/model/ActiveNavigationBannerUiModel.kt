package com.simonsaysgps.ui.model

import androidx.compose.ui.graphics.Color
import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.LaneDirection
import com.simonsaysgps.domain.model.LaneGuidance
import com.simonsaysgps.domain.model.LaneIndicator
import com.simonsaysgps.domain.model.LanePreference
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.util.DistanceFormatter

sealed interface ActiveNavigationBannerUiModel {
    data class EnRoute(
        val title: String,
        val primaryInstruction: String,
        val distanceLabel: String,
        val stepLabel: String?,
        val turnTypeLabel: String?,
        val roadLabel: String,
        val secondaryInstruction: String?,
        val authorization: AuthorizationUiModel,
        val laneGuidance: LaneGuidanceUiModel
    ) : ActiveNavigationBannerUiModel

    data class Arrived(
        val title: String,
        val message: String,
        val detail: String
    ) : ActiveNavigationBannerUiModel
}

data class AuthorizationUiModel(
    val label: String,
    val message: String,
    val badgeColor: Color
)

sealed interface LaneGuidanceUiModel {
    data class Ready(val lanes: List<LaneUiModel>, val hint: String) : LaneGuidanceUiModel
    data class Placeholder(val title: String, val message: String) : LaneGuidanceUiModel
    data object Hidden : LaneGuidanceUiModel
}

data class LaneUiModel(
    val label: String,
    val emphasized: Boolean,
    val subdued: Boolean
)

object ActiveNavigationBannerUiMapper {
    fun map(
        navigation: NavigationSessionState,
        settings: SettingsModel
    ): ActiveNavigationBannerUiModel {
        if (navigation.arrivalStatus == ArrivalStatus.ARRIVED || (!navigation.navigationActive && navigation.upcomingManeuver == null)) {
            return ActiveNavigationBannerUiModel.Arrived(
                title = "You've arrived",
                message = navigation.spokenPrompt ?: "Destination reached.",
                detail = "Navigation is complete. End the trip whenever you're ready."
            )
        }

        val maneuver = navigation.upcomingManeuver
        val totalSteps = navigation.route?.maneuvers?.size.orZero()
        val activeStepNumber = if (totalSteps == 0) null else (navigation.activeManeuverIndex + 1).coerceAtMost(totalSteps)
        val nextInstruction = navigation.route?.maneuvers?.getOrNull(navigation.activeManeuverIndex + 1)?.instruction
        val title = when (navigation.arrivalStatus) {
            ArrivalStatus.APPROACHING_DESTINATION -> "Arrival ahead"
            else -> "Next instruction"
        }
        return ActiveNavigationBannerUiModel.EnRoute(
            title = title,
            primaryInstruction = maneuver?.instruction ?: "Continue following the route",
            distanceLabel = navigation.distanceToNextManeuverMeters?.let {
                DistanceFormatter.format(it, settings.distanceUnit)
            } ?: "Done",
            stepLabel = activeStepNumber?.let { "Step $it of $totalSteps" },
            turnTypeLabel = maneuver?.turnType?.toDisplayLabel(),
            roadLabel = maneuver?.roadName ?: navigation.currentRoad ?: "Following route",
            secondaryInstruction = when {
                navigation.arrivalStatus == ArrivalStatus.APPROACHING_DESTINATION -> "Destination is just ahead. Stay alert for the finish."
                nextInstruction != null -> "Then $nextInstruction"
                else -> navigation.spokenPrompt
            },
            authorization = maneuver?.authorization.toAuthorizationUiModel(),
            laneGuidance = maneuver?.laneGuidance.toLaneGuidanceUiModel(maneuver?.turnType)
        )
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun ManeuverAuthorization?.toAuthorizationUiModel(): AuthorizationUiModel {
    return when (this) {
        ManeuverAuthorization.REQUIRED_SIMON_SAYS -> AuthorizationUiModel(
            label = "SIMON SAYS",
            message = "Authorized move — this maneuver counts only when Simon says it.",
            badgeColor = Color(0xFFB7F5C5)
        )
        else -> AuthorizationUiModel(
            label = "INFO ONLY",
            message = "Preview only — wait for the next Simon Says-approved move before turning.",
            badgeColor = Color(0xFFFFE08A)
        )
    }
}

private fun LaneGuidance?.toLaneGuidanceUiModel(turnType: TurnType?): LaneGuidanceUiModel {
    if (turnType == TurnType.ARRIVE) return LaneGuidanceUiModel.Hidden
    if (this == null || lanes.isEmpty()) {
        return LaneGuidanceUiModel.Placeholder(
            title = "Lane guidance ready",
            message = "This UI is ready to show lane-level guidance when the active routing provider supplies it."
        )
    }
    return LaneGuidanceUiModel.Ready(
        lanes = lanes.map(LaneIndicator::toUiModel),
        hint = if (lanes.any { it.preference == LanePreference.RECOMMENDED }) {
            "Use the highlighted lane."
        } else {
            "Lane guidance available."
        }
    )
}

private fun LaneIndicator.toUiModel(): LaneUiModel {
    return LaneUiModel(
        label = directions.sortedBy { it.ordinal }.joinToString(" / ") { it.toArrowLabel() },
        emphasized = preference == LanePreference.RECOMMENDED,
        subdued = preference == LanePreference.NOT_RECOMMENDED
    )
}

private fun LaneDirection.toArrowLabel(): String {
    return when (this) {
        LaneDirection.SHARP_LEFT -> "⇇"
        LaneDirection.LEFT -> "←"
        LaneDirection.SLIGHT_LEFT -> "↖"
        LaneDirection.STRAIGHT -> "↑"
        LaneDirection.SLIGHT_RIGHT -> "↗"
        LaneDirection.RIGHT -> "→"
        LaneDirection.SHARP_RIGHT -> "⇉"
        LaneDirection.UTURN -> "⤺"
        LaneDirection.UNKNOWN -> "•"
    }
}

private fun TurnType.toDisplayLabel(): String {
    return when (this) {
        TurnType.STRAIGHT -> "Continue"
        TurnType.SLIGHT_LEFT -> "Slight left"
        TurnType.LEFT -> "Left turn"
        TurnType.SHARP_LEFT -> "Sharp left"
        TurnType.SLIGHT_RIGHT -> "Slight right"
        TurnType.RIGHT -> "Right turn"
        TurnType.SHARP_RIGHT -> "Sharp right"
        TurnType.UTURN -> "U-turn"
        TurnType.ARRIVE -> "Arrive"
        TurnType.DEPART -> "Depart"
        TurnType.ROUNDABOUT -> "Roundabout"
        TurnType.UNKNOWN -> "Upcoming maneuver"
    }
}
