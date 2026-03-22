package com.simonsaysgps.domain.service

interface NavigationForegroundServiceController {
    fun start(reason: String)
    fun stop(reason: String)
}
