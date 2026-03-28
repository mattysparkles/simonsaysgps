package com.simonsaysgps.ui.components

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.simonsaysgps.config.MapStyleConfiguration
import com.simonsaysgps.domain.model.Coordinate
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    currentLocation: Coordinate?,
    selectedLocation: Coordinate?,
    routeGeometry: List<Coordinate>,
    followCurrentLocation: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolvedStyle = remember { MapStyleConfiguration.resolve() }
    val lastStaticCameraKey = remember { mutableStateOf<String?>(null) }
    val centeredOnCurrentLocation = remember { mutableStateOf(false) }

    DisposableEffect(resolvedStyle.warningMessage) {
        resolvedStyle.warningMessage?.let { warning -> Log.w(TAG, warning) }
        onDispose { }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromUri(resolvedStyle.styleUrl))
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mapView.onDestroy()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = {
                it.getMapAsync { map ->
                    map.getStyle { style ->
                        val features = mutableListOf<Feature>()
                        if (routeGeometry.isNotEmpty()) {
                            features += Feature.fromGeometry(
                                LineString.fromLngLats(routeGeometry.map { point ->
                                    Point.fromLngLat(point.longitude, point.latitude)
                                })
                            )
                        }
                        currentLocation?.let { location ->
                            features += Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))
                        }
                        selectedLocation?.let { location ->
                            features += Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))
                        }
                        val collection = FeatureCollection.fromFeatures(features)
                        val source = style.getSourceAs<GeoJsonSource>("route-source")
                        if (source == null) {
                            style.addSource(GeoJsonSource("route-source", collection))
                            style.addLayer(
                                LineLayer("route-layer", "route-source").withProperties(
                                    lineColor(Color.parseColor("#2F80FF")),
                                    lineWidth(5f)
                                )
                            )
                        } else {
                            source.setGeoJson(collection)
                        }

                        if (routeGeometry.size > 1 && !followCurrentLocation) {
                            val key = "route:${routeGeometry.first()}-${routeGeometry.last()}-${routeGeometry.size}"
                            if (lastStaticCameraKey.value != key) {
                                val bounds = LatLngBounds.Builder().apply {
                                    routeGeometry.forEach { include(LatLng(it.latitude, it.longitude)) }
                                }.build()
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 96))
                                lastStaticCameraKey.value = key
                                centeredOnCurrentLocation.value = true
                            }
                        } else if (selectedLocation != null && !followCurrentLocation) {
                            val key = "selected:${selectedLocation.latitude},${selectedLocation.longitude}"
                            if (lastStaticCameraKey.value != key) {
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(selectedLocation.latitude, selectedLocation.longitude), 13.2))
                                lastStaticCameraKey.value = key
                                centeredOnCurrentLocation.value = true
                            }
                        } else if (currentLocation != null) {
                            if (followCurrentLocation) {
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLocation.latitude, currentLocation.longitude), 15.2))
                            } else if (!centeredOnCurrentLocation.value) {
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLocation.latitude, currentLocation.longitude), 12.8))
                                centeredOnCurrentLocation.value = true
                            }
                        }
                    }
                }
            }
        )

        if (resolvedStyle.usesFallback) {
            Text(
                text = resolvedStyle.warningMessage ?: "Using fallback map style.",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .background(ComposeColor.Black.copy(alpha = 0.70f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = ComposeColor.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private const val TAG = "MapLibreMapView"
