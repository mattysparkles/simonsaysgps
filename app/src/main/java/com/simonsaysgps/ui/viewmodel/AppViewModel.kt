package com.simonsaysgps.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simonsaysgps.data.location.DemoLocationRepository
import com.simonsaysgps.data.location.FusedLocationRepository
import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.model.NetworkFailure
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.VisitHistoryEntry
import com.simonsaysgps.domain.model.VisitObservationSource
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreReviewSourceSummary
import com.simonsaysgps.domain.model.explore.ExploreReviewSummary
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.domain.model.explore.ExploreSettings
import com.simonsaysgps.domain.model.explore.ExploreSourceAttribution
import com.simonsaysgps.domain.model.explore.InternalReviewAggregateCalculator
import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.CrowdReportType
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.SpeechCaptureState
import com.simonsaysgps.domain.model.voice.VoiceContext
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.repository.VisitHistoryRepository
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import com.simonsaysgps.domain.repository.explore.PlaceDetailRepository
import com.simonsaysgps.domain.repository.voice.CrowdReportRepository
import com.simonsaysgps.domain.repository.voice.ReviewDraftRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import com.simonsaysgps.domain.service.NavigationSessionOrchestrator
import com.simonsaysgps.domain.service.RoutingSupportAdvisor
import com.simonsaysgps.domain.service.VoicePromptManager
import com.simonsaysgps.domain.service.explore.ExploreOrchestrator
import com.simonsaysgps.domain.service.voice.VoiceAssistantManager
import com.simonsaysgps.domain.service.voice.VoiceDispatchResult
import com.simonsaysgps.domain.usecase.ObserveNavigationSessionUseCase
import com.simonsaysgps.ui.model.explore.PlaceDetailStatus
import com.simonsaysgps.ui.model.explore.PlaceDetailUiState
import com.simonsaysgps.ui.model.explore.PlaceDetailUiStateFactory
import com.simonsaysgps.ui.model.explore.ReviewComposeUiState
import com.simonsaysgps.ui.model.explore.ReviewComposeValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
    private val recentDestinationRepository: RecentDestinationRepository,
    private val routingRepository: RoutingRepository,
    private val settingsRepository: SettingsRepository,
    private val fusedLocationRepository: FusedLocationRepository,
    private val demoLocationRepository: DemoLocationRepository,
    private val navigationUseCase: ObserveNavigationSessionUseCase,
    private val simonSaysEngine: SimonSaysEngine,
    private val voicePromptManager: VoicePromptManager,
    private val navigationForegroundServiceController: NavigationForegroundServiceController,
    private val navigationSessionOrchestrator: NavigationSessionOrchestrator,
    private val exploreOrchestrator: ExploreOrchestrator,
    private val placeDetailRepository: PlaceDetailRepository,
    private val internalReviewRepository: InternalReviewRepository,
    private val visitHistoryRepository: VisitHistoryRepository,
    private val crowdReportRepository: CrowdReportRepository,
    private val reviewDraftRepository: ReviewDraftRepository,
    private val voiceAssistantManager: VoiceAssistantManager
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
    private var searchJob: Job? = null
    private var exploreJob: Job? = null
    private var placeDetailJob: Job? = null
    private var rerouteJob: Job? = null
    private var lastRecordedVisitPlaceId: String? = null

    init {
        viewModelScope.launch {
            val restoredSession = navigationSessionOrchestrator.restoreSession()
            if (restoredSession?.navigationActive == true && !_uiState.value.navigationState.navigationActive) {
                routeDestination = restoredSession.route?.geometry?.lastOrNull()
                _uiState.value = _uiState.value.copy(
                    routePreview = restoredSession.route,
                    navigationState = restoredSession,
                    currentLocation = restoredSession.currentLocation ?: _uiState.value.currentLocation,
                    isLoading = false
                )
                navigationSessionOrchestrator.ensureForegroundService("restored persisted navigation session")
            }
        }

        viewModelScope.launch {
            settings.collect { updated ->
                _uiState.value = _uiState.value.copy(
                    settings = updated,
                    isLoading = false,
                    explore = _uiState.value.explore.copy(
                        walkthroughVisible = !updated.exploreSettings.walkthroughSeen
                    )
                )
                if (_uiState.value.hasLocationPermission) startLocationUpdates()
            }
        }
        viewModelScope.launch {
            recentDestinationRepository.recentDestinations.collect { recentDestinations ->
                val currentState = _uiState.value
                _uiState.value = currentState.copy(
                    recentDestinations = recentDestinations,
                    searchStatus = currentState.searchStatus.normalizeForQuery(
                        query = currentState.searchQuery,
                        hasRecentDestinations = recentDestinations.isNotEmpty()
                    )
                )
            }
        }
        viewModelScope.launch {
            visitHistoryRepository.visitHistory.collect { visits ->
                _uiState.value = _uiState.value.copy(visitHistory = visits)
            }
        }
        viewModelScope.launch {
            crowdReportRepository.reports.collect { reports ->
                _uiState.value = _uiState.value.copy(
                    voiceAssistant = _uiState.value.voiceAssistant.copy(submittedReports = reports)
                )
            }
        }
        viewModelScope.launch {
            crowdReportRepository.pendingReport.collect { pending ->
                _uiState.value = _uiState.value.copy(
                    voiceAssistant = _uiState.value.voiceAssistant.copy(pendingReport = pending)
                )
            }
        }
        viewModelScope.launch {
            reviewDraftRepository.activeDraft.collect { draft ->
                _uiState.value = _uiState.value.copy(
                    voiceAssistant = _uiState.value.voiceAssistant.copy(activeReviewDraft = draft)
                )
            }
        }
        viewModelScope.launch {
            voiceAssistantManager.captureState.collect { captureState ->
                _uiState.value = _uiState.value.copy(
                    voiceAssistant = _uiState.value.voiceAssistant.copy(captureState = captureState)
                )
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasLocationPermission = granted, isLoading = false)
        if (granted) startLocationUpdates()
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            voiceAssistant = _uiState.value.voiceAssistant.copy(hasMicrophonePermission = granted)
        )
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                searchResults = emptyList(),
                searchError = null,
                searchInfo = null,
                searchInFlight = false,
                searchStatus = SearchStatus.RECENTS
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchError = null,
            searchInfo = null,
            searchInFlight = false,
            searchStatus = SearchStatus.DEBOUNCING
        )
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(query)
        }
    }

    fun search() {
        val query = _uiState.value.searchQuery
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                searchError = null,
                searchInfo = null,
                searchInFlight = false,
                searchStatus = SearchStatus.RECENTS
            )
            return
        }
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    fun selectPlace(place: PlaceResult) = selectPlaceInternal(place, saveRecent = true)

    fun previewExploreResult(result: ExploreResult) {
        selectPlaceInternal(result.toPlaceResult(), saveRecent = false)
        _uiState.value = _uiState.value.copy(
            explore = _uiState.value.explore.copy(actionMessage = "Preview ready on the map. Open route preview to navigate there.")
        )
    }

    fun saveExploreResult(result: ExploreResult) {
        selectPlaceInternal(result.toPlaceResult(), saveRecent = true)
        recordVisitForPlace(result.toPlaceResult(), VisitObservationSource.APP_CONFIRMED_SAVE, 0.82f, "Saved from Explore")
        _uiState.value = _uiState.value.copy(
            explore = _uiState.value.explore.copy(actionMessage = "Saved ${result.candidate.name} to recent destinations and first-party visit history.")
        )
    }

    fun openPlaceDetail(result: ExploreResult) {
        placeDetailJob?.cancel()
        selectPlaceInternal(result.toPlaceResult(), saveRecent = false)
        _uiState.value = _uiState.value.copy(
            placeDetail = PlaceDetailUiStateFactory.loading(result.candidate.id),
            reviewCompose = ReviewComposeUiState()
        )
        placeDetailJob = viewModelScope.launch {
            placeDetailRepository.observePlaceDetail(result).collect { detail ->
                _uiState.value = _uiState.value.copy(
                    placeDetail = PlaceDetailUiStateFactory.fromRecord(detail)
                )
                syncExploreReviewSummary(detail.canonicalPlaceId, detail.internalReviews)
            }
        }
    }

    fun clearPlaceDetail() {
        placeDetailJob?.cancel()
        _uiState.value = _uiState.value.copy(
            placeDetail = PlaceDetailUiState(),
            reviewCompose = ReviewComposeUiState()
        )
    }

    fun startLeaveReview() {
        val detail = _uiState.value.placeDetail
        val placeId = detail.seedPlaceId ?: return
        viewModelScope.launch {
            val existing = internalReviewRepository.observeOwnReview(placeId).first()
            _uiState.value = _uiState.value.copy(
                reviewCompose = ReviewComposeUiState(
                    canonicalPlaceId = placeId,
                    placeName = detail.title,
                    existingReviewId = existing?.internalReviewId,
                    rating = existing?.rating ?: _uiState.value.reviewCompose.rating,
                    reviewText = existing?.reviewText ?: _uiState.value.reviewCompose.reviewText,
                    selectedTags = existing?.tags ?: _uiState.value.reviewCompose.selectedTags,
                    helperMessage = if (existing == null) "Your review is stored locally on this device for now." else "Editing your local review for this place."
                )
            )
        }
    }

    fun updateReviewRating(rating: Int) {
        _uiState.value = _uiState.value.copy(
            reviewCompose = _uiState.value.reviewCompose.copy(rating = rating, validationError = null, successMessage = null)
        )
    }

    fun updateReviewText(reviewText: String) {
        _uiState.value = _uiState.value.copy(
            reviewCompose = _uiState.value.reviewCompose.copy(reviewText = reviewText, validationError = null, successMessage = null)
        )
    }

    fun toggleReviewTag(tag: PlaceReviewTag) {
        val current = _uiState.value.reviewCompose.selectedTags
        _uiState.value = _uiState.value.copy(
            reviewCompose = _uiState.value.reviewCompose.copy(
                selectedTags = if (tag in current) current - tag else current + tag,
                validationError = null,
                successMessage = null
            )
        )
    }

    fun submitReview() {
        val compose = _uiState.value.reviewCompose
        val placeId = compose.canonicalPlaceId ?: return
        val validationError = ReviewComposeValidator.validate(compose.rating, compose.reviewText)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                reviewCompose = compose.copy(validationError = validationError)
            )
            return
        }
        viewModelScope.launch {
            val author = internalReviewRepository.localAuthorDisplayName.first()
            val now = System.currentTimeMillis()
            val existing = internalReviewRepository.observeOwnReview(placeId).first()
            val reviewId = compose.existingReviewId ?: existing?.internalReviewId ?: "internal-review-$placeId-$now"
            val createdAt = existing?.createdAtEpochMillis ?: now
            internalReviewRepository.upsert(
                InternalPlaceReview(
                    internalReviewId = reviewId,
                    canonicalPlaceId = placeId,
                    authorDisplayName = author,
                    rating = compose.rating,
                    reviewText = compose.reviewText.trim(),
                    createdAtEpochMillis = createdAt,
                    updatedAtEpochMillis = now,
                    tags = compose.selectedTags,
                    visitContext = _uiState.value.placeDetail.whyChosen.takeIf { it.isNotBlank() }
                )
            )
            _uiState.value = _uiState.value.copy(
                reviewCompose = compose.copy(
                    existingReviewId = reviewId,
                    saving = false,
                    validationError = null,
                    successMessage = "Review saved locally and shown immediately."
                ),
                explore = _uiState.value.explore.copy(actionMessage = "Saved your internal review for ${_uiState.value.placeDetail.title}.")
            )
        }
    }

    fun dismissReviewComposeMessage() {
        _uiState.value = _uiState.value.copy(
            reviewCompose = _uiState.value.reviewCompose.copy(validationError = null, successMessage = null)
        )
    }

    fun removeOwnReview(reviewId: String) {
        viewModelScope.launch {
            internalReviewRepository.remove(reviewId)
            _uiState.value = _uiState.value.copy(
                reviewCompose = ReviewComposeUiState(),
                explore = _uiState.value.explore.copy(actionMessage = "Removed your local review from this device.")
            )
        }
    }

    fun reportReviewPlaceholder(author: String) {
        _uiState.value = _uiState.value.copy(
            explore = _uiState.value.explore.copy(actionMessage = "Reporting hooks are scaffolded for a future moderation flow. Review author: $author.")
        )
    }

    fun noteExploreAction(message: String) {
        _uiState.value = _uiState.value.copy(explore = _uiState.value.explore.copy(actionMessage = message))
    }

    fun clearExploreActionMessage() {
        _uiState.value = _uiState.value.copy(explore = _uiState.value.explore.copy(actionMessage = null))
    }

    fun loadExplore(category: ExploreCategory) {
        val location = _uiState.value.currentLocation?.coordinate
        if (location == null) {
            _uiState.value = _uiState.value.copy(
                explore = _uiState.value.explore.copy(
                    selectedCategory = category,
                    loading = false,
                    errorMessage = "Explore needs a current location before it can suggest nearby places.",
                    results = emptyList()
                )
            )
            return
        }

        exploreJob?.cancel()
        _uiState.value = _uiState.value.copy(
            explore = _uiState.value.explore.copy(
                selectedCategory = category,
                loading = true,
                errorMessage = null,
                infoMessage = category.helperText,
                actionMessage = null
            )
        )
        exploreJob = viewModelScope.launch {
            runCatching {
                exploreOrchestrator.explore(
                    ExploreQuery(
                        category = category,
                        userLocation = location,
                        activeRoute = _uiState.value.routePreview.takeIf { _uiState.value.navigationState.navigationActive },
                        settings = settings.value.exploreSettings
                    )
                )
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    explore = _uiState.value.explore.copy(
                        selectedCategory = category,
                        loading = false,
                        results = response.results,
                        providerStatuses = response.providerStatuses,
                        autoPicked = response.autoPicked,
                        errorMessage = null,
                        infoMessage = when {
                            response.results.isEmpty() -> "No strong matches yet. Try widening the radius or loosening filters in Explore settings."
                            response.autoPicked -> "Auto-pick mode is on, so Simon picked the single strongest suggestion for you."
                            else -> "Showing ${response.results.size} ranked suggestions from ${response.totalCandidatesConsidered} candidates."
                        }
                    )
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    explore = _uiState.value.explore.copy(
                        selectedCategory = category,
                        loading = false,
                        results = emptyList(),
                        providerStatuses = emptyList(),
                        errorMessage = "Explore could not rank suggestions right now: ${error.message ?: "unknown error"}."
                    )
                )
            }
        }
    }

    fun removeRecentDestination(placeId: String) {
        viewModelScope.launch {
            recentDestinationRepository.remove(placeId)
        }
    }

    fun clearRecentDestinations() {
        viewModelScope.launch {
            recentDestinationRepository.clear()
        }
    }

    fun removeVisitHistoryPlace(placeId: String) {
        viewModelScope.launch { visitHistoryRepository.remove(placeId) }
    }

    fun clearVisitHistory() {
        viewModelScope.launch { visitHistoryRepository.clear() }
    }

    fun requestRoute() {
        val origin = _uiState.value.currentLocation?.coordinate ?: return
        val destination = _uiState.value.selectedPlace?.coordinate ?: return
        routeDestination = destination
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(routeLoading = true, routeError = null, routeInfo = null)
            when (val routeResult = routingRepository.calculateRoute(origin, destination)) {
                is RepositoryResult.Success -> {
                    val route = simonSaysEngine.assignAuthorizations(routeResult.value, settings.value.gameMode)
                    _uiState.value = _uiState.value.copy(
                        routePreview = route,
                        routeLoading = false,
                        routeInfo = composeRouteInfo(routeResult.source, routeResult.fallbackFailure),
                        routeError = null
                    )
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        routeLoading = false,
                        routeError = routeErrorMessage(routeResult.failure),
                        routeInfo = null
                    )
                }
            }
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
        viewModelScope.launch { navigationSessionOrchestrator.syncSession(updatedState) }
    }

    fun endNavigation(reason: String = "navigation cancelled") {
        val wasActive = _uiState.value.navigationState.navigationActive
        Log.i(TAG, "Navigation ended. reason=$reason wasActive=$wasActive")
        voicePromptManager.stop()
        if (wasActive) {
            navigationForegroundServiceController.stop(reason)
        }
        _uiState.value = _uiState.value.copy(navigationState = NavigationSessionState())
        viewModelScope.launch { navigationSessionOrchestrator.syncSession(NavigationSessionState()) }
    }

    fun updateSettings(transform: (SettingsModel) -> SettingsModel) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateExploreSettings(transform: (ExploreSettings) -> ExploreSettings) {
        updateSettings { current -> current.copy(exploreSettings = transform(current.exploreSettings)) }
    }

    fun dismissExploreWalkthrough() {
        updateExploreSettings { it.copy(walkthroughSeen = true) }
        _uiState.value = _uiState.value.copy(explore = _uiState.value.explore.copy(walkthroughVisible = false))
    }

    fun saveCurrentLocationAsExploreHome(label: String = "Saved Home") {
        val location = _uiState.value.currentLocation?.coordinate ?: return
        updateExploreSettings { current -> current.copy(homeLabel = label, homeCoordinate = location) }
        noteExploreAction("Saved your current location as Explore home.")
    }

    fun clearExploreHome() {
        updateExploreSettings { current -> current.copy(homeLabel = "", homeCoordinate = null) }
        noteExploreAction("Cleared your Explore home anchor.")
    }

    fun updateVoiceTranscript(transcript: String) {
        _uiState.value = _uiState.value.copy(
            voiceAssistant = _uiState.value.voiceAssistant.copy(draftTranscript = transcript)
        )
    }

    fun startVoiceCapture() {
        voiceAssistantManager.startListening()
    }

    fun stopVoiceCapture() {
        voiceAssistantManager.stopListening()
    }

    fun submitVoiceTranscript() {
        val transcript = _uiState.value.voiceAssistant.draftTranscript
        if (!_uiState.value.settings.voiceAssistantSettings.enabled) {
            _uiState.value = _uiState.value.copy(
                voiceAssistant = _uiState.value.voiceAssistant.copy(lastActionMessage = "Voice assistant input is disabled in Settings.")
            )
            return
        }
        if (transcript.isBlank()) return
        viewModelScope.launch {
            val result = voiceAssistantManager.handleTranscript(transcript, currentVoiceContext())
            _uiState.value = _uiState.value.copy(
                voiceAssistant = _uiState.value.voiceAssistant.copy(lastTranscript = transcript, draftTranscript = "")
            )
            applyVoiceDispatchResult(result)
        }
    }

    fun stageManualReport(type: CrowdReportType) {
        updateVoiceTranscript("report ${type.label.lowercase()}")
        submitVoiceTranscript()
    }

    fun confirmPendingVoiceAction(accepted: Boolean) {
        viewModelScope.launch {
            applyVoiceDispatchResult(
                voiceAssistantManager.handleTranscript(if (accepted) "yes" else "no", currentVoiceContext())
            )
        }
    }

    fun startReviewDraft() {
        updateVoiceTranscript("leave a review for this place")
        submitVoiceTranscript()
    }

    fun updateReviewDraftTranscript(transcript: String) {
        viewModelScope.launch { reviewDraftRepository.updateRawTranscript(transcript) }
    }

    fun applyReviewCleanup(option: ReviewCleanupOption) {
        viewModelScope.launch {
            applyVoiceDispatchResult(
                voiceAssistantManager.handleTranscript(option.label, currentVoiceContext())
            )
        }
    }

    fun approveReviewDraft(text: String) {
        viewModelScope.launch {
            reviewDraftRepository.approveFinalText(text)
            _uiState.value = _uiState.value.copy(
                voiceAssistant = _uiState.value.voiceAssistant.copy(lastActionMessage = "Review saved locally and marked as approved.")
            )
        }
    }

    fun clearReviewDraft() {
        viewModelScope.launch { reviewDraftRepository.clearDraft() }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank() || query != _uiState.value.searchQuery) return
        _uiState.value = _uiState.value.copy(searchInFlight = true, searchError = null, searchInfo = null, searchStatus = SearchStatus.LOADING)
        when (val result = geocodingRepository.search(query)) {
            is RepositoryResult.Success -> {
                if (query != _uiState.value.searchQuery) return
                _uiState.value = _uiState.value.copy(
                    searchResults = result.value,
                    searchInFlight = false,
                    searchStatus = if (result.value.isEmpty()) SearchStatus.EMPTY else SearchStatus.SUCCESS,
                    searchInfo = searchMessageFor(result.source, result.fallbackFailure),
                    searchError = null
                )
            }

            is RepositoryResult.Failure -> {
                if (query != _uiState.value.searchQuery) return
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    searchError = searchErrorMessage(result.failure),
                    searchInfo = null,
                    searchInFlight = false,
                    searchStatus = SearchStatus.ERROR
                )
            }
        }
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            val repo = if (settings.value.demoMode) demoLocationRepository else fusedLocationRepository
            var previousLocation: com.simonsaysgps.domain.model.LocationSample? = null
            repo.locationUpdates().collect { location ->
                val currentState = _uiState.value.navigationState
                val updatedNavigation = if (currentState.navigationActive) {
                    navigationUseCase.updateState(
                        previousState = currentState,
                        previousLocation = previousLocation,
                        currentLocation = location,
                        distanceUnit = settings.value.distanceUnit,
                        promptPersonality = settings.value.promptPersonality
                    )
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
                if (currentState.arrivalStatus != com.simonsaysgps.domain.model.ArrivalStatus.ARRIVED && updatedNavigation.arrivalStatus == com.simonsaysgps.domain.model.ArrivalStatus.ARRIVED) {
                    _uiState.value.selectedPlace?.let { place ->
                        recordVisitForPlace(place, VisitObservationSource.APP_CONFIRMED_ARRIVAL, 0.96f, "Navigation arrival confirmed")
                    }
                }
                if (currentState.navigationActive || updatedNavigation.navigationActive) {
                    navigationSessionOrchestrator.syncSession(updatedNavigation)
                }
                if (simonSaysEngine.shouldReroute(updatedNavigation)) {
                    triggerReroute(location.coordinate, updatedNavigation.lastRerouteReason)
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

    private fun triggerReroute(origin: Coordinate, reason: com.simonsaysgps.domain.model.RerouteReason) {
        if (rerouteJob?.isActive == true) {
            Log.d(TAG, "Reroute already in flight; suppressing duplicate request. reason=$reason")
            return
        }
        val destination = routeDestination ?: return
        rerouteJob = viewModelScope.launch {
            when (val routeResult = navigationUseCase.reroute(origin, destination, _uiState.value.navigationState.route, reason)) {
                is RepositoryResult.Success -> {
                    val route = simonSaysEngine.assignAuthorizations(routeResult.value, settings.value.gameMode)
                    val previousState = _uiState.value.navigationState
                    val updatedState = navigationUseCase.start(route).copy(
                        rerouteCooldownUntilMillis = System.currentTimeMillis() + 8_000L,
                        debugInfo = previousState.debugInfo.copy(
                            rerouteSuppressionReason = "reroute-completed-cooldown",
                            lastTransitionReason = "reroute-completed:$reason"
                        )
                    )
                    Log.i(TAG, "Reroute completed. maneuverCount=${route.maneuvers.size} source=${routeResult.source} reason=$reason")
                    _uiState.value = _uiState.value.copy(
                        routePreview = route,
                        navigationState = updatedState,
                        routeInfo = composeRouteInfo(routeResult.source, routeResult.fallbackFailure),
                        routeError = null
                    )
                    navigationSessionOrchestrator.syncSession(updatedState)
                    syncForegroundService(previousState, updatedState)
                }

                is RepositoryResult.Failure -> {
                    Log.w(TAG, "Reroute failed: ${routeResult.failure.type} ${routeResult.failure.detail}")
                    _uiState.value = _uiState.value.copy(routeError = routeErrorMessage(routeResult.failure))
                }
            }
        }
    }

    private fun selectPlaceInternal(place: PlaceResult, saveRecent: Boolean) {
        _uiState.value = _uiState.value.copy(selectedPlace = place)
        if (saveRecent) {
            viewModelScope.launch { recentDestinationRepository.save(place) }
        }
    }

    private fun ExploreResult.toPlaceResult() = PlaceResult(
        id = candidate.id,
        name = candidate.name,
        fullAddress = candidate.address,
        coordinate = candidate.coordinate
    )

    private fun syncExploreReviewSummary(placeId: String, reviews: List<InternalPlaceReview>) {
        val aggregate = InternalReviewAggregateCalculator.calculate(reviews)
        val updatedResults = _uiState.value.explore.results.map { result ->
            if (result.candidate.id != placeId) return@map result
            val existingExternal = result.candidate.reviewSummary?.thirdPartySources.orEmpty()
            val internalSource = aggregate?.let {
                ExploreReviewSourceSummary(
                    provider = "internal-community",
                    providerLabel = "Simon Says GPS",
                    averageRating = it.averageRating,
                    reviewCount = it.count,
                    summary = it.topTags.takeIf { tags -> tags.isNotEmpty() }?.joinToString(prefix = "Tagged: ") { tag -> tag.label },
                    internal = true,
                    attribution = ExploreSourceAttribution("internal-community", "Simon Says GPS", verified = true),
                    confidence = 0.99f
                )
            }
            val sources = listOfNotNull(internalSource) + existingExternal
            result.copy(
                candidate = result.candidate.copy(
                    reviewSummary = if (sources.isEmpty()) {
                        null
                    } else {
                        ExploreReviewSummary(
                            averageRating = internalSource?.averageRating ?: sources.mapNotNull { it.averageRating }.average(),
                            totalCount = sources.sumOf { it.reviewCount },
                            internalAverageRating = internalSource?.averageRating,
                            internalCount = internalSource?.reviewCount ?: 0,
                            summary = internalSource?.summary ?: sources.firstNotNullOfOrNull { it.summary },
                            sources = sources
                        )
                    }
                )
            )
        }
        _uiState.value = _uiState.value.copy(explore = _uiState.value.explore.copy(results = updatedResults))
    }

    private fun searchMessageFor(source: FetchSource, fallbackFailure: NetworkFailure?): String? {
        return when {
            source == FetchSource.CACHE && fallbackFailure != null -> "Showing cached search results because ${networkReasonLabel(fallbackFailure)}."
            source == FetchSource.CACHE -> "Showing recently cached search results."
            else -> null
        }
    }

    private fun routeMessageFor(source: FetchSource, fallbackFailure: NetworkFailure?): String? {
        return when {
            source == FetchSource.CACHE && fallbackFailure != null -> "Using the latest saved route because ${networkReasonLabel(fallbackFailure)}."
            source == FetchSource.CACHE -> "Using a recently cached route preview."
            else -> null
        }
    }

    private fun composeRouteInfo(source: FetchSource, fallbackFailure: NetworkFailure?): String {
        val plan = RoutingSupportAdvisor.plan(settings.value)
        val parts = listOfNotNull(
            routeMessageFor(source, fallbackFailure),
            "Transport profile: ${settings.value.routingPreferences.transportProfile.displayName}.",
            "Requested route styles: ${plan.requestedStyles.joinToString()}.",
            plan.advisory.summary.takeIf { plan.advisory.limitations.isNotEmpty() }
        )
        return parts.joinToString(" ")
    }


    private fun recordVisitForPlace(
        place: PlaceResult,
        source: VisitObservationSource,
        confidence: Float,
        notes: String
    ) {
        if (lastRecordedVisitPlaceId == place.id && source == VisitObservationSource.APP_CONFIRMED_ARRIVAL) return
        if (!_uiState.value.settings.exploreSettings.visitHistoryEnabled) return
        if (source == VisitObservationSource.APP_CONFIRMED_ARRIVAL) {
            lastRecordedVisitPlaceId = place.id
        }
        viewModelScope.launch {
            visitHistoryRepository.record(
                VisitHistoryEntry(
                    id = "visit-${place.id}-${System.currentTimeMillis()}",
                    placeId = place.id,
                    name = place.name,
                    address = place.fullAddress,
                    coordinate = place.coordinate,
                    visitedAtEpochMillis = System.currentTimeMillis(),
                    confidence = confidence,
                    source = source,
                    notes = notes
                )
            )
        }
    }

    private fun searchErrorMessage(failure: NetworkFailure): String = when (failure.type) {
        NetworkFailureType.NO_NETWORK -> "No network connection. Search needs internet unless you already have cached results for this query."
        NetworkFailureType.TIMEOUT -> "Search timed out. Please try again in a moment."
        NetworkFailureType.SERVER -> "Search server error. Please try again shortly."
        NetworkFailureType.UNKNOWN -> "Search failed unexpectedly. Please try again."
    }

    private fun routeErrorMessage(failure: NetworkFailure): String = when (failure.type) {
        NetworkFailureType.NO_NETWORK -> "No network connection. Route calculation still needs internet unless this trip was cached recently."
        NetworkFailureType.TIMEOUT -> "Route request timed out. Please try again."
        NetworkFailureType.SERVER -> failure.detail?.let { "Routing service error: $it" }
            ?: "Routing service error. Please try again shortly."
        NetworkFailureType.UNKNOWN -> "Route request failed unexpectedly. Please try again."
    }

    private fun networkReasonLabel(failure: NetworkFailure): String = when (failure.type) {
        NetworkFailureType.NO_NETWORK -> "the device is offline"
        NetworkFailureType.TIMEOUT -> "the network request timed out"
        NetworkFailureType.SERVER -> "the server did not respond successfully"
        NetworkFailureType.UNKNOWN -> "the latest network request failed"
    }

    private fun currentVoiceContext(): VoiceContext = VoiceContext(
        currentLocation = _uiState.value.currentLocation?.coordinate,
        selectedPlace = _uiState.value.selectedPlace,
        navigationActive = _uiState.value.navigationState.navigationActive
    )

    private fun applyVoiceDispatchResult(result: VoiceDispatchResult) {
        val message = when (result) {
            is VoiceDispatchResult.Search -> {
                _uiState.value = _uiState.value.copy(searchQuery = result.query)
                search()
                result.spokenConfirmation
            }
            is VoiceDispatchResult.Explore -> {
                val category = when {
                    result.spokenConfirmation.contains("fun", ignoreCase = true) -> ExploreCategory.FUN
                    result.spokenConfirmation.contains("delicious", ignoreCase = true) -> ExploreCategory.DELICIOUS
                    else -> ExploreCategory.ON_MY_WAY
                }
                loadExplore(category)
                result.spokenConfirmation
            }
            is VoiceDispatchResult.ReportStaged -> result.spokenConfirmation
            is VoiceDispatchResult.ReportSubmitted -> result.spokenConfirmation
            is VoiceDispatchResult.ReviewStarted -> result.spokenConfirmation
            is VoiceDispatchResult.ReviewUpdated -> result.spokenConfirmation
            is VoiceDispatchResult.SoundtrackQueued -> result.result.message
            is VoiceDispatchResult.NoOp -> result.spokenConfirmation
        }
        if (_uiState.value.settings.voiceAssistantSettings.spokenConfirmationsEnabled) {
            voicePromptManager.speak(message)
        }
        _uiState.value = _uiState.value.copy(
            voiceAssistant = _uiState.value.voiceAssistant.copy(lastActionMessage = message)
        )
    }

    companion object {
        private const val TAG = "AppViewModel"
        internal const val SEARCH_DEBOUNCE_MS = 400L
    }
}

data class ExploreUiState(
    val selectedCategory: ExploreCategory? = null,
    val loading: Boolean = false,
    val results: List<ExploreResult> = emptyList(),
    val providerStatuses: List<ExploreProviderStatus> = emptyList(),
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val autoPicked: Boolean = false,
    val walkthroughVisible: Boolean = true
)

data class VoiceAssistantUiState(
    val hasMicrophonePermission: Boolean = false,
    val captureState: SpeechCaptureState = SpeechCaptureState.Idle,
    val draftTranscript: String = "",
    val lastTranscript: String? = null,
    val lastActionMessage: String? = null,
    val pendingReport: CrowdReport? = null,
    val submittedReports: List<CrowdReport> = emptyList(),
    val activeReviewDraft: ReviewDraft? = null
)

data class AppUiState(
    val isLoading: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val recentDestinations: List<PlaceResult> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus.RECENTS,
    val selectedPlace: PlaceResult? = null,
    val routePreview: Route? = null,
    val currentLocation: com.simonsaysgps.domain.model.LocationSample? = null,
    val navigationState: NavigationSessionState = NavigationSessionState(),
    val searchInFlight: Boolean = false,
    val routeLoading: Boolean = false,
    val searchError: String? = null,
    val searchInfo: String? = null,
    val routeError: String? = null,
    val routeInfo: String? = null,
    val lastPrompt: String? = null,
    val settings: SettingsModel = SettingsModel(),
    val visitHistory: List<VisitHistoryEntry> = emptyList(),
    val explore: ExploreUiState = ExploreUiState(),
    val placeDetail: PlaceDetailUiState = PlaceDetailUiState(),
    val reviewCompose: ReviewComposeUiState = ReviewComposeUiState(),
    val voiceAssistant: VoiceAssistantUiState = VoiceAssistantUiState()
)

enum class SearchStatus {
    RECENTS,
    DEBOUNCING,
    LOADING,
    SUCCESS,
    EMPTY,
    ERROR;

    fun normalizeForQuery(query: String, hasRecentDestinations: Boolean): SearchStatus {
        return if (query.isBlank()) {
            RECENTS
        } else if (this == RECENTS && !hasRecentDestinations) {
            EMPTY
        } else {
            this
        }
    }
}
