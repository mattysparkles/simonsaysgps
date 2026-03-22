package com.simonsaysgps.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 8,
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("extratags") extraTags: Int = 1,
        @Query("namedetails") nameDetails: Int = 1,
        @Query("viewbox") viewbox: String? = null,
        @Query("bounded") bounded: Int? = null
    ): List<NominatimPlaceDto>
}

data class NominatimPlaceDto(
    val place_id: Long,
    val osm_id: Long? = null,
    val osm_type: String? = null,
    val display_name: String,
    val lat: String,
    val lon: String,
    val name: String? = null,
    val category: String? = null,
    val type: String? = null,
    val address: Map<String, String>? = null,
    val extratags: Map<String, String>? = null,
    val namedetails: Map<String, String>? = null
)
