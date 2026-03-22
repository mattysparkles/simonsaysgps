package com.simonsaysgps.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.simonsaysgps.data.location.DemoLocationRepository
import com.simonsaysgps.data.location.FusedLocationRepository
import com.simonsaysgps.domain.engine.SimonSaysEngine
import com.simonsaysgps.domain.engine.TurnDetector
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
import com.simonsaysgps.domain.model.TurnType
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.RecentDestinationRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
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
        viewModel.startNavigation()
        viewModel.endNavigation("user ended navigation")
        advanceUntilIdle()

        assertThat(serviceController.startReasons).containsExactly("turn-by-turn navigation began")
        assertThat(serviceController.stopReasons).containsExactly("user ended navigation")
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
        assertThat(serviceController.startReasons).containsExactly("turn-by-turn navigation began")
        assertThat(serviceController.stopReasons).containsExactly("destination reached")
        verify(exactly = 1) { voicePromptManager.stop() }
        verify { voicePromptManager.speak(any()) }
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
            navigationForegroundServiceController = serviceController
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

    private fun createViewModel(
        locationFlow: Flow<LocationSample> = MutableSharedFlow(extraBufferCapacity = 4),
        serviceController: NavigationForegroundServiceController = FakeNavigationForegroundServiceController(),
        voicePromptManager: VoicePromptManager = mockk(relaxed = true),
        route: Route = routeWithSingleManeuver(),
        geocodingRepository: GeocodingRepository = FakeGeocodingRepository(),
        recentDestinationRepository: RecentDestinationRepository = FakeRecentDestinationRepository()
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
            navigationForegroundServiceController = serviceController
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
}
