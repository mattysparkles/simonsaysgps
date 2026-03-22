package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun locationUpdates(): Flow<LocationSample>
}
