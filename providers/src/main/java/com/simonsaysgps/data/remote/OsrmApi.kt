package com.simonsaysgps.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OsrmApi {
    @GET("route/v1/driving/{coordinates}")
    suspend fun route(
        @Path(value = "coordinates", encoded = true) coordinates: String,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true,
        @Query("annotations") annotations: Boolean = false
    ): OsrmRouteResponse
}

data class OsrmRouteResponse(
    val routes: List<OsrmRouteDto>,
    val code: String
)

data class OsrmRouteDto(
    val geometry: OsrmGeometryDto,
    val distance: Double,
    val duration: Double,
    val legs: List<OsrmLegDto>
)

data class OsrmGeometryDto(
    val coordinates: List<List<Double>>
)

data class OsrmLegDto(
    val steps: List<OsrmStepDto>
)

data class OsrmStepDto(
    val distance: Double,
    val duration: Double,
    val name: String,
    val geometry: OsrmGeometryDto,
    val maneuver: OsrmManeuverDto,
    val mode: String
)

data class OsrmManeuverDto(
    val location: List<Double>,
    val type: String,
    val modifier: String? = null,
    val bearing_before: Int? = null,
    val bearing_after: Int? = null,
    val instruction: String? = null
)
