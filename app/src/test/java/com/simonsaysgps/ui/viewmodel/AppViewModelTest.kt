package com.simonsaysgps.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.location.DemoLocationRepository
import com.simonsaysgps.data.location.FusedLocationRepository
import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.engine.TurnDetector
import com.simonsaysgps.domain.model.ArrivalStatus
import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.LocationSample
import com.simonsaysgps.domain.model.ManeuverAuthorization
import com.simonsaysgps.domain.model.FetchSource
import com.simonsaysgps.domain.model.NetworkFailure
import com.simonsaysgps.domain.model.NetworkFailureType
import com.simonsaysgps.domain.model.PlaceResult
import com.simonsaysgps.domain.model.RepositoryResult
import com.simonsaysgps.domain.model.Route
import com.simonsaysgps.domain.model.RouteManeuver
import com.simonsaysgps.domain.model.SettingsModel
import com.simonsaysgps.domain.model.explore.ExploreCategory
import com.simonsaysgps.domain.model.explore.InternalPlaceReview
import com.simonsaysgps.domain.model.explore.SavedPlaceRecord
import com.simonsaysgps.domain.model.explore.PlaceDetailRecord
import com.simonsaysgps.domain.model.explore.PlaceReviewTag
import com.simonsaysgps.domain.model.voice.CrowdReport
import com.simonsaysgps.domain.model.voice.ReviewCleanupOption
import com.simonsaysgps.domain.model.voice.ReviewDraft
import com.simonsaysgps.domain.model.voice.SpeechCaptureState
import com.simonsaysgps.domain.model.voice.VoiceContext
import com.simonsaysgps.domain.model.explore.ExploreProviderStatus
import com.simonsaysgps.domain.model.explore.ExploreQuery
import com.simonsaysgps.domain.model.explore.ExploreResponse
import com.simonsaysgps.domain.model.explore.ExploreResult
import com.simonsaysgps.domain.service.explore.ExploreOrchestrator
import com.simonsaysgps.domain.service.voice.VoiceAssistantManager
import com.simonsaysgps.domain.service.voice.VoiceDispatchResult
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.repository.VisitHistoryRepository
import com.simonsaysgps.domain.repository.explore.InternalReviewRepository
import com.simonsaysgps.domain.repository.explore.PlaceDetailRepository
import com.simonsaysgps.domain.repository.explore.SavedPlaceRepository
import com.simonsaysgps.domain.repository.voice.CrowdReportRepository
import com.simonsaysgps.domain.repository.voice.ReviewDraftRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import com.simonsaysgps.domain.service.NavigationSessionOrchestrator
import com.simonsaysgps.domain.service.PromptFactory
import com.simonsaysgps.domain.service.VoicePromptManager
import com.simonsaysgps.domain.usecase.ObserveNavigationSessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting navigation starts foreground service only once and ending navigation stops it`() = runTest(dispatcher) {
        val locationFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 4)
        val serviceController = FakeNavigationForegroundServiceController()
        val voicePromptManager = mockk<VoicePromptManager>(relaxed = true)
        val orchestrator = FakeNavigationSessionOrchestrator()
        val viewModel = createViewModel(
            locationFlow = locationFlow,
            serviceController = serviceController,
            voicePromptManager = voicePromptManager,
            route = routeWithSingleManeuver(),
            navigationSessionOrchestrator = orchestrator
        )

        viewModel.onLocationPermissionResult(true)
        locationFlow.emit(sample(0.0, 0.0, 0f))
        advanceUntilIdle()

        viewModel.selectPlace(destinationPlace())
        viewModel.requestRoute()
        advanceUntilIdle()

        viewModel.startNavigation()
        viewModel.startNavigation()
        viewModel.endNavigation("user ended navigation")
        advanceUntilIdle()

        assertThat(serviceController.startReasons).containsExactly("turn-by-turn navigation began")
        assertThat(serviceController.stopReasons).containsExactly("user ended navigation")
        assertThat(orchestrator.syncedStates.map { it.navigationActive }).containsExactly(true, false).inOrder()
        verify(exactly = 1) { voicePromptManager.stop() }
    }

    @Test
    fun `reaching destination stops foreground service automatically`() = runTest(dispatcher) {
        val locationFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 4)
        val serviceController = FakeNavigationForegroundServiceController()
        val voicePromptManager = mockk<VoicePromptManager>(relaxed = true)
        val viewModel = createViewModel(
            locationFlow = locationFlow,
            serviceController = serviceController,
            voicePromptManager = voicePromptManager,
            route = routeWithSingleManeuver()
        )

        viewModel.onLocationPermissionResult(true)
        locationFlow.emit(sample(0.0, 0.0, 0f))
        advanceUntilIdle()

        viewModel.selectPlace(destinationPlace())
        viewModel.requestRoute()
        advanceUntilIdle()

        viewModel.startNavigation()
        locationFlow.emit(sample(0.0, 0.0001, 90f))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.navigationState.navigationActive).isFalse()
        assertThat(viewModel.uiState.value.navigationState.arrivalStatus).isEqualTo(ArrivalStatus.ARRIVED)
        assertThat(serviceController.startReasons).containsExactly("turn-by-turn navigation began")
        assertThat(serviceController.stopReasons).containsExactly("destination reached")
        verify(exactly = 1) { voicePromptManager.stop() }
        verify { voicePromptManager.speak(any()) }
    }

    @Test
    fun `restored persisted navigation session repopulates ui state and restarts orchestration`() = runTest(dispatcher) {
        val restoredState = com.simonsaysgps.domain.model.NavigationSessionState(
            route = routeWithSingleManeuver(),
            currentLocation = sample(0.0, 0.0, 0f),
            activeManeuverIndex = 0,
            upcomingManeuver = routeWithSingleManeuver().maneuvers.first(),
            navigationActive = true
        )
        val serviceController = FakeNavigationForegroundServiceController()
        val orchestrator = FakeNavigationSessionOrchestrator(restoredSession = restoredState)
        val viewModel = createViewModel(
            serviceController = serviceController,
            navigationSessionOrchestrator = orchestrator
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.navigationState).isEqualTo(restoredState)
        assertThat(viewModel.uiState.value.routePreview).isEqualTo(restoredState.route)
        assertThat(viewModel.uiState.value.currentLocation).isEqualTo(restoredState.currentLocation)
        assertThat(orchestrator.ensuredForegroundReasons).containsExactly("restored persisted navigation session")
        assertThat(orchestrator.restoreCalls).isEqualTo(1)
    }

    @Test
    fun `search query is debounced before geocoding`() = runTest(dispatcher) {
        val geocodingRepository = FakeGeocodingRepository(
            responses = mapOf("san francisco" to RepositoryResult.Success(listOf(destinationPlace()), FetchSource.NETWORK))
        )
        val viewModel = createViewModel(geocodingRepository = geocodingRepository)

        viewModel.updateSearchQuery("san")
        advanceTimeBy(AppViewModel.SEARCH_DEBOUNCE_MS / 2)
        viewModel.updateSearchQuery("san fran")
        advanceTimeBy(AppViewModel.SEARCH_DEBOUNCE_MS / 2)
        viewModel.updateSearchQuery("san francisco")
        advanceTimeBy(AppViewModel.SEARCH_DEBOUNCE_MS - 1)
        advanceUntilIdle()

        assertThat(geocodingRepository.queries).isEmpty()
        assertThat(viewModel.uiState.value.searchStatus).isEqualTo(SearchStatus.DEBOUNCING)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertThat(geocodingRepository.queries).containsExactly("san francisco")
        assertThat(viewModel.uiState.value.searchResults).containsExactly(destinationPlace())
        assertThat(viewModel.uiState.value.searchStatus).isEqualTo(SearchStatus.SUCCESS)
    }

    @Test
    fun `selecting and clearing recent destinations updates repository-backed state`() = runTest(dispatcher) {
        val recentRepository = FakeRecentDestinationRepository()
        val placeOne = destinationPlace()
        val placeTwo = destinationPlace(id = "coffee", name = "Coffee Shop")
        val viewModel = createViewModel(recentDestinationRepository = recentRepository)

        viewModel.selectPlace(placeOne)
        advanceUntilIdle()
        viewModel.selectPlace(placeTwo)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recentDestinations).containsExactly(placeTwo, placeOne).inOrder()

        viewModel.removeRecentDestination(placeOne.id)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recentDestinations).containsExactly(placeTwo)

        viewModel.clearRecentDestinations()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recentDestinations).isEmpty()
        assertThat(viewModel.uiState.value.searchStatus).isEqualTo(SearchStatus.RECENTS)
    }


    @Test
    fun `cached search fallback surfaces offline message without failing UI`() = runTest(dispatcher) {
        val geocodingRepository = FakeGeocodingRepository(
            responses = mapOf(
                "san francisco" to RepositoryResult.Success(
                    value = listOf(destinationPlace()),
                    source = FetchSource.CACHE,
                    fallbackFailure = NetworkFailure(NetworkFailureType.NO_NETWORK)
                )
            )
        )
        val viewModel = createViewModel(geocodingRepository = geocodingRepository)

        viewModel.updateSearchQuery("san francisco")
        advanceTimeBy(AppViewModel.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.searchStatus).isEqualTo(SearchStatus.SUCCESS)
        assertThat(viewModel.uiState.value.searchInfo).contains("cached search results")
        assertThat(viewModel.uiState.value.searchError).isNull()
    }

    @Test
    fun `route failures distinguish timeout from empty results`() = runTest(dispatcher) {
        val geocodingRepository = FakeGeocodingRepository()
        val routingRepository = mockk<RoutingRepository>()
        val locationFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 4)
        val fusedLocationRepository = mockk<FusedLocationRepository>()
        val demoLocationRepository = mockk<DemoLocationRepository>()
        val serviceController = FakeNavigationForegroundServiceController()
        val voicePromptManager = mockk<VoicePromptManager>(relaxed = true)
        val settingsRepository = FakeSettingsRepository()
        val navigationSessionOrchestrator = FakeNavigationSessionOrchestrator()
        val savedPlaceRepository = FakeSavedPlaceRepository()
        val engine = SimonSaysEngine(TurnDetector(), PromptFactory())
        val navigationUseCase = ObserveNavigationSessionUseCase(routingRepository = routingRepository, simonSaysEngine = engine)

        every { fusedLocationRepository.locationUpdates() } returns locationFlow
        every { demoLocationRepository.locationUpdates() } returns locationFlow
        coEvery { routingRepository.calculateRoute(any(), any()) } returns RepositoryResult.Failure(NetworkFailure(NetworkFailureType.TIMEOUT))

        val viewModel = AppViewModel(
            geocodingRepository = geocodingRepository,
            recentDestinationRepository = FakeRecentDestinationRepository(),
            routingRepository = routingRepository,
            settingsRepository = settingsRepository,
            fusedLocationRepository = fusedLocationRepository,
            demoLocationRepository = demoLocationRepository,
            navigationUseCase = navigationUseCase,
            simonSaysEngine = engine,
            voicePromptManager = voicePromptManager,
            navigationForegroundServiceController = serviceController,
            navigationSessionOrchestrator = navigationSessionOrchestrator,
            exploreOrchestrator = FakeExploreOrchestrator(),
            placeDetailRepository = FakePlaceDetailRepository(FakeInternalReviewRepository(), savedPlaceRepository),
            internalReviewRepository = FakeInternalReviewRepository(),
            savedPlaceRepository = savedPlaceRepository,
            visitHistoryRepository = FakeVisitHistoryRepository(),
            crowdReportRepository = FakeCrowdReportRepository(),
            reviewDraftRepository = FakeReviewDraftRepository(),
            voiceAssistantManager = FakeVoiceAssistantManager()
        )

        viewModel.onLocationPermissionResult(true)
        locationFlow.emit(sample(0.0, 0.0, 0f))
        advanceUntilIdle()
        viewModel.selectPlace(destinationPlace())
        advanceUntilIdle()

        viewModel.requestRoute()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.routePreview).isNull()
        assertThat(viewModel.uiState.value.routeError).contains("timed out")
    }

    @Test
    fun `submitting a review refreshes place detail immediately`() = runTest(dispatcher) {
        val internalReviewRepository = FakeInternalReviewRepository()
        val viewModel = createViewModel(internalReviewRepository = internalReviewRepository)

        val result = FakeExploreOrchestrator().explore(
            ExploreQuery(category = ExploreCategory.DELICIOUS, userLocation = Coordinate(0.0, 0.0))
        ).results.single()
        viewModel.openPlaceDetail(result)
        advanceUntilIdle()

        viewModel.startLeaveReview()
        advanceUntilIdle()
        viewModel.updateReviewRating(5)
        viewModel.updateReviewText("Quiet stop with quick parking and friendly staff.")
        viewModel.toggleReviewTag(PlaceReviewTag.QUIET)
        viewModel.submitReview()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.placeDetail.internalReviews).hasSize(1)
        assertThat(viewModel.uiState.value.placeDetail.internalRatingSummary).contains("1 internal review")
        assertThat(internalReviewRepository.reviews.value.single().reviewText).contains("Quiet stop")
    }

    @Test
    fun `saving and unsaving a place refreshes detail and saved surfaces immediately`() = runTest(dispatcher) {
        val savedPlaceRepository = FakeSavedPlaceRepository()
        val viewModel = createViewModel(savedPlaceRepository = savedPlaceRepository)

        val result = FakeExploreOrchestrator().explore(
            ExploreQuery(category = ExploreCategory.DELICIOUS, userLocation = Coordinate(0.0, 0.0))
        ).results.single()

        viewModel.openPlaceDetail(result)
        advanceUntilIdle()
        viewModel.toggleSavedPlaceDetail()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.placeDetail.isSaved).isTrue()
        assertThat(viewModel.uiState.value.savedPlaces).hasSize(1)
        assertThat(savedPlaceRepository.savedPlacesFlow.value.single().canonicalPlaceId).isEqualTo(result.candidate.id)

        viewModel.toggleSavedPlaceDetail()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.placeDetail.isSaved).isFalse()
        assertThat(viewModel.uiState.value.savedPlaces).isEmpty()
    }

    @Test
    fun `voice capture requires microphone permission and surfaces a helpful message`() = runTest(dispatcher) {
        val voiceManager = FakeVoiceAssistantManager()
        val viewModel = createViewModel(voiceAssistantManager = voiceManager)

        viewModel.startVoiceCapture()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.voiceAssistant.lastActionMessage).contains("Grant microphone permission")
        assertThat(voiceManager.startListeningCalls).isEqualTo(0)
    }

    @Test
    fun `sync permission state seeds startup permissions without extra prompts`() = runTest(dispatcher) {
        val locationFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 4)
        val viewModel = createViewModel(locationFlow = locationFlow)

        viewModel.syncPermissionState(
            hasLocationPermission = true,
            hasMicrophonePermission = true,
            hasNotificationPermission = false
        )
        locationFlow.emit(sample(0.0, 0.0, 0f))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasLocationPermission).isTrue()
        assertThat(viewModel.uiState.value.voiceAssistant.hasMicrophonePermission).isTrue()
        assertThat(viewModel.uiState.value.hasNotificationPermission).isFalse()
        assertThat(viewModel.uiState.value.currentLocation?.coordinate).isEqualTo(Coordinate(0.0, 0.0))
    }

    @Test
    fun `voice capture errors and transcripts feed the voice assistant ui state`() = runTest(dispatcher) {
        val voiceManager = FakeVoiceAssistantManager()
        val viewModel = createViewModel(voiceAssistantManager = voiceManager)
        viewModel.onMicrophonePermissionResult(true)
        advanceUntilIdle()

        voiceManager.captureStateFlow.value = SpeechCaptureState.Error("Speech recognition hit a network problem.")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.voiceAssistant.lastActionMessage).contains("network problem")

        voiceManager.captureStateFlow.value = SpeechCaptureState.TranscriptAvailable("Simon, report traffic")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.voiceAssistant.draftTranscript).isEqualTo("Simon, report traffic")
    }

    @Test
    fun `voice dispatch drives explore and review state updates`() = runTest(dispatcher) {
        val reviewDraftRepository = FakeReviewDraftRepository()
        val voiceManager = FakeVoiceAssistantManager(
            resultProvider = { transcript, _ ->
                when (transcript) {
                    "quiet places" -> VoiceDispatchResult.Explore(ExploreCategory.QUIET, "Opening quiet suggestions nearby.")
                    "leave a review for this place" -> VoiceDispatchResult.ReviewStarted("Ready to draft a review.")
                    else -> VoiceDispatchResult.NoOp("Unhandled")
                }
            }
        )
        val viewModel = createViewModel(
            reviewDraftRepository = reviewDraftRepository,
            voiceAssistantManager = voiceManager
        )

        viewModel.updateVoiceTranscript("quiet places")
        viewModel.submitVoiceTranscript()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.explore.selectedCategory).isEqualTo(ExploreCategory.QUIET)
        assertThat(viewModel.uiState.value.voiceAssistant.lastActionMessage).contains("quiet suggestions")

        reviewDraftRepository.startDraft(ReviewDraft(id = "draft-1", place = destinationPlace()))
        advanceUntilIdle()

        viewModel.updateVoiceTranscript("leave a review for this place")
        viewModel.submitVoiceTranscript()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.voiceAssistant.activeReviewDraft?.id).isEqualTo("draft-1")
        assertThat(viewModel.uiState.value.voiceAssistant.lastActionMessage).contains("Ready to draft a review")
    }

    private fun createViewModel(
        locationFlow: Flow<LocationSample> = MutableSharedFlow(extraBufferCapacity = 4),
        serviceController: NavigationForegroundServiceController = FakeNavigationForegroundServiceController(),
        voicePromptManager: VoicePromptManager = mockk(relaxed = true),
        route: Route = routeWithSingleManeuver(),
        geocodingRepository: GeocodingRepository = FakeGeocodingRepository(),
        recentDestinationRepository: RecentDestinationRepository = FakeRecentDestinationRepository(),
        navigationSessionOrchestrator: FakeNavigationSessionOrchestrator = FakeNavigationSessionOrchestrator(),
        exploreOrchestrator: ExploreOrchestrator = FakeExploreOrchestrator(),
        internalReviewRepository: FakeInternalReviewRepository = FakeInternalReviewRepository(),
        savedPlaceRepository: FakeSavedPlaceRepository = FakeSavedPlaceRepository(),
        visitHistoryRepository: VisitHistoryRepository = FakeVisitHistoryRepository(),
        crowdReportRepository: CrowdReportRepository = FakeCrowdReportRepository(),
        reviewDraftRepository: ReviewDraftRepository = FakeReviewDraftRepository(),
        voiceAssistantManager: VoiceAssistantManager = FakeVoiceAssistantManager()
    ): AppViewModel {
        val routingRepository = mockk<RoutingRepository>()
        val fusedLocationRepository = mockk<FusedLocationRepository>()
        val demoLocationRepository = mockk<DemoLocationRepository>()
        val settingsRepository = FakeSettingsRepository()
        val engine = SimonSaysEngine(TurnDetector(), PromptFactory())
        val navigationUseCase = ObserveNavigationSessionUseCase(
            routingRepository = routingRepository,
            simonSaysEngine = engine
        )

        every { fusedLocationRepository.locationUpdates() } returns locationFlow
        every { demoLocationRepository.locationUpdates() } returns locationFlow
        coEvery { routingRepository.calculateRoute(any(), any()) } returns RepositoryResult.Success(route, FetchSource.NETWORK)

        return AppViewModel(
            geocodingRepository = geocodingRepository,
            recentDestinationRepository = recentDestinationRepository,
            routingRepository = routingRepository,
            settingsRepository = settingsRepository,
            fusedLocationRepository = fusedLocationRepository,
            demoLocationRepository = demoLocationRepository,
            navigationUseCase = navigationUseCase,
            simonSaysEngine = engine,
            voicePromptManager = voicePromptManager,
            navigationForegroundServiceController = serviceController,
            navigationSessionOrchestrator = navigationSessionOrchestrator,
            exploreOrchestrator = exploreOrchestrator,
            placeDetailRepository = FakePlaceDetailRepository(internalReviewRepository, savedPlaceRepository),
            internalReviewRepository = internalReviewRepository,
            savedPlaceRepository = savedPlaceRepository,
            visitHistoryRepository = visitHistoryRepository,
            crowdReportRepository = crowdReportRepository,
            reviewDraftRepository = reviewDraftRepository,
            voiceAssistantManager = voiceAssistantManager
        )
    }

    private fun routeWithSingleManeuver() = Route(
        geometry = listOf(
            Coordinate(0.0, 0.0),
            Coordinate(0.0, 0.0001),
            Coordinate(0.0001, 0.0001)
        ),
        maneuvers = listOf(
            RouteManeuver(
                id = "one",
                coordinate = Coordinate(0.0, 0.0001),
                instruction = "Turn right onto Pond Road",
                turnType = TurnType.RIGHT,
                roadName = "Pond Road",
                distanceFromPreviousMeters = 20.0,
                distanceToNextMeters = 0.0,
                authorization = ManeuverAuthorization.REQUIRED_SIMON_SAYS,
                headingBefore = 0.0,
                headingAfter = 90.0
            )
        ),
        totalDistanceMeters = 100.0,
        totalDurationSeconds = 80.0,
        etaEpochSeconds = 1L
    )

    private fun destinationPlace(
        id: String = "dest",
        name: String = "Destination"
    ) = PlaceResult(
        id = id,
        name = name,
        fullAddress = "$name Address",
        coordinate = Coordinate(0.0001, 0.0001)
    )

    private fun sample(lat: Double, lon: Double, bearing: Float) = LocationSample(
        coordinate = Coordinate(lat, lon),
        accuracyMeters = 5f,
        bearing = bearing,
        speedMetersPerSecond = 4f,
        timestampMillis = 0L
    )

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(SettingsModel())

        override suspend fun update(transform: (SettingsModel) -> SettingsModel) {
            settings.value = transform(settings.value)
        }
    }

    private class FakeGeocodingRepository(
        private val responses: Map<String, RepositoryResult<List<PlaceResult>>> = emptyMap()
    ) : GeocodingRepository {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): RepositoryResult<List<PlaceResult>> {
            queries += query
            return responses[query] ?: RepositoryResult.Success(emptyList(), FetchSource.NETWORK)
        }
    }

    private class FakeRecentDestinationRepository : RecentDestinationRepository {
        private val recent = MutableStateFlow<List<PlaceResult>>(emptyList())

        override val recentDestinations: Flow<List<PlaceResult>> = recent

        override suspend fun save(place: PlaceResult) {
            recent.value = listOf(place) + recent.value.filterNot { it.id == place.id }
        }

        override suspend fun remove(placeId: String) {
            recent.value = recent.value.filterNot { it.id == placeId }
        }

        override suspend fun clear() {
            recent.value = emptyList()
        }
    }


    private class FakeExploreOrchestrator : ExploreOrchestrator {
        override suspend fun explore(query: ExploreQuery): ExploreResponse = ExploreResponse(
            results = listOf(
                ExploreResult(
                    candidate = com.simonsaysgps.domain.model.explore.ExploreCandidate(
                        id = "explore",
                        name = "Explore Place",
                        typeLabel = "Cafe",
                        address = "1 Explore Way",
                        coordinate = query.userLocation,
                        facets = setOf(com.simonsaysgps.domain.model.explore.ExploreFacet.FOOD),
                        openNow = true
                    ),
                    score = 0.9,
                    confidence = 0.8f,
                    distanceMeters = 10.0,
                    reasons = emptyList(),
                    debugBreakdown = mapOf("category" to 1.0)
                )
            ),
            providerStatuses = listOf(ExploreProviderStatus("place-data", true, "OK")),
            autoPicked = query.category == ExploreCategory.OPEN_NOW,
            totalCandidatesConsidered = 1
        )
    }

    private class FakeNavigationSessionOrchestrator(
        private val restoredSession: com.simonsaysgps.domain.model.NavigationSessionState? = null
    ) : NavigationSessionOrchestrator {
        var restoreCalls: Int = 0
        val syncedStates = mutableListOf<com.simonsaysgps.domain.model.NavigationSessionState>()
        val ensuredForegroundReasons = mutableListOf<String>()

        override suspend fun restoreSession(): com.simonsaysgps.domain.model.NavigationSessionState? {
            restoreCalls += 1
            return restoredSession
        }

        override suspend fun syncSession(state: com.simonsaysgps.domain.model.NavigationSessionState) {
            syncedStates += state
        }

        override fun ensureForegroundService(reason: String) {
            ensuredForegroundReasons += reason
        }
    }

    private class FakeNavigationForegroundServiceController : NavigationForegroundServiceController {
        val startReasons = mutableListOf<String>()
        val stopReasons = mutableListOf<String>()
        private var running = false

        override fun start(reason: String) {
            if (running) return
            running = true
            startReasons += reason
        }

        override fun stop(reason: String) {
            if (!running) return
            running = false
            stopReasons += reason
        }
    }


    private class FakeVisitHistoryRepository : VisitHistoryRepository {
        override val visitHistory = MutableStateFlow<List<VisitHistoryEntry>>(emptyList())
        override suspend fun record(entry: VisitHistoryEntry) { visitHistory.value = listOf(entry) + visitHistory.value }
        override suspend fun remove(placeId: String) { visitHistory.value = visitHistory.value.filterNot { it.placeId == placeId } }
        override suspend fun clear() { visitHistory.value = emptyList() }
    }

    private class FakePlaceDetailRepository(
        private val internalReviewRepository: FakeInternalReviewRepository,
        private val savedPlaceRepository: FakeSavedPlaceRepository
    ) : PlaceDetailRepository {
        override fun observePlaceDetail(seed: ExploreResult): Flow<PlaceDetailRecord> =
            combine(internalReviewRepository.reviews, savedPlaceRepository.savedPlacesFlow) { reviews, savedPlaces ->
                PlaceDetailRecord(
                    canonicalPlaceId = seed.candidate.id,
                    name = seed.candidate.name,
                    typeLabel = seed.candidate.typeLabel,
                    address = seed.candidate.address,
                    coordinate = seed.candidate.coordinate,
                    openNow = seed.candidate.openNow,
                    distanceMeters = seed.distanceMeters,
                    whyChosen = seed.primaryWhyChosen,
                    savedPlace = savedPlaces.firstOrNull { it.canonicalPlaceId == seed.candidate.id },
                    internalReviews = reviews.filter { it.canonicalPlaceId == seed.candidate.id },
                    internalAggregate = com.simonsaysgps.domain.model.explore.InternalReviewAggregateCalculator.calculate(
                        reviews.filter { it.canonicalPlaceId == seed.candidate.id }
                    )
                )
            }
    }

    private class FakeInternalReviewRepository : InternalReviewRepository {
        val reviews = MutableStateFlow<List<InternalPlaceReview>>(emptyList())
        override val localAuthorDisplayName: Flow<String> = flowOf("Local driver")
        override fun observeReviews(canonicalPlaceId: String): Flow<List<InternalPlaceReview>> = reviews
        override fun observeOwnReview(canonicalPlaceId: String): Flow<InternalPlaceReview?> = reviews.map { list ->
            list.firstOrNull { it.canonicalPlaceId == canonicalPlaceId }
        }
        override suspend fun upsert(review: InternalPlaceReview) {
            reviews.value = listOf(review) + reviews.value.filterNot { it.internalReviewId == review.internalReviewId }
        }
        override suspend fun remove(reviewId: String) {
            reviews.value = reviews.value.filterNot { it.internalReviewId == reviewId }
        }
    }

    private class FakeSavedPlaceRepository : SavedPlaceRepository {
        val savedPlacesFlow = MutableStateFlow<List<SavedPlaceRecord>>(emptyList())
        override val savedPlaces: Flow<List<SavedPlaceRecord>> = savedPlacesFlow
        override fun observeSavedPlace(canonicalPlaceId: String): Flow<SavedPlaceRecord?> = savedPlacesFlow.map { saved ->
            saved.firstOrNull { it.canonicalPlaceId == canonicalPlaceId }
        }
        override suspend fun upsert(place: SavedPlaceRecord) {
            savedPlacesFlow.value = listOf(place) + savedPlacesFlow.value.filterNot { it.canonicalPlaceId == place.canonicalPlaceId }
        }
        override suspend fun remove(canonicalPlaceId: String) {
            savedPlacesFlow.value = savedPlacesFlow.value.filterNot { it.canonicalPlaceId == canonicalPlaceId }
        }
    }

    private class FakeCrowdReportRepository : CrowdReportRepository {
        override val reports = MutableStateFlow<List<CrowdReport>>(emptyList())
        override val pendingReport = MutableStateFlow<CrowdReport?>(null)
        override suspend fun stage(report: CrowdReport) { pendingReport.value = report }
        override suspend fun confirmPending() {
            pendingReport.value?.let { reports.value = listOf(it.copy(userConfirmed = true)) + reports.value }
            pendingReport.value = null
        }
        override suspend fun dismissPending(reason: String?) { pendingReport.value = null }
    }

    private class FakeReviewDraftRepository : ReviewDraftRepository {
        override val drafts = MutableStateFlow<List<ReviewDraft>>(emptyList())
        override val activeDraft = MutableStateFlow<ReviewDraft?>(null)
        override suspend fun startDraft(draft: ReviewDraft) {
            activeDraft.value = draft
            drafts.value = listOf(draft)
        }
        override suspend fun updateRawTranscript(transcript: String) {
            activeDraft.value = activeDraft.value?.copy(rawTranscript = transcript)
            activeDraft.value?.let { active -> drafts.value = drafts.value.map { if (it.id == active.id) active else it } }
        }
        override suspend fun applyCleanupSuggestion(option: ReviewCleanupOption, suggestion: String?) {
            activeDraft.value = activeDraft.value?.copy(selectedCleanupOption = option, cleanedSuggestion = suggestion)
            activeDraft.value?.let { active -> drafts.value = drafts.value.map { if (it.id == active.id) active else it } }
        }
        override suspend fun approveFinalText(text: String) {
            activeDraft.value = activeDraft.value?.copy(finalApprovedText = text)
            activeDraft.value?.let { active -> drafts.value = drafts.value.map { if (it.id == active.id) active else it } }
        }
        override suspend fun clearDraft() {
            activeDraft.value = null
        }
    }

    private class FakeVoiceAssistantManager(
        private val resultProvider: suspend (String, VoiceContext) -> VoiceDispatchResult = { transcript, _ ->
            VoiceDispatchResult.NoOp("Handled: $transcript")
        }
    ) : VoiceAssistantManager {
        val captureStateFlow = MutableStateFlow<SpeechCaptureState>(SpeechCaptureState.Idle)
        var startListeningCalls: Int = 0
        override val captureState: StateFlow<SpeechCaptureState> = captureStateFlow
        override suspend fun handleTranscript(transcript: String, context: VoiceContext): VoiceDispatchResult =
            resultProvider(transcript, context)
        override fun startListening() {
            startListeningCalls += 1
        }
        override fun stopListening() = Unit
    }

}
