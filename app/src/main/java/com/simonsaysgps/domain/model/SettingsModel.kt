package com.simonsaysgps.domain.model

data class SettingsModel(
    val voiceEnabled: Boolean = true,
    val gameMode: GameMode = GameMode.BASIC,
    val promptFrequency: PromptFrequency = PromptFrequency.NORMAL,
    val distanceUnit: DistanceUnit = DistanceUnit.IMPERIAL,
    val debugMode: Boolean = false,
    val demoMode: Boolean = true
)
