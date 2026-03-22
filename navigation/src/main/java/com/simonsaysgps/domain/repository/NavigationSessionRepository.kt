package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.NavigationSessionState
import kotlinx.coroutines.flow.Flow

interface NavigationSessionRepository {
    val sessionState: Flow<NavigationSessionState?>

    suspend fun read(): NavigationSessionState?

    suspend fun save(state: NavigationSessionState)

    suspend fun clear()
}
