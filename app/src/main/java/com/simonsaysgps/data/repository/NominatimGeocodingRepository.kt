package com.simonsaysgps.data.repository

import com.simonsaysgps.data.remote.NominatimApi
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.repository.GeocodingRepository
import javax.inject.Inject

class NominatimGeocodingRepository @Inject constructor(
    private val api: NominatimApi
) : GeocodingRepository {
    override suspend fun search(query: String): Result<List<PlaceResult>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        api.search(query).map {
            PlaceResult(
                id = it.place_id.toString(),
                name = it.name ?: it.display_name.substringBefore(','),
                fullAddress = it.display_name,
                coordinate = Coordinate(it.lat.toDouble(), it.lon.toDouble())
            )
        }
    }
}
