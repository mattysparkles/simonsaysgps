package com.simonsaysgps.data.location

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.repository.LocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoLocationRepository @Inject constructor() : LocationRepository {
    private val path = listOf(
        Coordinate(40.7484, -73.9857),
        Coordinate(40.7488, -73.9850),
        Coordinate(40.7492, -73.9842),
        Coordinate(40.7498, -73.9835),
        Coordinate(40.7504, -73.9827),
        Coordinate(40.7510, -73.9820)
    )

    override fun locationUpdates(): Flow<LocationSample> = flow {
        while (true) {
            path.forEachIndexed { index, coordinate ->
                val prev = path.getOrNull(index - 1) ?: coordinate
                emit(
                    LocationSample(
                        coordinate = coordinate,
                        accuracyMeters = 8f,
                        bearing = com.simonsaysgps.domain.util.GeoUtils.bearingDegrees(prev, coordinate).toFloat(),
                        speedMetersPerSecond = 8f,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                delay(2_000)
            }
        }
    }
}
