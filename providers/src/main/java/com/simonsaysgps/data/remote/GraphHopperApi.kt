package com.simonsaysgps.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GraphHopperApi {
    @GET("route")
    suspend fun route(
        @Query("point") points: List<String>,
        @Query("profile") profile: String,
        @Query("instructions") instructions: Boolean = true,
        @Query("calc_points") calcPoints: Boolean = true,
        @Query("points_encoded") pointsEncoded: Boolean = false,
        @Query("key") apiKey: String
    ): GraphHopperRouteResponse
}

data class GraphHopperRouteResponse(
    val paths: List<GraphHopperPathDto>
)

data class GraphHopperPathDto(
    val distance: Double,
    val time: Long,
    val points: GraphHopperPointsDto,
    val instructions: List<GraphHopperInstructionDto>
)

data class GraphHopperPointsDto(
    val coordinates: List<List<Double>>
)

data class GraphHopperInstructionDto(
    val distance: Double,
    val text: String,
    val street_name: String? = null,
    val sign: Int,
    val interval: List<Int>,
    val time: Long
)
