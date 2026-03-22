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
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "simonsays_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    override val settings: Flow<SettingsModel> = context.settingsDataStore.data.map { prefs ->
        prefs.toSettingsModel()
    }

    override suspend fun update(transform: (SettingsModel) -> SettingsModel) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.toSettingsModel())
            prefs[VOICE_ENABLED] = updated.voiceEnabled
            prefs[GAME_MODE] = updated.gameMode.name
            prefs[PROMPT_FREQUENCY] = updated.promptFrequency.name
            prefs[PROMPT_PERSONALITY] = updated.promptPersonality.name
            prefs[DISTANCE_UNIT] = updated.distanceUnit.name
            prefs[ROUTING_PROVIDER] = updated.routingProvider.name
            prefs[DEBUG_MODE] = updated.debugMode
            prefs[DEMO_MODE] = updated.demoMode
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toSettingsModel() = SettingsModel(
        voiceEnabled = this[VOICE_ENABLED] ?: true,
        gameMode = this[GAME_MODE]?.let(GameMode::valueOf) ?: GameMode.BASIC,
        promptFrequency = this[PROMPT_FREQUENCY]?.let(PromptFrequency::valueOf) ?: PromptFrequency.NORMAL,
        promptPersonality = this[PROMPT_PERSONALITY]?.let(PromptPersonality::valueOf) ?: PromptPersonality.CLASSIC_SIMON,
        distanceUnit = this[DISTANCE_UNIT]?.let(DistanceUnit::valueOf) ?: DistanceUnit.IMPERIAL,
        routingProvider = RoutingProvider.fromNameOrDefault(this[ROUTING_PROVIDER]),
        debugMode = this[DEBUG_MODE] ?: false,
        demoMode = this[DEMO_MODE] ?: true
    )

    private companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val GAME_MODE = stringPreferencesKey("game_mode")
        val PROMPT_FREQUENCY = stringPreferencesKey("prompt_frequency")
        val PROMPT_PERSONALITY = stringPreferencesKey("prompt_personality")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ROUTING_PROVIDER = stringPreferencesKey("routing_provider")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
    }
}
