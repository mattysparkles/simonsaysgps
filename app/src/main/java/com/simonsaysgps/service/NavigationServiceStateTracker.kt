package com.simonsaysgps.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NavigationServiceStateTracker @Inject constructor() {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun markRunning() {
        _running.value = true
    }

    fun markStopped() {
        _running.value = false
    }
}
