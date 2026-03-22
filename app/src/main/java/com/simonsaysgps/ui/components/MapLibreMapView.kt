package com.simonsaysgps.ui.components

import android.graphics.Color
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.simonsaysgps.BuildConfig
import com.simonsaysgps.domain.model.Coordinate
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapLibre.getInstance(context)
            MapView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri(BuildConfig.MAP_STYLE_URL))
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
                        style.addLayer(LineLayer("route-layer", "route-source").withProperties(lineColor(Color.parseColor("#5B7CFA")), lineWidth(5f)))
                    } else {
                        source.setGeoJson(collection)
                    }
                    val focus = selectedLocation ?: currentLocation
                    focus?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.5)) }
                }
            }
        }
    )
}
