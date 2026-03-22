package com.simonsaysgps.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simonsaysgps.data.location.DemoLocationRepository
import com.simonsaysgps.data.location.FusedLocationRepository
import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import com.simonsaysgps.domain.service.VoicePromptManager
import com.simonsaysgps.domain.usecase.ObserveNavigationSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
    private val routingRepository: RoutingRepository,
    private val settingsRepository: SettingsRepository,
    private val fusedLocationRepository: FusedLocationRepository,
    private val demoLocationRepository: DemoLocationRepository,
    private val navigationUseCase: ObserveNavigationSessionUseCase,
    private val simonSaysEngine: SimonSaysEngine,
    private val voicePromptManager: VoicePromptManager,
    private val navigationForegroundServiceController: NavigationForegroundServiceController
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val settings: StateFlow<SettingsModel> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsModel()
    )

    private var locationJob: Job? = null
    private var routeDestination: Coordinate? = null

    init {
        viewModelScope.launch {
            settings.collect { updated ->
                _uiState.value = _uiState.value.copy(settings = updated, isLoading = false)
                if (_uiState.value.hasLocationPermission) startLocationUpdates()
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasLocationPermission = granted, isLoading = false)
        if (granted) startLocationUpdates()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        val query = _uiState.value.searchQuery
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchInFlight = true, searchError = null)
            geocodingRepository.search(query)
                .onSuccess { results -> _uiState.value = _uiState.value.copy(searchResults = results, searchInFlight = false) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(searchError = error.message, searchInFlight = false) }
        }
    }

    fun selectPlace(place: PlaceResult) {
        _uiState.value = _uiState.value.copy(selectedPlace = place)
    }

    fun requestRoute() {
        val origin = _uiState.value.currentLocation?.coordinate ?: return
        val destination = _uiState.value.selectedPlace?.coordinate ?: return
        routeDestination = destination
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(routeLoading = true, routeError = null)
            routingRepository.calculateRoute(origin, destination)
                .map { simonSaysEngine.assignAuthorizations(it, settings.value.gameMode) }
                .onSuccess { route -> _uiState.value = _uiState.value.copy(routePreview = route, routeLoading = false) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(routeError = error.message, routeLoading = false) }
        }
    }

    fun startNavigation() {
        if (_uiState.value.navigationState.navigationActive) {
            Log.d(TAG, "Ignoring startNavigation because a navigation session is already active.")
            return
        }

        val route = _uiState.value.routePreview ?: return
        val updatedState = navigationUseCase.start(route)
        Log.i(TAG, "Navigation started. routeManeuvers=${route.maneuvers.size}")
        _uiState.value = _uiState.value.copy(navigationState = updatedState)
        navigationForegroundServiceController.start("turn-by-turn navigation began")
    }

    fun endNavigation(reason: String = "navigation cancelled") {
        val wasActive = _uiState.value.navigationState.navigationActive
        Log.i(TAG, "Navigation ended. reason=$reason wasActive=$wasActive")
        voicePromptManager.stop()
        if (wasActive) {
            navigationForegroundServiceController.stop(reason)
        }
        _uiState.value = _uiState.value.copy(navigationState = NavigationSessionState())
    }

    fun updateSettings(transform: (SettingsModel) -> SettingsModel) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            val repo = if (settings.value.demoMode) demoLocationRepository else fusedLocationRepository
            var previousLocation: com.simonsaysgps.domain.model.LocationSample? = null
            repo.locationUpdates().collect { location ->
                val currentState = _uiState.value.navigationState
                val updatedNavigation = if (currentState.navigationActive) {
                    navigationUseCase.updateState(currentState, previousLocation, location, settings.value.distanceUnit)
                } else {
                    currentState.copy(currentLocation = location)
                }
                syncForegroundService(currentState, updatedNavigation)
                if (updatedNavigation.spokenPrompt != null && settings.value.voiceEnabled) {
                    voicePromptManager.speak(updatedNavigation.spokenPrompt)
                }
                _uiState.value = _uiState.value.copy(
                    currentLocation = location,
                    navigationState = updatedNavigation,
                    lastPrompt = updatedNavigation.spokenPrompt ?: _uiState.value.lastPrompt
                )
                if (simonSaysEngine.shouldReroute(updatedNavigation)) {
                    triggerReroute(location.coordinate)
                }
                previousLocation = location
            }
        }
    }

    private fun syncForegroundService(
        previousState: NavigationSessionState,
        updatedState: NavigationSessionState
    ) {
        when {
            !previousState.navigationActive && updatedState.navigationActive -> {
                Log.i(TAG, "Foreground service transition: inactive -> active")
                navigationForegroundServiceController.start("navigation session became active")
            }

            previousState.navigationActive && !updatedState.navigationActive -> {
                val reason = when {
                    updatedState.upcomingManeuver == null -> "destination reached"
                    else -> "navigation session became inactive"
                }
                Log.i(TAG, "Foreground service transition: active -> inactive. reason=$reason")
                voicePromptManager.stop()
                navigationForegroundServiceController.stop(reason)
            }

            previousState.navigationActive && updatedState.navigationActive -> {
                Log.d(TAG, "Foreground service transition: active -> active")
            }

            else -> {
                Log.d(TAG, "Foreground service transition: inactive -> inactive")
            }
        }
    }


    private fun triggerReroute(origin: Coordinate) {
        val destination = routeDestination ?: return
        viewModelScope.launch {
            routingRepository.calculateRoute(origin, destination)
                .map { simonSaysEngine.assignAuthorizations(it, settings.value.gameMode) }
                .onSuccess { route ->
                    val previousState = _uiState.value.navigationState
                    val updatedState = navigationUseCase.start(route)
                    Log.i(TAG, "Reroute completed. maneuverCount=${route.maneuvers.size}")
                    _uiState.value = _uiState.value.copy(
                        routePreview = route,
                        navigationState = updatedState
                    )
                    syncForegroundService(previousState, updatedState)
                }
        }
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}

data class AppUiState(
    val isLoading: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val selectedPlace: PlaceResult? = null,
    val routePreview: Route? = null,
    val currentLocation: com.simonsaysgps.domain.model.LocationSample? = null,
    val navigationState: NavigationSessionState = NavigationSessionState(),
    val searchInFlight: Boolean = false,
    val routeLoading: Boolean = false,
    val searchError: String? = null,
    val routeError: String? = null,
    val lastPrompt: String? = null,
    val settings: SettingsModel = SettingsModel()
)
