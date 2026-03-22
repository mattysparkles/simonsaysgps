package com.simonsaysgps.domain.model

import com.simonsaysgps.domain.model.explore.ExploreSettings

data class SettingsModel(
    val voiceEnabled: Boolean = true,
    val gameMode: GameMode = GameMode.BASIC,
    val promptFrequency: PromptFrequency = PromptFrequency.NORMAL,
    val promptPersonality: PromptPersonality = PromptPersonality.CLASSIC_SIMON,
    val distanceUnit: DistanceUnit = DistanceUnit.IMPERIAL,
    val routingProvider: RoutingProvider = RoutingProvider.OSRM,
    val debugMode: Boolean = false,
    val demoMode: Boolean = true,
    val exploreSettings: ExploreSettings = ExploreSettings()
)
