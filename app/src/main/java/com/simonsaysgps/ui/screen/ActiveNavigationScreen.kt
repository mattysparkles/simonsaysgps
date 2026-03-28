package com.simonsaysgps.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simonsaysgps.config.ReleaseSurface
import com.simonsaysgps.domain.model.NavigationSessionState
import com.simonsaysgps.domain.util.DistanceFormatter
import com.simonsaysgps.ui.components.BrandTopBar
import com.simonsaysgps.ui.components.InfoCard
import com.simonsaysgps.ui.components.MapLibreMapView
import com.simonsaysgps.ui.model.ActiveNavigationBannerUiMapper
import com.simonsaysgps.ui.model.ActiveNavigationBannerUiModel
import com.simonsaysgps.ui.model.LaneGuidanceUiModel
import com.simonsaysgps.ui.model.LaneUiModel
import com.simonsaysgps.ui.test.UiTestTags
import com.simonsaysgps.ui.theme.Coral
import com.simonsaysgps.ui.theme.ElectricBlue
import com.simonsaysgps.ui.theme.NightSky
import com.simonsaysgps.ui.theme.Sun
import com.simonsaysgps.ui.viewmodel.AppUiState
import com.simonsaysgps.ui.viewmodel.AppViewModel

@Composable
fun ActiveNavigationScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ActiveNavigationScreenContent(
        state = state,
        onBack = onBack,
        debugOverlay = { DebugOverlay(viewModel = viewModel) }
    )
}

@Composable
fun ActiveNavigationScreenContent(
    state: AppUiState,
    onBack: () -> Unit,
    mapContent: @Composable (Modifier) -> Unit = { modifier ->
        MapLibreMapView(
            modifier = modifier,
            currentLocation = state.currentLocation?.coordinate,
            selectedLocation = state.selectedPlace?.coordinate,
            routeGeometry = state.navigationState.route?.geometry.orEmpty(),
            followCurrentLocation = true
        )
    },
    debugOverlay: @Composable () -> Unit = { DebugOverlayContent(state.navigationState, state.currentLocation?.coordinate?.latitude, state.currentLocation?.coordinate?.longitude) }
) {
    val navigation = state.navigationState
    val route = navigation.route
    val banner = ActiveNavigationBannerUiMapper.map(navigation, state.settings)
    Scaffold(
        modifier = Modifier.testTag(UiTestTags.ACTIVE_NAVIGATION_SCREEN),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrandTopBar(
                title = if (banner is ActiveNavigationBannerUiModel.Arrived) "Trip complete" else "Simon is driving shotgun",
                subtitle = if (banner is ActiveNavigationBannerUiModel.Arrived) {
                    "You made it. Time for the post-game commentary."
                } else {
                    "Bright cues, quick reads, and just enough personality to keep the drive awake."
                },
                badge = if (banner is ActiveNavigationBannerUiModel.Arrived) "Arrived" else "Live route"
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NightSky)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        mapContent(Modifier.fillMaxSize())
                        ActiveNavigationBannerCard(
                            banner = banner,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    InfoCard(
                        title = "Distance to next",
                        value = navigation.distanceToNextManeuverMeters?.let { DistanceFormatter.format(it, state.settings.distanceUnit) } ?: "Done",
                        badgeText = "Next",
                        badgeColor = Sun,
                        modifier = Modifier.weight(1f)
                    )
                    InfoCard(
                        title = "ETA",
                        value = route?.etaEpochSeconds?.let {
                            java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString()
                        } ?: "--",
                        badgeText = "Clock",
                        badgeColor = ElectricBlue.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f)
                    )
                }
                InfoCard(
                    title = "Current road",
                    value = navigation.currentRoad ?: navigation.upcomingManeuver?.roadName ?: "Following route",
                    badgeText = "Live"
                )
                navigation.spokenPrompt?.let {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Trip mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (banner is ActiveNavigationBannerUiModel.Arrived) "Arrival moment" else "Simon is actively guiding",
                                style = MaterialTheme.typography.titleMedium,
                                color = NightSky
                            )
                        }
                        Spacer(modifier = Modifier)
                        Button(onClick = onBack) {
                            Text(if (banner is ActiveNavigationBannerUiModel.Arrived) "Finish trip" else "End trip")
                        }
                    }
                }
                if (state.settings.debugMode) {
                    debugOverlay()
                }
            }
        }
    }
}

@Composable
private fun ActiveNavigationBannerCard(
    banner: ActiveNavigationBannerUiModel,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        when (banner) {
            is ActiveNavigationBannerUiModel.Arrived -> ArrivalBannerContent(banner)
            is ActiveNavigationBannerUiModel.EnRoute -> EnRouteBannerContent(banner)
        }
    }
}

@Composable
private fun ArrivalBannerContent(banner: ActiveNavigationBannerUiModel.Arrived) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = banner.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NightSky)
        Text(text = banner.message, style = MaterialTheme.typography.headlineSmall, color = NightSky)
        Text(text = banner.detail, style = MaterialTheme.typography.bodyMedium, color = NightSky.copy(alpha = 0.78f))
    }
}

@Composable
private fun EnRouteBannerContent(banner: ActiveNavigationBannerUiModel.EnRoute) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = banner.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Badge(text = banner.authorization.label, color = banner.authorization.badgeColor)
        }
        Text(text = banner.primaryInstruction, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        banner.secondaryInstruction?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailPill(text = banner.distanceLabel)
            banner.stepLabel?.let { DetailPill(text = it) }
            banner.turnTypeLabel?.let { DetailPill(text = it) }
        }
        Text(
            text = "Road: ${banner.roadLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = banner.authorization.badgeColor.copy(alpha = 0.25f)
        ) {
            Text(
                text = banner.authorization.message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LaneGuidanceSection(banner.laneGuidance, ReleaseSurface.fromBuildConfig().showLaneGuidancePlaceholder)
    }
}

@Composable
private fun LaneGuidanceSection(model: LaneGuidanceUiModel, showPlaceholder: Boolean) {
    when (model) {
        LaneGuidanceUiModel.Hidden -> Unit
        is LaneGuidanceUiModel.Placeholder -> if (showPlaceholder) Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = model.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(text = model.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Unit
        is LaneGuidanceUiModel.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = model.hint, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                model.lanes.forEach { lane ->
                    LaneIndicatorChip(lane)
                }
            }
        }
    }
}

@Composable
private fun LaneIndicatorChip(lane: LaneUiModel) {
    val background = when {
        lane.emphasized -> MaterialTheme.colorScheme.primaryContainer
        lane.subdued -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        lane.emphasized -> MaterialTheme.colorScheme.primary
        lane.subdued -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(background, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = lane.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (color == Coral) Color.White else NightSky
    )
}

@Composable
private fun DebugOverlay(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    DebugOverlayContent(
        navigation = state.navigationState,
        latitude = state.currentLocation?.coordinate?.latitude,
        longitude = state.currentLocation?.coordinate?.longitude
    )
}

@Composable
private fun DebugOverlayContent(
    navigation: NavigationSessionState,
    latitude: Double?,
    longitude: Double?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.DEBUG_OVERLAY)
            .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Text(
            text = """
GPS: $latitude, $longitude
Step index: ${navigation.activeManeuverIndex}
Distance: ${navigation.distanceToNextManeuverMeters}
Active step distance: ${navigation.debugInfo.activeStepDistanceMeters}
Simon auth: ${navigation.upcomingManeuver?.authorization}
Arrival: ${navigation.arrivalStatus}
Heading: ${navigation.headingDegrees}
Heading confidence: ${navigation.debugInfo.headingConfidence}
Corridor: ${navigation.debugInfo.routeCorridorStatus} (${navigation.debugInfo.routeCorridorDistanceMeters} / ${navigation.debugInfo.routeCorridorThresholdMeters})
Hysteresis: ${navigation.debugInfo.hysteresisState}
Reroute suppression: ${navigation.debugInfo.rerouteSuppressionReason}
Last transition: ${navigation.debugInfo.lastTransitionReason}
Off-route: ${navigation.offRoute}
Last reroute: ${navigation.lastRerouteReason}
            """.trimIndent(),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
