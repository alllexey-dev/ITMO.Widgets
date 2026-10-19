package dev.alllexey.itmowidgets.feature.map.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapStyle
import dev.alllexey.itmowidgets.feature.map.presentation.MapScene
import dev.alllexey.itmowidgets.shared.feature.map.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The native plan (MapLibre Android) of [scene]: the style is loaded once per building and theme (the dark plan in a
 * dark app theme), the floor switches by the shared layer filters, the route and the selection replace their GeoJSON,
 * and a tap reports the room under it. The style and the icons are built off the main thread; a spinner shows until
 * the style has loaded.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun IndoorMapView(scene: MapScene, onRoomTap: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var sprites by remember { mutableStateOf<Map<String, Bitmap>?>(null) }
    var framed by remember { mutableStateOf<String?>(null) }
    val tap by rememberUpdatedState(onRoomTap)
    val dark = ItmoTheme.colorScheme.background.luminance() < HALF
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(Unit) { sprites = loadSprites() }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { loaded ->
            loaded.setMinZoomPreference(MIN_ZOOM)
            loaded.setMaxZoomPreference(MAX_ZOOM)
            loaded.uiSettings.isRotateGesturesEnabled = true
            // The plan's source is in the licences, not on the map (docs/features/map.md).
            loaded.uiSettings.isLogoEnabled = false
            loaded.uiSettings.isAttributionEnabled = false
            // The plan turns with the building's bearing; a compass would sit under the search, and itmo-map-web has none.
            loaded.uiSettings.isCompassEnabled = false
            loaded.addOnMapClickListener { point ->
                val screen = loaded.projection.toScreenLocation(point)
                val feature = loaded.queryRenderedFeatures(screen, *CampusMapStyle.TAP_LAYERS.toTypedArray()).firstOrNull()
                tap(feature?.getStringProperty("id")?.toLongOrNull())
                true
            }
            map = loaded
        }
    }
    LaunchedEffect(map, sprites, scene.tilesPath, dark) {
        val loaded = map ?: return@LaunchedEffect
        val icons = sprites ?: return@LaunchedEffect
        style = null
        // A theme switch keeps the camera; only another building is framed anew, before the style is built off the
        // main thread, so the room or step the focus and frame effects below move to is not framed over.
        if (framed != scene.tilesPath) {
            framed = scene.tilesPath
            val (west, south, east, north) = scene.bounds
            val bounds = LatLngBounds.from(north, east, south, west)
            loaded.setLatLngBoundsForCameraTarget(bounds)
            loaded.getCameraForLatLngBounds(bounds, IntArray(4) { PADDING }, scene.bearing, 0.0)?.let { loaded.cameraPosition = it }
        }
        val json = withContext(Dispatchers.Default) { CampusMapStyle.json(scene, dark) }
        val builder = icons.entries.fold(Style.Builder().fromJson(json)) { b, (name, icon) -> b.withImage(name, icon) }
        loaded.setStyle(builder) { style = it }
    }
    LaunchedEffect(style, scene.level, scene.route, scene.selection) {
        val current = style ?: return@LaunchedEffect
        CampusMapStyle.filters(scene.level, scene.selectedRoomId).forEach { (id, filter) ->
            val expression = Expression.Converter.convert(filter)
            when (val layer = current.getLayer(id)) {
                is FillLayer -> layer.setFilter(expression)
                is LineLayer -> layer.setFilter(expression)
                is CircleLayer -> layer.setFilter(expression)
                is SymbolLayer -> layer.setFilter(expression)
                else -> Unit
            }
        }
        current.getSourceAs<GeoJsonSource>(CampusMapStyle.ROUTE_SOURCE)?.setGeoJson(scene.route)
        current.getSourceAs<GeoJsonSource>(CampusMapStyle.SELECTION_SOURCE)?.setGeoJson(scene.selection)
    }
    LaunchedEffect(map, scene.frame) {
        val loaded = map ?: return@LaunchedEffect
        val (west, south, east, north) = scene.frame ?: return@LaunchedEffect
        val frame = LatLngBounds.from(north, east, south, west)
        loaded.getCameraForLatLngBounds(frame, IntArray(4) { FRAME_PADDING }, scene.bearing, 0.0)?.let { camera ->
            val zoomed = CameraPosition.Builder(camera).zoom(minOf(camera.zoom, MAX_FRAME_ZOOM)).build()
            loaded.animateCamera(CameraUpdateFactory.newCameraPosition(zoomed), ANIMATION_MS)
        }
    }
    LaunchedEffect(map, scene.focus) {
        val loaded = map ?: return@LaunchedEffect
        if (scene.frame != null) return@LaunchedEffect
        val focus = scene.focus ?: return@LaunchedEffect
        val camera = CameraPosition.Builder()
            .target(LatLng(focus.latitude, focus.longitude))
            .zoom(maxOf(loaded.cameraPosition.zoom, FOCUS_ZOOM))
            .bearing(scene.bearing)
            .build()
        loaded.animateCamera(CameraUpdateFactory.newCameraPosition(camera), ANIMATION_MS)
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (style == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

/**
 * The campus app's icons from the module's resources, at its pixel ratio of 3 (the bitmap's density says it); 36
 * PNGs of 72 px, read and decoded off the main thread.
 */
private suspend fun loadSprites(): Map<String, Bitmap> = withContext(Dispatchers.Default) {
    CampusMapStyle.SPRITES.associateWith { name ->
        val bytes = Res.readBytes("files/map_sprites/$name.png")
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).apply { density = DisplayMetrics.DENSITY_DEFAULT * CampusMapStyle.SPRITE_PIXEL_RATIO }
    }
}

private operator fun List<Double>.component1() = this[0]
private operator fun List<Double>.component2() = this[1]
private operator fun List<Double>.component3() = this[2]
private operator fun List<Double>.component4() = this[3]

private const val MIN_ZOOM = 16.0
private const val MAX_ZOOM = 22.0
private const val FOCUS_ZOOM = 19.5
private const val PADDING = 96
private const val FRAME_PADDING = 220
private const val MAX_FRAME_ZOOM = 20.0
private const val ANIMATION_MS = 400
private const val HALF = 0.5f
