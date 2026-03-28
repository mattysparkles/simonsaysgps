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

internal data class StoredCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun toCoordinate() = Coordinate(latitude = latitude, longitude = longitude)

    companion object {
        fun fromCoordinate(coordinate: Coordinate) = StoredCoordinate(
            latitude = coordinate.latitude,
            longitude = coordinate.longitude
        )
    }
}

internal data class StoredRoute(
    val geometry: List<StoredCoordinate>,
    val maneuvers: List<StoredRouteManeuver>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val etaEpochSeconds: Long
) {
    fun toRoute() = Route(
        geometry = geometry.map(StoredCoordinate::toCoordinate),
        maneuvers = maneuvers.map(StoredRouteManeuver::toRouteManeuver),
        totalDistanceMeters = totalDistanceMeters,
        totalDurationSeconds = totalDurationSeconds,
        etaEpochSeconds = etaEpochSeconds
    )

    companion object {
        fun fromRoute(route: Route) = StoredRoute(
            geometry = route.geometry.map(StoredCoordinate::fromCoordinate),
            maneuvers = route.maneuvers.map(StoredRouteManeuver::fromRouteManeuver),
            totalDistanceMeters = route.totalDistanceMeters,
            totalDurationSeconds = route.totalDurationSeconds,
            etaEpochSeconds = route.etaEpochSeconds
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

internal data class StoredRouteManeuver(
    val id: String,
    val coordinate: StoredCoordinate,
    val instruction: String,
    val turnType: String,
    val roadName: String?,
    val distanceFromPreviousMeters: Double,
    val distanceToNextMeters: Double,
    val authorization: String,
    val headingBefore: Double?,
    val headingAfter: Double?
) {
    companion object {
        fun fromRouteManeuver(maneuver: RouteManeuver) = StoredRouteManeuver(
            id = maneuver.id,
            coordinate = StoredCoordinate.fromCoordinate(maneuver.coordinate),
            instruction = maneuver.instruction,
            turnType = maneuver.turnType.name,
            roadName = maneuver.roadName,
            distanceFromPreviousMeters = maneuver.distanceFromPreviousMeters,
            distanceToNextMeters = maneuver.distanceToNextMeters,
            authorization = maneuver.authorization.name,
            headingBefore = maneuver.headingBefore,
            headingAfter = maneuver.headingAfter
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
