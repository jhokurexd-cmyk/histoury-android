package com.histoury.app.ui.map

import com.histoury.app.theme.overlayShadow
import com.histoury.app.theme.styledElevation
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.BuildConfig
import com.histoury.app.map.MapConfig
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.isDarkTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * The one place the app creates a MapLibre map.
 *
 * Handles everything a screen shouldn't have to: the MapView's lifecycle
 * (start / resume / pause / stop / destroy / low memory), the light and dark
 * styles following the APP's theme, drawing the [scene], marker taps and
 * their callout, the "you are here" dot and button, a loading cover, a
 * load-error message with Retry, and the OpenStreetMap attribution.
 *
 * Camera control lives in [state] so screens can move, fit and follow.
 *
 * @param initialTarget where the camera starts (before any fit), in Google zoom levels via [initialGoogleZoom]
 * @param interactive false for small previews: no gestures, no callouts, no buttons
 * @param uiInsets the area (dp) covered by the screen's own overlays; attribution and buttons stay clear of it
 * @param textureMode use for maps inside scrolling content, so they clip and layer like any view
 * @param onMarkerClick return true when the screen handled the tap itself (no callout)
 */
@Composable
fun HistouryMapLibre(
    state: HistouryMapState,
    scene: MapScene,
    modifier: Modifier = Modifier,
    initialTarget: LatLng = MapConfig.INTRAMUROS_CENTER,
    initialGoogleZoom: Float = 15.5f,
    showUserLocation: Boolean = false,
    showMyLocationButton: Boolean = false,
    interactive: Boolean = true,
    uiInsets: MapInsets = MapInsets(),
    textureMode: Boolean = false,
    // MapLibre's own compass. Off where the screen draws its own (navigation).
    showCompass: Boolean = true,
    onMapClick: (() -> Unit)? = null,
    onMarkerClick: ((MapMarker) -> Boolean)? = null,
    onMapLoaded: (() -> Unit)? = null
) {

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val dark = isDarkTheme
    val styleUrl = MapConfig.styleUrl(dark)
    val accent = Primary.toArgb()

    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapLoaded by rememberUpdatedState(onMapLoaded)
    val currentInteractive by rememberUpdatedState(interactive)

    // One MapView for the life of this composable. MapLibre must be
    // initialised before the first MapView exists; getInstance is idempotent.
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(textureMode)
            .camera(
                CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(initialTarget.latitude, initialTarget.longitude))
                    .zoom(googleZoomToMapLibre(initialGoogleZoom))
                    .build()
            )
            .logoEnabled(false)
            .attributionEnabled(true)
            .compassEnabled(interactive && showCompass)
            .rotateGesturesEnabled(interactive)
            .scrollGesturesEnabled(interactive)
            .tiltGesturesEnabled(interactive)
            .zoomGesturesEnabled(interactive)
            .doubleTapGesturesEnabled(interactive)
            .quickZoomGesturesEnabled(interactive)
        MapView(context, options).apply { onCreate(null) }
    }

    // ---- lifecycle ---------------------------------------------------------
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        // addObserver replays ON_CREATE..current state, so a map composed
        // into an already-resumed screen starts and resumes at once.
        lifecycleOwner.lifecycle.addObserver(observer)

        val memoryCallbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                // Memory pressure while running, or the system about to kill
                // the app — not simply the app going to the background.
                val runningLow = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
                    level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                if (runningLow || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) mapView.onLowMemory()
            }
        }
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            state.detach()
            mapView.onDestroy()
        }
    }

    // ---- map setup (once) --------------------------------------------------
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { message ->
            state.loadError = message ?: "The map couldn't load."
        }
        mapView.getMapAsync { map ->
            // The callback can arrive after the screen was left and the map
            // destroyed (e.g. backing out quickly, or straight after AR).
            // Attaching a destroyed map would let later camera/style calls
            // hit a dead native map.
            if (mapView.isDestroyed) return@getMapAsync
            state.attach(map, mapView, density)
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.END
            map.addOnMapClickListener { point ->
                if (!currentInteractive) {
                    currentOnMapClick?.invoke()
                    return@addOnMapClickListener true
                }
                val marker = state.markerAt(point)
                if (marker != null) {
                    val handled = currentOnMarkerClick?.invoke(marker) == true
                    state.callout = if (handled || marker.title.isNullOrBlank()) null else marker
                } else {
                    state.callout = null
                    currentOnMapClick?.invoke()
                }
                true
            }
        }
    }

    // ---- style: light / dark, and reloads after an error ------------------
    val map = state.map
    val reloadCount = state.reloadCount
    LaunchedEffect(map, styleUrl, reloadCount) {
        val m = map ?: return@LaunchedEffect
        state.onStyleLoading()
        m.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            state.onStyleLoaded(style)
            currentOnMapLoaded?.invoke()
        }
    }

    // ---- what to draw ------------------------------------------------------
    val styleLoaded = state.isStyleLoaded
    LaunchedEffect(scene, styleLoaded) {
        if (styleLoaded) state.render(scene) else state.lastScene = scene
    }

    LaunchedEffect(showUserLocation, styleLoaded) {
        if (styleLoaded) state.setUserLocationVisible(context, showUserLocation, accent)
    }

    // Keeps MapLibre's own "i" attribution button clear of the screen's overlays.
    LaunchedEffect(map, uiInsets) {
        val m = map ?: return@LaunchedEffect
        val px = { dp: Float -> (dp * density).toInt() }
        m.uiSettings.setAttributionMargins(px(uiInsets.left + 8f), px(uiInsets.top), px(uiInsets.right + 8f), px(uiInsets.bottom + 6f))
        m.uiSettings.setCompassMargins(px(uiInsets.left + 8f), px(uiInsets.top + 8f), px(uiInsets.right + 8f), px(uiInsets.bottom))
    }

    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // The data licence (ODbL) and tile schema (OpenMapTiles) require a
        // visible credit; the "i" button lists the full links.
        Text(
            text = BuildConfig.HISTOURY_MAP_ATTRIBUTION,
            color = if (dark) Color(0xFFE6E1EA) else Color(0xFF2A2A2A),
            fontSize = if (interactive) 9.sp else 8.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = (uiInsets.left + 6f).dp, bottom = (uiInsets.bottom + 4f).dp)
                .widthIn(max = 260.dp)
                .background(
                    (if (dark) Color.Black else Color.White).copy(alpha = 0.65f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )

        if (interactive && showMyLocationButton && showUserLocation) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = (uiInsets.right + 12f).dp, bottom = (uiInsets.bottom + 28f).dp)
                    .size(44.dp)
                    .overlayShadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable { scope.launch { state.animateToUserLocation() } },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Show my location",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Tapped marker: its name (and note), like the Google info window.
        val callout = state.callout
        AnimatedVisibility(
            visible = interactive && callout != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (uiInsets.top + 12f).dp, start = 24.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                shadowElevation = styledElevation(6.dp),
                modifier = Modifier.clickable { state.callout = null }
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = callout?.title.orEmpty(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = callout?.subtitle
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        }

        // No style (offline with nothing cached, or the tile server down).
        val error = state.loadError
        if (error != null && !state.isStyleLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardSurface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "The map couldn't load",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (interactive) 15.sp else 12.sp
                    )
                    if (interactive) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Check your connection. Site information and saved trips still work offline.",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                        TextButton(onClick = { state.retry() }) {
                            Text("Retry", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
