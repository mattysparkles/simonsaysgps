package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route

interface RoutingRepository {
    suspend fun calculateRoute(origin: Coordinate, destination: Coordinate): RepositoryResult<Route>
}
