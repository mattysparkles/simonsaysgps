package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.DistanceUnit
import com.simonsaysgps.domain.model.GameMode
import com.simonsaysgps.domain.model.PromptFrequency
import com.simonsaysgps.domain.model.PromptPersonality
import com.simonsaysgps.domain.model.RouteStyle
import com.simonsaysgps.domain.model.RoutingPreferences
import com.simonsaysgps.domain.model.RoutingProvider
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.TransportProfile
import com.simonsaysgps.domain.model.VehicleProfile
import com.simonsaysgps.domain.model.explore.AccessiblePlacesPreference
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.model.explore.ExploreSuggestionCount
import com.simonsaysgps.domain.model.explore.QuietPreferenceStrictness
import com.simonsaysgps.domain.model.voice.VoiceAssistantSettings
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
            prefs[ONBOARDING_SEEN] = updated.onboardingSeen
            prefs[TRANSPORT_PROFILE] = updated.routingPreferences.transportProfile.name
            prefs[PRIMARY_ROUTE_STYLE] = updated.routingPreferences.primaryRouteStyle.name
            prefs[AVOID_TOLLS] = updated.routingPreferences.avoidTolls
            prefs[PREFER_SCENIC] = updated.routingPreferences.preferScenic
            prefs[PREFER_FASTEST] = updated.routingPreferences.preferFastest
            prefs[PREFER_LOW_STRESS] = updated.routingPreferences.preferLowStress
            prefs[SIMON_CHALLENGE_MODE] = updated.routingPreferences.simonChallengeMode
            prefs[CHALLENGE_INTENSITY] = updated.routingPreferences.challengeIntensity
            writeNullableDouble(prefs, VEHICLE_HEIGHT_METERS, updated.routingPreferences.vehicleProfile.heightMeters)
            writeNullableDouble(prefs, VEHICLE_LENGTH_METERS, updated.routingPreferences.vehicleProfile.lengthMeters)
            writeNullableDouble(prefs, VEHICLE_WEIGHT_TONS, updated.routingPreferences.vehicleProfile.weightTons)
            prefs[EXPLORE_DEFAULT_RADIUS_MILES] = updated.exploreSettings.defaultRadiusMiles
            prefs[EXPLORE_REQUIRE_OPEN_NOW] = updated.exploreSettings.requireOpenNowByDefault
            prefs[EXPLORE_SUGGESTION_COUNT] = updated.exploreSettings.suggestionCount.name
            prefs[EXPLORE_ALLOW_ROUTE_DETOURS] = updated.exploreSettings.allowRouteDetoursWhileNavigating
            prefs[EXPLORE_MAX_DETOUR_DISTANCE_MILES] = updated.exploreSettings.maxDetourDistanceMiles.toString()
            prefs[EXPLORE_MAX_DETOUR_MINUTES] = updated.exploreSettings.maxDetourMinutes
            prefs[EXPLORE_USE_EVENT_DATA] = updated.exploreSettings.useEventDataWhenAvailable
            prefs[EXPLORE_USE_INTERNAL_REVIEWS_FIRST] = updated.exploreSettings.useInternalReviewsFirst
            prefs[EXPLORE_INCLUDE_THIRD_PARTY_REVIEWS] = updated.exploreSettings.includeThirdPartyReviewSummariesWhenAvailable
            prefs[EXPLORE_HOME_LABEL] = updated.exploreSettings.homeLabel
            updated.exploreSettings.homeCoordinate?.let {
                prefs[EXPLORE_HOME_LATITUDE] = it.latitude.toString()
                prefs[EXPLORE_HOME_LONGITUDE] = it.longitude.toString()
            } ?: run {
                prefs.remove(EXPLORE_HOME_LATITUDE)
                prefs.remove(EXPLORE_HOME_LONGITUDE)
            }
            prefs[EXPLORE_HOME_RADIUS_MILES] = updated.exploreSettings.closeToHomeRadiusMiles
            prefs[EXPLORE_VISIT_HISTORY_ENABLED] = updated.exploreSettings.visitHistoryEnabled
            prefs[EXPLORE_VISIT_HISTORY_RETENTION_DAYS] = updated.exploreSettings.visitHistoryRetentionDays
            prefs[EXPLORE_SURPRISE_WEIGHT] = updated.exploreSettings.surpriseMeWeight
            prefs[EXPLORE_KID_FRIENDLY_ONLY] = updated.exploreSettings.kidFriendlyOnly
            prefs[EXPLORE_QUIET_STRICTNESS] = updated.exploreSettings.quietPreferenceStrictness.name
            prefs[EXPLORE_ACCESSIBLE_PREFERENCE] = updated.exploreSettings.accessiblePlacesPreference.name
            prefs[EXPLORE_AVOID_ALCOHOL] = updated.exploreSettings.avoidAlcoholFocusedVenues
            prefs[EXPLORE_AVOID_ADULT] = updated.exploreSettings.avoidAdultOrientedVenues
            prefs[EXPLORE_WALKTHROUGH_SEEN] = updated.exploreSettings.walkthroughSeen
            prefs[VOICE_ASSISTANT_ENABLED] = updated.voiceAssistantSettings.enabled
            prefs[HANDS_FREE_REPORTING_ENABLED] = updated.voiceAssistantSettings.handsFreeReportingEnabled
            prefs[VOICE_CONFIRMATION_REQUIRED] = updated.voiceAssistantSettings.voiceConfirmationRequired
            prefs[VOICE_AI_CLEANUP_OPT_IN] = updated.voiceAssistantSettings.aiCleanupOptIn
            prefs[SOUNDTRACK_INTEGRATION_ENABLED] = updated.voiceAssistantSettings.soundtrackIntegrationEnabled
            prefs[SPOKEN_CONFIRMATIONS_ENABLED] = updated.voiceAssistantSettings.spokenConfirmationsEnabled
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
        demoMode = this[DEMO_MODE] ?: false,
        onboardingSeen = this[ONBOARDING_SEEN] ?: false,
        routingPreferences = RoutingPreferences(
            transportProfile = this[TRANSPORT_PROFILE]?.let(TransportProfile::valueOf) ?: TransportProfile.CAR,
            primaryRouteStyle = this[PRIMARY_ROUTE_STYLE]?.let(RouteStyle::valueOf) ?: RouteStyle.FASTEST,
            avoidTolls = this[AVOID_TOLLS] ?: false,
            preferScenic = this[PREFER_SCENIC] ?: false,
            preferFastest = this[PREFER_FASTEST] ?: true,
            preferLowStress = this[PREFER_LOW_STRESS] ?: false,
            simonChallengeMode = this[SIMON_CHALLENGE_MODE] ?: false,
            challengeIntensity = this[CHALLENGE_INTENSITY] ?: 2,
            vehicleProfile = VehicleProfile(
                heightMeters = this[VEHICLE_HEIGHT_METERS]?.toDoubleOrNull(),
                lengthMeters = this[VEHICLE_LENGTH_METERS]?.toDoubleOrNull(),
                weightTons = this[VEHICLE_WEIGHT_TONS]?.toDoubleOrNull()
            )
        ),
        exploreSettings = ExploreSettings(
            defaultRadiusMiles = this[EXPLORE_DEFAULT_RADIUS_MILES] ?: 10,
            requireOpenNowByDefault = this[EXPLORE_REQUIRE_OPEN_NOW] ?: true,
            suggestionCount = this[EXPLORE_SUGGESTION_COUNT]?.let(ExploreSuggestionCount::valueOf) ?: ExploreSuggestionCount.THREE_CHOICES,
            allowRouteDetoursWhileNavigating = this[EXPLORE_ALLOW_ROUTE_DETOURS] ?: true,
            maxDetourDistanceMiles = this[EXPLORE_MAX_DETOUR_DISTANCE_MILES]?.toDoubleOrNull() ?: 5.0,
            maxDetourMinutes = this[EXPLORE_MAX_DETOUR_MINUTES] ?: 12,
            useEventDataWhenAvailable = this[EXPLORE_USE_EVENT_DATA] ?: true,
            useInternalReviewsFirst = this[EXPLORE_USE_INTERNAL_REVIEWS_FIRST] ?: true,
            includeThirdPartyReviewSummariesWhenAvailable = this[EXPLORE_INCLUDE_THIRD_PARTY_REVIEWS] ?: true,
            homeLabel = this[EXPLORE_HOME_LABEL] ?: "",
            homeCoordinate = parseCoordinate(this[EXPLORE_HOME_LATITUDE], this[EXPLORE_HOME_LONGITUDE]),
            closeToHomeRadiusMiles = this[EXPLORE_HOME_RADIUS_MILES] ?: 8,
            visitHistoryEnabled = this[EXPLORE_VISIT_HISTORY_ENABLED] ?: true,
            visitHistoryRetentionDays = this[EXPLORE_VISIT_HISTORY_RETENTION_DAYS] ?: 180,
            surpriseMeWeight = this[EXPLORE_SURPRISE_WEIGHT] ?: 0.2f,
            kidFriendlyOnly = this[EXPLORE_KID_FRIENDLY_ONLY] ?: false,
            quietPreferenceStrictness = this[EXPLORE_QUIET_STRICTNESS]?.let(QuietPreferenceStrictness::valueOf) ?: QuietPreferenceStrictness.BALANCED,
            accessiblePlacesPreference = this[EXPLORE_ACCESSIBLE_PREFERENCE]?.let(AccessiblePlacesPreference::valueOf) ?: AccessiblePlacesPreference.PREFER_ACCESSIBLE,
            avoidAlcoholFocusedVenues = this[EXPLORE_AVOID_ALCOHOL] ?: true,
            avoidAdultOrientedVenues = this[EXPLORE_AVOID_ADULT] ?: true,
            walkthroughSeen = this[EXPLORE_WALKTHROUGH_SEEN] ?: false
        ),
        voiceAssistantSettings = VoiceAssistantSettings(
            enabled = this[VOICE_ASSISTANT_ENABLED] ?: true,
            handsFreeReportingEnabled = this[HANDS_FREE_REPORTING_ENABLED] ?: true,
            voiceConfirmationRequired = this[VOICE_CONFIRMATION_REQUIRED] ?: true,
            aiCleanupOptIn = this[VOICE_AI_CLEANUP_OPT_IN] ?: false,
            soundtrackIntegrationEnabled = this[SOUNDTRACK_INTEGRATION_ENABLED] ?: false,
            spokenConfirmationsEnabled = this[SPOKEN_CONFIRMATIONS_ENABLED] ?: true
        )
    )

    private fun parseCoordinate(latitude: String?, longitude: String?): Coordinate? {
        val lat = latitude?.toDoubleOrNull()
        val lon = longitude?.toDoubleOrNull()
        return if (lat != null && lon != null) Coordinate(lat, lon) else null
    }

    private fun writeNullableDouble(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: Double?
    ) {
        if (value != null && value > 0.0) prefs[key] = value.toString() else prefs.remove(key)
    }

    private companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val GAME_MODE = stringPreferencesKey("game_mode")
        val PROMPT_FREQUENCY = stringPreferencesKey("prompt_frequency")
        val PROMPT_PERSONALITY = stringPreferencesKey("prompt_personality")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ROUTING_PROVIDER = stringPreferencesKey("routing_provider")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val TRANSPORT_PROFILE = stringPreferencesKey("transport_profile")
        val PRIMARY_ROUTE_STYLE = stringPreferencesKey("primary_route_style")
        val AVOID_TOLLS = booleanPreferencesKey("avoid_tolls")
        val PREFER_SCENIC = booleanPreferencesKey("prefer_scenic")
        val PREFER_FASTEST = booleanPreferencesKey("prefer_fastest")
        val PREFER_LOW_STRESS = booleanPreferencesKey("prefer_low_stress")
        val SIMON_CHALLENGE_MODE = booleanPreferencesKey("simon_challenge_mode")
        val CHALLENGE_INTENSITY = intPreferencesKey("challenge_intensity")
        val VEHICLE_HEIGHT_METERS = stringPreferencesKey("vehicle_height_meters")
        val VEHICLE_LENGTH_METERS = stringPreferencesKey("vehicle_length_meters")
        val VEHICLE_WEIGHT_TONS = stringPreferencesKey("vehicle_weight_tons")
        val EXPLORE_DEFAULT_RADIUS_MILES = intPreferencesKey("explore_default_radius_miles")
        val EXPLORE_REQUIRE_OPEN_NOW = booleanPreferencesKey("explore_require_open_now")
        val EXPLORE_SUGGESTION_COUNT = stringPreferencesKey("explore_suggestion_count")
        val EXPLORE_ALLOW_ROUTE_DETOURS = booleanPreferencesKey("explore_allow_route_detours")
        val EXPLORE_MAX_DETOUR_DISTANCE_MILES = stringPreferencesKey("explore_max_detour_distance_miles")
        val EXPLORE_MAX_DETOUR_MINUTES = intPreferencesKey("explore_max_detour_minutes")
        val EXPLORE_USE_EVENT_DATA = booleanPreferencesKey("explore_use_event_data")
        val EXPLORE_USE_INTERNAL_REVIEWS_FIRST = booleanPreferencesKey("explore_use_internal_reviews_first")
        val EXPLORE_INCLUDE_THIRD_PARTY_REVIEWS = booleanPreferencesKey("explore_include_third_party_reviews")
        val EXPLORE_HOME_LABEL = stringPreferencesKey("explore_home_label")
        val EXPLORE_HOME_LATITUDE = stringPreferencesKey("explore_home_latitude")
        val EXPLORE_HOME_LONGITUDE = stringPreferencesKey("explore_home_longitude")
        val EXPLORE_HOME_RADIUS_MILES = intPreferencesKey("explore_home_radius_miles")
        val EXPLORE_VISIT_HISTORY_ENABLED = booleanPreferencesKey("explore_visit_history_enabled")
        val EXPLORE_VISIT_HISTORY_RETENTION_DAYS = intPreferencesKey("explore_visit_history_retention_days")
        val EXPLORE_SURPRISE_WEIGHT = floatPreferencesKey("explore_surprise_weight")
        val EXPLORE_KID_FRIENDLY_ONLY = booleanPreferencesKey("explore_kid_friendly_only")
        val EXPLORE_QUIET_STRICTNESS = stringPreferencesKey("explore_quiet_strictness")
        val EXPLORE_ACCESSIBLE_PREFERENCE = stringPreferencesKey("explore_accessible_preference")
        val EXPLORE_AVOID_ALCOHOL = booleanPreferencesKey("explore_avoid_alcohol")
        val EXPLORE_AVOID_ADULT = booleanPreferencesKey("explore_avoid_adult")
        val EXPLORE_WALKTHROUGH_SEEN = booleanPreferencesKey("explore_walkthrough_seen")
        val VOICE_ASSISTANT_ENABLED = booleanPreferencesKey("voice_assistant_enabled")
        val HANDS_FREE_REPORTING_ENABLED = booleanPreferencesKey("hands_free_reporting_enabled")
        val VOICE_CONFIRMATION_REQUIRED = booleanPreferencesKey("voice_confirmation_required")
        val VOICE_AI_CLEANUP_OPT_IN = booleanPreferencesKey("voice_ai_cleanup_opt_in")
        val SOUNDTRACK_INTEGRATION_ENABLED = booleanPreferencesKey("soundtrack_integration_enabled")
        val SPOKEN_CONFIRMATIONS_ENABLED = booleanPreferencesKey("spoken_confirmations_enabled")
    }
}
