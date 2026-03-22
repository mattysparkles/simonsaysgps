package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.PromptFrequency
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "simonsays_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    override val settings: Flow<SettingsModel> = context.settingsDataStore.data.map { prefs ->
        SettingsModel(
            voiceEnabled = prefs[VOICE_ENABLED] ?: true,
            gameMode = prefs[GAME_MODE]?.let(GameMode::valueOf) ?: GameMode.BASIC,
            promptFrequency = prefs[PROMPT_FREQUENCY]?.let(PromptFrequency::valueOf) ?: PromptFrequency.NORMAL,
            promptPersonality = prefs[PROMPT_PERSONALITY]?.let(PromptPersonality::valueOf) ?: PromptPersonality.CLASSIC_SIMON,
            distanceUnit = prefs[DISTANCE_UNIT]?.let(DistanceUnit::valueOf) ?: DistanceUnit.IMPERIAL,
            debugMode = prefs[DEBUG_MODE] ?: false,
            demoMode = prefs[DEMO_MODE] ?: true
        )
    }

    override suspend fun update(transform: (SettingsModel) -> SettingsModel) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(
                SettingsModel(
                    voiceEnabled = prefs[VOICE_ENABLED] ?: true,
                    gameMode = prefs[GAME_MODE]?.let(GameMode::valueOf) ?: GameMode.BASIC,
                    promptFrequency = prefs[PROMPT_FREQUENCY]?.let(PromptFrequency::valueOf) ?: PromptFrequency.NORMAL,
                    promptPersonality = prefs[PROMPT_PERSONALITY]?.let(PromptPersonality::valueOf) ?: PromptPersonality.CLASSIC_SIMON,
                    distanceUnit = prefs[DISTANCE_UNIT]?.let(DistanceUnit::valueOf) ?: DistanceUnit.IMPERIAL,
                    debugMode = prefs[DEBUG_MODE] ?: false,
                    demoMode = prefs[DEMO_MODE] ?: true
                )
            )
            prefs[VOICE_ENABLED] = updated.voiceEnabled
            prefs[GAME_MODE] = updated.gameMode.name
            prefs[PROMPT_FREQUENCY] = updated.promptFrequency.name
            prefs[PROMPT_PERSONALITY] = updated.promptPersonality.name
            prefs[DISTANCE_UNIT] = updated.distanceUnit.name
            prefs[DEBUG_MODE] = updated.debugMode
            prefs[DEMO_MODE] = updated.demoMode
        }
    }

    private companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val GAME_MODE = stringPreferencesKey("game_mode")
        val PROMPT_FREQUENCY = stringPreferencesKey("prompt_frequency")
        val PROMPT_PERSONALITY = stringPreferencesKey("prompt_personality")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
    }
}
