package com.simonsaysgps.data.repository

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.roundToInt
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.NetworkFailure
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.TurnType

internal object NetworkFailureClassifier {
    fun classify(error: Throwable): NetworkFailure = when (error) {
        is UnknownHostException, is ConnectException -> NetworkFailure(NetworkFailureType.NO_NETWORK, error.message)
        is SocketTimeoutException, is InterruptedIOException -> NetworkFailure(NetworkFailureType.TIMEOUT, error.message)
        is HttpException -> NetworkFailure(NetworkFailureType.SERVER, "HTTP ${error.code()}")
        is IllegalStateException, is IllegalArgumentException -> NetworkFailure(NetworkFailureType.SERVER, error.message)
        else -> NetworkFailure(NetworkFailureType.UNKNOWN, error.message)
    }
}

internal class RetryOnFailureInterceptor(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 200L
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                val response = chain.proceed(request)
                if (!shouldRetry(request, response, attempt)) {
                    return response
                }
                response.close()
            } catch (error: Exception) {
                if (!request.isGet() || attempt >= maxAttempts || !isTransient(error)) {
                    throw error
                }
                lastError = error
            }
            Thread.sleep(baseDelayMillis * attempt)
        }
        throw lastError ?: IllegalStateException("Request retry exhausted")
    }

    private fun shouldRetry(request: Request, response: Response, attempt: Int): Boolean {
        return request.isGet() && response.code in 500..599 && attempt < maxAttempts
    }

    private fun isTransient(error: Exception): Boolean {
        return error is UnknownHostException || error is ConnectException || error is SocketTimeoutException || error is InterruptedIOException
    }

    private fun Request.isGet(): Boolean = method.equals("GET", ignoreCase = true)
}

internal object RouteCacheKeyFactory {
    fun fromCoordinate(coordinate: Coordinate): String {
        return listOf(coordinate.latitude.roundedTo4(), coordinate.longitude.roundedTo4()).joinToString(",")
    }

    private fun Double.roundedTo4(): String = ((this * 10_000.0).roundToInt() / 10_000.0).toString()
}

internal object RouteCacheStorage {
    fun adapter(moshi: Moshi): JsonAdapter<List<StoredRouteCacheEntry>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredRouteCacheEntry::class.java)
    )

    fun decode(rawValue: String?, adapter: JsonAdapter<List<StoredRouteCacheEntry>>): List<StoredRouteCacheEntry> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(rawValue).orEmpty() }.getOrDefault(emptyList())
    }

    fun encode(entries: List<StoredRouteCacheEntry>, adapter: JsonAdapter<List<StoredRouteCacheEntry>>): String = adapter.toJson(entries)
}

internal data class StoredRouteCacheEntry(
    val originKey: String,
    val destinationKey: String,
    val timestampMillis: Long,
    val route: StoredRoute
)

internal data class StoredRoute(
    val geometry: List<StoredCoordinate>,
    val maneuvers: List<StoredRouteManeuver>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val etaEpochSeconds: Long
) {
    fun toRoute(): Route = Route(
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

internal data class StoredCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun toCoordinate() = Coordinate(latitude = latitude, longitude = longitude)

    companion object {
        fun fromCoordinate(coordinate: Coordinate) = StoredCoordinate(coordinate.latitude, coordinate.longitude)
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
    fun toRouteManeuver() = RouteManeuver(
        id = id,
        coordinate = coordinate.toCoordinate(),
        instruction = instruction,
        turnType = TurnType.valueOf(turnType),
        roadName = roadName,
        distanceFromPreviousMeters = distanceFromPreviousMeters,
        distanceToNextMeters = distanceToNextMeters,
        authorization = ManeuverAuthorization.valueOf(authorization),
        headingBefore = headingBefore,
        headingAfter = headingAfter
    )

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
