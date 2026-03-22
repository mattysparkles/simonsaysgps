package com.simonsaysgps.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 8,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<NominatimPlaceDto>
}

data class NominatimPlaceDto(
    val place_id: Long,
    val display_name: String,
    val lat: String,
    val lon: String,
    val name: String? = null
)
