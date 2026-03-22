package com.simonsaysgps.ui.components

import android.graphics.Color
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.simonsaysgps.config.MapStyleConfiguration
import com.simonsaysgps.domain.model.Coordinate
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
    routeGeometry: List<Coordinate>
) {
    val resolvedStyle = remember { MapStyleConfiguration.resolve() }

    DisposableEffect(resolvedStyle.warningMessage) {
        resolvedStyle.warningMessage?.let { warning ->
            Log.w(TAG, warning)
        }
        onDispose { }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapLibre.getInstance(context)
                MapView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    getMapAsync { map ->
                        map.setStyle(Style.Builder().fromUri(resolvedStyle.styleUrl))
                    }
                }
            },
            update = { mapView ->
                mapView.getMapAsync { map ->
                    map.getStyle { style ->
                        val features = mutableListOf<Feature>()
                        if (routeGeometry.isNotEmpty()) {
                            features += Feature.fromGeometry(
                                LineString.fromLngLats(routeGeometry.map { Point.fromLngLat(it.longitude, it.latitude) })
                            )
                        }
                        currentLocation?.let { features += Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                        selectedLocation?.let { features += Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                        val collection = FeatureCollection.fromFeatures(features)
                        val source = style.getSourceAs<GeoJsonSource>("route-source")
                        if (source == null) {
                            style.addSource(GeoJsonSource("route-source", collection))
                            style.addLayer(
                                LineLayer("route-layer", "route-source").withProperties(
                                    lineColor(Color.parseColor("#5B7CFA")),
                                    lineWidth(5f)
                                )
                            )
                        } else {
                            source.setGeoJson(collection)
                        }
                        val focus = selectedLocation ?: currentLocation
                        focus?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.5)) }
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
