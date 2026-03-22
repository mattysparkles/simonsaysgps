package com.simonsaysgps.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.RerouteReason
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SimonTurnResolution
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.NavigationSessionRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.navigationSessionDataStore by preferencesDataStore(name = "simonsays_navigation_session")

@Singleton
class DataStoreNavigationSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) : NavigationSessionRepository {
    private val adapter: JsonAdapter<StoredNavigationSessionState> = moshi.adapter(StoredNavigationSessionState::class.java)

    override val sessionState: Flow<NavigationSessionState?> = context.navigationSessionDataStore.data.map { prefs ->
        NavigationSessionStorage.decode(prefs[SESSION_STATE_KEY], adapter)
    }

    override suspend fun read(): NavigationSessionState? = sessionState.first()

    override suspend fun save(state: NavigationSessionState) {
        context.navigationSessionDataStore.edit { prefs ->
            prefs[SESSION_STATE_KEY] = NavigationSessionStorage.encode(state, adapter)
        }
    }

    override suspend fun clear() {
        context.navigationSessionDataStore.edit { prefs ->
            prefs.remove(SESSION_STATE_KEY)
        }
    }

    private companion object {
        val SESSION_STATE_KEY = stringPreferencesKey("navigation_session_state")
    }
}

internal object NavigationSessionStorage {
    fun encode(state: NavigationSessionState, adapter: JsonAdapter<StoredNavigationSessionState>): String {
        return adapter.toJson(StoredNavigationSessionState.fromNavigationSessionState(state))
    }

    fun decode(rawValue: String?, adapter: JsonAdapter<StoredNavigationSessionState>): NavigationSessionState? {
        if (rawValue.isNullOrBlank()) return null
        return runCatching {
            adapter.fromJson(rawValue)?.toNavigationSessionState()
        }.getOrNull()
    }
}

internal data class StoredNavigationSessionState(
    val route: StoredRoute?,
    val currentLocation: StoredLocationSample?,
    val snappedLocation: StoredCoordinate?,
    val activeManeuverIndex: Int,
    val distanceToNextManeuverMeters: Double?,
    val currentRoad: String?,
    val upcomingManeuver: StoredRouteManeuver?,
    val offRoute: Boolean,
    val lastRerouteReason: String,
    val headingDegrees: Double?,
    val navigationActive: Boolean
) {
    fun toNavigationSessionState(): NavigationSessionState = NavigationSessionState(
        route = route?.toRoute(),
        currentLocation = currentLocation?.toLocationSample(),
        snappedLocation = snappedLocation?.toCoordinate(),
        activeManeuverIndex = activeManeuverIndex,
        distanceToNextManeuverMeters = distanceToNextManeuverMeters,
        currentRoad = currentRoad,
        upcomingManeuver = upcomingManeuver?.toRouteManeuver(),
        spokenPrompt = null,
        latestResolution = SimonTurnResolution.None,
        offRoute = offRoute,
        lastRerouteReason = enumValueOrDefault(lastRerouteReason, RerouteReason.NONE),
        headingDegrees = headingDegrees,
        navigationActive = navigationActive
    )

    companion object {
        fun fromNavigationSessionState(state: NavigationSessionState) = StoredNavigationSessionState(
            route = state.route?.let(StoredRoute::fromRoute),
            currentLocation = state.currentLocation?.let(StoredLocationSample::fromLocationSample),
            snappedLocation = state.snappedLocation?.let(StoredCoordinate::fromCoordinate),
            activeManeuverIndex = state.activeManeuverIndex,
            distanceToNextManeuverMeters = state.distanceToNextManeuverMeters,
            currentRoad = state.currentRoad,
            upcomingManeuver = state.upcomingManeuver?.let(StoredRouteManeuver::fromRouteManeuver),
            offRoute = state.offRoute,
            lastRerouteReason = state.lastRerouteReason.name,
            headingDegrees = state.headingDegrees,
            navigationActive = state.navigationActive
        )
    }
}

internal data class StoredLocationSample(
    val coordinate: StoredCoordinate,
    val accuracyMeters: Float,
    val bearing: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long
) {
    fun toLocationSample() = LocationSample(
        coordinate = coordinate.toCoordinate(),
        accuracyMeters = accuracyMeters,
        bearing = bearing,
        speedMetersPerSecond = speedMetersPerSecond,
        timestampMillis = timestampMillis
    )

    companion object {
        fun fromLocationSample(sample: LocationSample) = StoredLocationSample(
            coordinate = StoredCoordinate.fromCoordinate(sample.coordinate),
            accuracyMeters = sample.accuracyMeters,
            bearing = sample.bearing,
            speedMetersPerSecond = sample.speedMetersPerSecond,
            timestampMillis = sample.timestampMillis
        )
    }
}

internal fun StoredRouteManeuver.toRouteManeuver() = RouteManeuver(
    id = id,
    coordinate = coordinate.toCoordinate(),
    instruction = instruction,
    turnType = enumValueOrDefault(turnType, TurnType.UNKNOWN),
    roadName = roadName,
    distanceFromPreviousMeters = distanceFromPreviousMeters,
    distanceToNextMeters = distanceToNextMeters,
    authorization = enumValueOrDefault(authorization, ManeuverAuthorization.NORMAL_INFO_ONLY),
    headingBefore = headingBefore,
    headingAfter = headingAfter
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T {
    return enumValues<T>().firstOrNull { it.name == name } ?: default
}
