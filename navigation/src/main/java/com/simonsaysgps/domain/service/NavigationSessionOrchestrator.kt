package com.simonsaysgps.domain.service

import com.simonsaysgps.domain.model.NavigationSessionState

interface NavigationSessionOrchestrator {
    suspend fun restoreSession(): NavigationSessionState?

    suspend fun syncSession(state: NavigationSessionState)

    fun ensureForegroundService(reason: String)
}
