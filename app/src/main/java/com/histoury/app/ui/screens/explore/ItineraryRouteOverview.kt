@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.explore

import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.navigateSafely
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.histoury.app.ui.components.CharacterCounter
import com.histoury.app.ui.components.FieldErrorText
import com.histoury.app.util.Validators
import com.histoury.app.ui.components.rememberThemedMapColorScheme
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.map.MapConfig
import com.histoury.app.map.TravelModes
import com.histoury.app.ui.map.ItineraryMapLibre
import com.histoury.app.data.itinerary.ItineraryPlanner
import com.histoury.app.data.model.ItineraryStatus
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.repository.TravelMode
import com.histoury.app.data.viewmodel.ItineraryUiState
import com.histoury.app.data.viewmodel.ItineraryViewModel
import com.histoury.app.data.viewmodel.StopPreviewMode
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CompletedSurfaceColor
import com.histoury.app.theme.CompletedContentColor
import com.histoury.app.theme.Danger
import com.histoury.app.theme.DangerSurface
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.ui.components.PrimaryButton

// Was three literals; now the shared palette, so a completed stop greys
// back against the night surface instead of glowing on it.

@Composable
fun ItineraryRouteOverviewScreen(
    viewModel: ItineraryViewModel,
    navController: NavHostController
) {

    val uiState by viewModel.uiState.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }

    val isEditingExisting = uiState.editingItineraryId != null

    val context = androidx.compose.ui.platform.LocalContext.current

    // Leaving the overview (Back gesture or the map's back arrow) used to
    // drop a freshly built — or edited — itinerary without a word. Ask first
    // whenever there's something unsaved.
    var showLeaveConfirm by remember { mutableStateOf(false) }

    fun requestClose() {
        // A route still being built goes back to the previous wizard step
        // with its selections intact, rather than out of the builder.
        if (viewModel.stepBack()) return
        if (viewModel.hasUnsavedChanges()) {
            showLeaveConfirm = true
        } else {
            viewModel.closeBuilder()
        }
    }

    fun saveWithConfirmation() {
        viewModel.saveItinerary {
            android.widget.Toast
                .makeText(context, "Itinerary saved", android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    // Registered after ExploreScreen's own BackHandler, so it takes
    // precedence while the overview is showing.
    androidx.activity.compose.BackHandler { requestClose() }

    val listState = rememberLazyListState()

    val haptics = LocalHapticFeedback.current

    // ----- drag-to-reorder -----
    //
    // The reorder is previewed locally and only committed to the ViewModel
    // on release. Reordering there discards the road-following path and
    // refetches it, so applying every intermediate position would fire a
    // burst of Routes API calls for orders nobody chose.
    var draggedKey by remember { mutableStateOf<String?>(null) }

    // Commute: which stop's "how to get here" directions are open.
    var commuteStepsFor by remember { mutableStateOf<Int?>(null) }

    // Commute steps for getting from here to the first stop.
    var showApproachSteps by remember { mutableStateOf(false) }
    // Where the dragged card's top edge is drawn, in viewport pixels. Held
    // in absolute terms rather than as an offset from the card's slot, so a
    // swap (which moves the slot) or an auto-scroll (which moves the whole
    // list) never needs compensating: the card simply stays under the
    // finger.
    var dragTopY by remember { mutableFloatStateOf(0f) }
    // Where the card was picked up, so auto-scroll can tell which way the
    // visitor is actually heading.
    var dragStartTopY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }
    // Lazy-list index of the first stop, so a layout that hasn't caught up
    // with the latest swap yet can be recognised and skipped.
    var firstStopLazyIndex by remember { mutableStateOf(0) }
    var reorderPreview by remember {
        mutableStateOf<List<ItineraryPlanner.ScheduledStop>?>(null)
    }

    val isReordering = draggedKey != null

    // After release, the card glides the last few pixels from under the
    // finger into its slot instead of snapping there.
    val settleScope = rememberCoroutineScope()
    val settleOffset = remember { Animatable(0f) }
    var settlingKey by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val autoScrollZonePx = with(density) { AUTO_SCROLL_ZONE.toPx() }
    val autoScrollMaxStepPx = with(density) { AUTO_SCROLL_MAX_STEP.toPx() }
    val dragSlopPx = with(density) { 8.dp.toPx() }

    val displayedSchedule = reorderPreview ?: uiState.schedule

    // The index is looked up from the key at the moment the drag starts.
    // The card's pointerInput outlives recompositions, so an index captured
    // when the card was first composed goes stale after a stop is removed or
    // reordered, and a stale index past the end of the list used to crash
    // the reorder's removeAt().
    fun beginDrag(key: String) {
        val index = uiState.schedule.indexOfFirst { timelineKey(it.stop) == key }
        if (index < 0) return
        settlingKey = null
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        reorderPreview = uiState.schedule
        draggedKey = key
        dragStartIndex = index
        dragCurrentIndex = index
        firstStopLazyIndex = info.index - index
        dragTopY = info.offset.toFloat()
        dragStartTopY = dragTopY
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Moves the dragged stop once its centre has crossed a neighbour's
    // centre. Comparing against the neighbour's midpoint in the direction
    // of travel (rather than "is the centre inside that card") means a
    // swap can never immediately undo itself.
    fun reorderIfNeeded() {

        val key = draggedKey ?: return
        val preview = reorderPreview ?: return
        if (dragCurrentIndex !in preview.indices) return

        val layout = listState.layoutInfo
        val current = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return

        // The list hasn't been re-laid-out since the last swap; its
        // positions still describe the old order.
        if (current.index - firstStopLazyIndex != dragCurrentIndex) return

        val draggedCenter = dragTopY + current.size / 2f

        val stopKeys = preview.map { timelineKey(it.stop) }.toSet()
        val neighbours = layout.visibleItemsInfo.filter { it.key != key && it.key in stopKeys }

        val target = if (draggedCenter > current.offset + current.size / 2f) {
            neighbours.lastOrNull { it.index > current.index && draggedCenter > it.offset + it.size / 2f }
        } else {
            neighbours.firstOrNull { it.index < current.index && draggedCenter < it.offset + it.size / 2f }
        } ?: return

        val toIndex = preview.indexOfFirst { timelineKey(it.stop) == target.key }

        if (toIndex < 0 || toIndex == dragCurrentIndex) return

        // A LazyColumn keeps its scroll position pinned to the KEY of the
        // first visible item. When that item is the one being dragged, or
        // the one it swaps with, the list would scroll to follow the key to
        // its new slot — shifting every card under the finger. That jump is
        // what made dragging upward toward the top of the list feel like it
        // was fighting back. Pinning the position by index for this swap
        // keeps the list still; only the two cards trade places.
        val firstVisible = listState.firstVisibleItemIndex
        if (current.index == firstVisible || target.index == firstVisible) {
            listState.requestScrollToItem(firstVisible, listState.firstVisibleItemScrollOffset)
        }

        reorderPreview = preview.toMutableList().apply {
            add(toIndex, removeAt(dragCurrentIndex))
        }

        dragCurrentIndex = toIndex

        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDragBy(deltaY: Float) {
        if (draggedKey == null) return
        dragTopY += deltaY
        reorderIfNeeded()
    }

    fun endDrag(commit: Boolean) {

        // Only here does the new order reach the ViewModel (and the route
        // recalculation behind it).
        if (commit && dragStartIndex >= 0 && dragCurrentIndex != dragStartIndex) {
            viewModel.moveStop(dragStartIndex, dragCurrentIndex)
        }

        // On cancel the preview is thrown away and the card returns to its
        // original slot, so there's no meaningful distance to glide from.
        val releasedKey = draggedKey
        val releasedAt = releasedKey?.let { key ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { dragTopY - it.offset }
        }
        if (commit && releasedKey != null && releasedAt != null) {
            settlingKey = releasedKey
            settleScope.launch {
                settleOffset.snapTo(releasedAt)
                settleOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                if (settlingKey == releasedKey) settlingKey = null
            }
        }

        draggedKey = null
        dragTopY = 0f
        dragStartTopY = 0f
        dragStartIndex = -1
        dragCurrentIndex = -1
        reorderPreview = null
    }

    // How far the dragged card is drawn from its laid-out slot. Read inside
    // graphicsLayer, so following the finger only redraws that one card
    // instead of recomposing the whole timeline every frame.
    val dragTranslation: (String) -> Float = { key ->
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?.let { dragTopY - it.offset }
            ?: 0f
    }

    // Auto-scroll while a card is held near the top or bottom edge.
    //
    // Two things were wrong with the old loop, and together they were the
    // "resistance" when dragging upward and the card landing in the wrong
    // place:
    //  - its edge zone was 90% of a card's height, on a list viewport only a
    //    few cards tall, so simply picking up a card near the bottom started
    //    scrolling the list DOWN — the opposite of an upward drag — and slid
    //    other stops under it;
    //  - it stepped on a 16 ms timer rather than on frames, so the scroll
    //    stuttered against the finger.
    // Now the zone is a fixed strip at each edge, it only scrolls in the
    // direction the card has actually been moved, speed grows the deeper the
    // card goes into the strip, and it runs once per frame.
    LaunchedEffect(draggedKey) {

        val key = draggedKey ?: return@LaunchedEffect

        while (isActive && draggedKey == key) {

            withFrameNanos { }

            val layout = listState.layoutInfo
            val current = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: continue

            val top = dragTopY
            val bottom = dragTopY + current.size
            val viewportTop = layout.viewportStartOffset.toFloat()
            val viewportBottom = layout.viewportEndOffset.toFloat()
            val moved = dragTopY - dragStartTopY

            val step = when {
                moved < -dragSlopPx && top < viewportTop + autoScrollZonePx -> {
                    val depth = ((viewportTop + autoScrollZonePx - top) / autoScrollZonePx).coerceIn(0.15f, 1f)
                    -autoScrollMaxStepPx * depth
                }
                moved > dragSlopPx && bottom > viewportBottom - autoScrollZonePx -> {
                    val depth = ((bottom - (viewportBottom - autoScrollZonePx)) / autoScrollZonePx).coerceIn(0.15f, 1f)
                    autoScrollMaxStepPx * depth
                }
                else -> 0f
            }

            if (step != 0f && listState.scrollBy(step) != 0f) {
                reorderIfNeeded()
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState()

    // Frame the drawn route, not just the stops — a walking path that swings
    // around the walls can extend well past the pins at either end.
    LaunchedEffect(uiState.generatedStops, uiState.routePath) {

        // The MapLibre map frames the route itself (ItineraryMapLibre).
        if (MapConfig.useMapLibre) return@LaunchedEffect

        val framePoints = uiState.routePath
            ?.points
            ?.takeIf { it.isNotEmpty() }
            ?: uiState.generatedStops.map { LatLng(it.latitude, it.longitude) }

        if (framePoints.size == 1) {

            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(framePoints.first(), 17f)

        } else if (framePoints.size > 1) {

            try {
                val boundsBuilder = LatLngBounds.Builder()
                framePoints.forEach { boundsBuilder.include(it) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 130)
                )
            } catch (e: Exception) {
                // Map not laid out yet.
            }
        }
    }

    TutorialTour(
        id = "route_overview",
        enabled = !uiState.isGeneratingRoute && uiState.generatedStops.isNotEmpty(),
        steps = listOf(
            TutorialStep(
                target = "route_map",
                title = "Your route",
                body = "Your stops are numbered on the map in the order you'll visit them, joined by the path to walk."
            ),
            TutorialStep(
                target = "route_name",
                title = "Name your trip",
                body = "We gave it a name already. Tap the pencil to change it to something you'll recognize."
            ),
            TutorialStep(
                target = "route_mode",
                title = "How you'll get around",
                body = if (TravelModes.available.size > 2) {
                    "Walk, drive or ride. Times and the route update to match."
                } else {
                    "Walk or drive. Times and the route update to match."
                }
            ),
            TutorialStep(
                target = "route_summary",
                title = "Your day at a glance",
                body = "Number of stops, total distance and travel time. Tap the start time to change when your day begins."
            ),
            TutorialStep(
                target = "route_first_stop",
                title = "Your stops, in order",
                body = "Each stop shows when you'll arrive. Press and hold a stop to drag it to a new spot, or tap it for details."
            ),
            TutorialStep(
                target = "route_add_stop",
                title = "Add a stop",
                body = "Forgot something? Add another site or place to your day."
            ),
            TutorialStep(
                target = "route_start",
                title = "Start your trip",
                body = "Begins turn-by-turn directions to your first stop. Each time you arrive, the next one is ready."
            ),
            TutorialStep(
                target = "route_save",
                title = "Or save it for later",
                body = "Keeps the plan in My Itineraries so you can start it another day."
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {

        Box(Modifier.tutorialTarget("route_map")) {
            RouteMap(
                uiState = uiState,
                cameraPositionState = cameraPositionState,
                onBack = { requestClose() }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                Box(Modifier.tutorialTarget("route_name")) {
                    TripHeader(
                        name = uiState.itineraryName,
                        usedFallbackLocation = uiState.usedFallbackLocation,
                        onRename = { showRenameDialog = true }
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    RouteErrorBanner(
                        message = message,
                        onDismiss = viewModel::clearError
                    )
                }
            }

            uiState.infoMessage?.let { message ->
                item {
                    RouteInfoBanner(
                        message = message,
                        onDismiss = viewModel::clearInfoMessage
                    )
                }
            }

            item {
                Box(Modifier.tutorialTarget("route_mode")) {
                    TravelModeSelector(
                        selected = uiState.travelMode,
                        onSelect = { viewModel.setTravelMode(it) }
                    )
                }
            }

            // Car (and, on the Google map, motorcycle) trips: expressways /
            // toll roads on or off for every leg.
            if (uiState.travelMode == TravelMode.DRIVING ||
                (uiState.travelMode == TravelMode.MOTORCYCLE && !com.histoury.app.map.MapConfig.useMapLibre)
            ) {
                item {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val avoid by com.histoury.app.data.repository.DriveOptions.avoidExpressways.collectAsState()
                    com.histoury.app.ui.components.AvoidExpresswaysToggle(
                        checked = avoid,
                        onCheckedChange = { com.histoury.app.data.repository.DriveOptions.setAvoidExpressways(ctx, it) }
                    )
                }
            }

            // A saved mode that can't be routed yet stays selected; say so
            // instead of quietly routing the trip as something else.
            TravelModes.unavailableMessage(uiState.travelMode)?.let { message ->
                item {
                    ComingSoonModeBanner(message = message)
                }
            }

            item {
                Box(Modifier.tutorialTarget("route_summary")) {
                    TripSummaryCard(
                        uiState = uiState,
                        onStartTimeClick = { showStartTimePicker = true }
                    )
                }
            }

            // Credit lines the drawn route needs (OpenStreetMap-based legs).
            val routeCredits = uiState.routePath?.attributions.orEmpty()
            if (routeCredits.isNotEmpty()) {
                item {
                    Text(
                        text = routeCredits.joinToString("\n"),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (uiState.routeIsApproximate && !uiState.isLoadingRoutePath) {
                item {
                    ApproximateRouteNotice(
                        unroutedLegs = uiState.routePath?.unroutedLegCount ?: 0,
                        travelMode = uiState.travelMode,
                        onRetry = { viewModel.retryRoutePath() }
                    )
                }
            }

            item {
                Text(
                    text = "Your Day",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Not in Intramuros yet: how to get from here to stop 1.
            if (uiState.approachLeg != null || uiState.isLoadingApproach) {
                item {
                    GettingThereCard(
                        leg = uiState.approachLeg,
                        isLoading = uiState.isLoadingApproach,
                        travelMode = uiState.travelMode,
                        firstStopName = uiState.generatedStops.firstOrNull()?.name,
                        onShowSteps = { showApproachSteps = true }
                    )
                }
            }

            item { ReorderHintRow(isReordering = isReordering) }

            itemsIndexed(
                items = displayedSchedule,
                // refType is part of the key: a site and a place can share a
                // Firestore document id, and a repeated LazyColumn key crashes.
                key = { _, scheduled -> timelineKey(scheduled.stop) }
            ) { index, scheduled ->

                val stopKey = scheduled.stop.refId
                val itemKey = timelineKey(scheduled.stop)

                TimelineStop(
                    index = index,
                    lastIndex = displayedSchedule.lastIndex,
                    scheduled = scheduled,
                    startTimeMinutes = uiState.startTimeMinutes,
                    travelMode = uiState.travelMode,
                    // Commute: the jeepney / bus / train steps of the leg
                    // into this stop, for "Jeepney → Train ›".
                    commuteSteps = if (uiState.travelMode == TravelMode.COMMUTE && !isReordering) {
                        uiState.routePath?.legs?.firstOrNull { it.toIndex == index }?.steps.orEmpty()
                    } else {
                        emptyList()
                    },
                    onShowCommute = { commuteStepsFor = index },
                    isCompleted = scheduled.stop.status == StopStatus.COMPLETED,
                    // Walk legs and arrival times belong to an order that is
                    // still being decided, so they're withheld mid-drag
                    // rather than shown wrong.
                    isReordering = isReordering,
                    // Neighbours glide into their new slots; the dragged
                    // card is positioned by the finger, so it opts out.
                    modifier = (if (index == 0) Modifier.tutorialTarget("route_first_stop") else Modifier).animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = if (draggedKey == itemKey || settlingKey == itemKey) null else spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                    ),
                    // Still lifted while it settles, so it stays drawn
                    // above the neighbours it's gliding past.
                    isDragged = draggedKey == itemKey || settlingKey == itemKey,
                    dragOffsetY = {
                        when (itemKey) {
                            draggedKey -> dragTranslation(itemKey)
                            settlingKey -> settleOffset.value
                            else -> 0f
                        }
                    },
                    onDragStart = { beginDrag(itemKey) },
                    onDragBy = { delta -> onDragBy(delta) },
                    onDragStop = { endDrag(commit = true) },
                    onDragCancel = { endDrag(commit = false) },
                    onMoveUp = { viewModel.moveStopUp(index) },
                    onMoveDown = { viewModel.moveStopDown(index) },
                    onRemove = { viewModel.removeStop(stopKey) },
                    onOpenDetails = {
                        viewModel.openStopPreview(
                            refId = stopKey,
                            refType = scheduled.stop.refType,
                            mode = StopPreviewMode.VIEW_ONLY
                        )
                    }
                )
            }

            item {
                Box(Modifier.tutorialTarget("route_add_stop")) {
                    AddStopButton(onClick = { viewModel.openAddStopSheet() })
                }
            }

            if (isEditingExisting) {
                item {
                    Text(
                        text = "Delete Itinerary",
                        color = Danger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showDeleteConfirm = true }
                            .padding(vertical = 6.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(70.dp)) }
        }

        BottomActionBar(
            uiState = uiState,
            isEditingExisting = isEditingExisting,
            onStartTrip = {
                viewModel.beginTrip { firstStopId ->
                    navController.navigateSafely(Routes.Map.createRoute(firstStopId, autoStart = true))
                }
            },
            onSave = { saveWithConfirmation() }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = uiState.itineraryName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.onNameChange(newName)
                showRenameDialog = false
            }
        )
    }

    if (showStartTimePicker) {
        StartTimeDialog(
            currentMinutes = uiState.startTimeMinutes,
            onDismiss = { showStartTimePicker = false },
            onSelect = { minutes ->
                viewModel.setStartTimeMinutes(minutes)
                showStartTimePicker = false
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(if (isEditingExisting) "Save your changes?" else "Save this itinerary?") },
            text = {
                Text(
                    if (isEditingExisting) {
                        "You've changed this itinerary. Leaving now will discard those changes."
                    } else {
                        "This route hasn't been saved yet. Leaving now will discard it."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    saveWithConfirmation()
                }) {
                    Text("Save", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.closeBuilder()
                }) {
                    Text("Discard", color = Danger)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this itinerary?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    uiState.editingItineraryId?.let { viewModel.deleteItinerary(it) }
                    showDeleteConfirm = false
                    viewModel.closeBuilder()
                }) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showApproachSteps) {
        val leg = uiState.approachLeg
        val first = uiState.generatedStops.firstOrNull()
        if (leg == null || first == null || leg.steps.isEmpty()) {
            showApproachSteps = false
        } else {
            CommuteStepsSheet(
                fromName = "Your location",
                toName = first.name,
                steps = leg.steps,
                onDismiss = { showApproachSteps = false }
            )
        }
    }

    commuteStepsFor?.let { index ->
        val stops = uiState.generatedStops
        val leg = uiState.routePath?.legs?.firstOrNull { it.toIndex == index }
        val to = stops.getOrNull(index)
        val from = stops.getOrNull(index - 1)
        if (leg == null || to == null || leg.steps.isEmpty()) {
            commuteStepsFor = null
        } else {
            CommuteStepsSheet(
                fromName = from?.name,
                toName = to.name,
                steps = leg.steps,
                onDismiss = { commuteStepsFor = null }
            )
        }
    }

    if (uiState.showAddStopSheet) {
        AddStopSheet(
            viewModel = viewModel,
            existingStopIds = uiState.generatedStops.map { it.refId }.toSet()
        )
    }

    ItineraryStopPreviewHost(viewModel)
}

/** LazyColumn / drag key for a stop — refType included so a site and a place with the same id can't collide. */
private fun timelineKey(stop: ItineraryStop): String = "${stop.refType}:${stop.refId}"

// ---------------------------------------------------------------- map

@Composable
private fun RouteMap(
    uiState: ItineraryUiState,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    onBack: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 300.dp)
            .aspectRatio(1.55f)
    ) {

        if (MapConfig.useMapLibre) {
            ItineraryMapLibre(
                routePath = uiState.routePath,
                stops = uiState.generatedStops,
                approachLeg = uiState.approachLeg,
                showUserLocation = uiState.approachLeg != null,
                modifier = Modifier.fillMaxSize()
            )
        } else GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = rememberThemedMapProperties(),
            mapColorScheme = rememberThemedMapColorScheme(),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false
            )
        ) {

            val path = uiState.routePath

            if (path != null && !path.isEmpty) {

                // One Polyline per leg rather than one for the whole route:
                // legs that fell back to a straight line are drawn dashed, so
                // an unrouted hop is visibly different from a real one
                // instead of blending into the rest of the line.
                path.legs.forEach { leg ->

                    val destinationCompleted = uiState.generatedStops
                        .getOrNull(leg.toIndex)
                        ?.status == StopStatus.COMPLETED

                    if (leg.isStraightLineFallback) {

                        // No real route for this leg. Nothing is drawn: a
                        // line straight through walls and buildings looks
                        // like a walking route and isn't one. The numbered
                        // pins still show both stops, and the notice in the
                        // list says which leg couldn't be routed.

                    } else if (destinationCompleted) {

                        Polyline(
                            points = leg.points,
                            color = CompletedContentColor,
                            width = 10f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            zIndex = 2f
                        )

                    } else {

                        // Wider white line underneath lifts the route off the
                        // map's own roads at a glance.
                        Polyline(
                            points = leg.points,
                            color = Color.White,
                            width = 17f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            zIndex = 1f
                        )

                        Polyline(
                            points = leg.points,
                            color = Primary,
                            width = 10f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            zIndex = 2f
                        )

                    }
                }

            }

            // No placeholder line while the route loads: a connect-the-dots
            // line is exactly the straight-through-buildings path this map
            // must never suggest. The pins are enough until the real route
            // arrives.

            uiState.generatedStops.forEachIndexed { index, stop ->

                val isCompleted = stop.status == StopStatus.COMPLETED
                val pinColor = when {
                    isCompleted -> CompletedContentColor
                    stop.isRecommended -> AccentGold
                    else -> Primary
                }

                val icon = remember(index, stop.isRecommended, isCompleted) {
                    numberedPin(number = index + 1, fillColor = pinColor.toArgb())
                }

                val markerState = remember(stop.refType, stop.refId, stop.latitude, stop.longitude) {
                    MarkerState(position = LatLng(stop.latitude, stop.longitude))
                }

                Marker(
                    state = markerState,
                    title = "${index + 1}. ${stop.name}${if (isCompleted) " · Done" else ""}",
                    icon = icon,
                    anchor = Offset(0.5f, 1f),
                    zIndex = 3f
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Sits over a full-bleed map, so it insets itself — BEFORE
                // the circle is drawn. The status-bar inset used to be
                // applied after clip/background, so it became part of the
                // button: a tall dark pill running up behind the clock.
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(CardSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Primary
            )
        }

        AnimatedVisibility(
            visible = uiState.isLoadingRoutePath,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tracing the route\u2026",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Draws a numbered map pin at runtime.
 *
 * The Maps SDK only ships coloured teardrops with no label, so the stop
 * order had to live in the marker's tap-title — invisible until tapped, and
 * useless for reading a route at a glance. Rendering the number into the pin
 * makes the map match the numbered list underneath it.
 */
private fun numberedPin(number: Int, fillColor: Int): BitmapDescriptor {

    val diameter = 84
    val pointerHeight = 26
    val ring = 7f

    val bitmap = Bitmap.createBitmap(
        diameter,
        diameter + pointerHeight,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)

    val center = diameter / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pointer first, so the circle's white ring paints over its top edge.
    paint.color = fillColor

    val pointer = Path().apply {
        moveTo(center - 15f, diameter - 12f)
        lineTo(center + 15f, diameter - 12f)
        lineTo(center, (diameter + pointerHeight).toFloat())
        close()
    }

    canvas.drawPath(pointer, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center, paint)

    paint.color = fillColor
    canvas.drawCircle(center, center, center - ring, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f

    canvas.drawText(number.toString(), center, center - textOffset, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// ---------------------------------------------------------------- header

@Composable
private fun TripHeader(
    name: String,
    usedFallbackLocation: Boolean,
    onRename: () -> Unit
) {

    Column {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                text = name.ifBlank { "My Intramuros Trip" },
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Rename",
                tint = TextSecondary,
                // Bigger tap area around the same 18dp icon.
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRename
                    )
                    .padding(11.dp)
                    .size(18.dp)
            )
        }

        if (usedFallbackLocation) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ordered from the Intramuros center \u2014 turn on location " +
                    "for a route that starts exactly where you are.",
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun TravelModeSelector(
    selected: TravelMode,
    onSelect: (TravelMode) -> Unit
) {

    val modes = listOf(
        Triple(TravelMode.WALKING, Icons.Default.DirectionsWalk, "Walk"),
        Triple(TravelMode.DRIVING, Icons.Default.DirectionsCar, "Drive"),
        Triple(TravelMode.MOTORCYCLE, Icons.Default.TwoWheeler, "Ride"),
        Triple(TravelMode.COMMUTE, Icons.Default.DirectionsTransit, "Commute")
    ).filter { (mode, _, _) -> mode in TravelModes.shownWith(selected) }
    // Postponed modes aren't offered, except a saved one that is selected,
    // which is shown (marked "Soon") so the trip's real mode stays visible.

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        modes.forEach { (mode, icon, label) ->

            val isSelected = mode == selected

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) PrimarySoft else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(mode) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Primary else TextTertiary,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(Modifier.width(5.dp))

                Text(
                    text = if (TravelModes.isAvailable(mode)) label else "$label · Soon",
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Primary else TextSecondary
                )
            }
        }
    }
}

/** Shown under the mode selector when the trip's saved mode is coming soon. */
@Composable
private fun ComingSoonModeBanner(message: String) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrimarySoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "Coming Soon",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$message Travel times below are rough straight-line estimates, not directions.",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Justify
            )
        }
    }
}

@Composable
private fun TripSummaryCard(
    uiState: ItineraryUiState,
    onStartTimeClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            SummaryStat(
                icon = Icons.Default.Place,
                value = uiState.generatedStops.size.toString(),
                label = "Stops"
            )

            SummaryStat(
                icon = Icons.Default.Straighten,
                value = formatDistance(uiState.totalDistanceMeters),
                label = "Distance"
            )

            SummaryStat(
                icon = Icons.Default.DirectionsWalk,
                value = formatDuration(uiState.totalWalkingMinutes),
                label = "Travel"
            )

            SummaryStat(
                icon = Icons.Default.AccessTime,
                value = formatDuration(
                    if (uiState.itineraryStatus == ItineraryStatus.COMPLETED) {
                        uiState.actualTripMinutes ?: uiState.totalTripMinutes
                    } else {
                        uiState.totalTripMinutes
                    }
                ),
                label = if (uiState.itineraryStatus == ItineraryStatus.COMPLETED) "Actual" else "Estimated"
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Outline)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStartTimeClick
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Start at ${formatClock(uiState.startTimeMinutes)}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = if (uiState.schedule.isEmpty()) {
                    "Change"
                } else {
                    "Back by ${formatClock(uiState.finishTimeMinutes)}"
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SummaryStat(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(17.dp)
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextPrimary,
            maxLines = 1
        )

        Text(
            text = label,
            fontSize = 10.5.sp,
            color = TextSecondary
        )
    }
}

/**
 * Shown when one or more legs have no real route. Those legs are not drawn
 * on the map and their times are estimates; this says so, and offers a
 * retry (most failures are a dropped connection).
 */
@Composable
private fun ApproximateRouteNotice(
    unroutedLegs: Int,
    travelMode: TravelMode,
    onRetry: () -> Unit
) {

    val modeWord = when (travelMode) {
        TravelMode.WALKING -> "walking"
        TravelMode.DRIVING -> "driving"
        TravelMode.MOTORCYCLE -> "riding"
        TravelMode.COMMUTE -> "commute"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DangerSurface)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Danger,
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (unroutedLegs == 1) {
                    "No $modeWord route found for 1 leg"
                } else {
                    "No $modeWord route found for $unroutedLegs legs"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Those legs aren't drawn on the map, and their times are estimates.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }

        TextButton(onClick = onRetry) {
            Text("Retry", color = Primary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
    }
}

// ---------------------------------------------------------------- timeline

/**
 * One stop in the built itinerary.
 *
 * Every card has the same structure wherever it sits in the list: name,
 * arrival time, and how you get there from the previous stop. The travel
 * line used to be a separate row ABOVE each card except the first, joined
 * by a connector stub — so a card changed height the moment it was dragged
 * into or out of first place, and jumped under the finger. Inside the card,
 * and reading "First stop" on the first, it can't.
 *
 * The estimated stay ("20 min here") and its picker are gone: they were
 * planning detail, not something anyone needs while walking the route.
 */
@Composable
private fun TimelineStop(
    index: Int,
    lastIndex: Int,
    scheduled: ItineraryPlanner.ScheduledStop,
    startTimeMinutes: Int,
    travelMode: TravelMode,
    isCompleted: Boolean,
    isReordering: Boolean,
    isDragged: Boolean,
    commuteSteps: List<com.histoury.app.data.repository.RouteStep> = emptyList(),
    onShowCommute: () -> Unit = {},
    dragOffsetY: () -> Float,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragStop: () -> Unit,
    onDragCancel: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {

    val stop = scheduled.stop

    // pointerInput(stop.refId) keeps running across recompositions, so the
    // gesture handlers must read the latest callbacks rather than the ones
    // captured when the card first appeared.
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDragBy by rememberUpdatedState(onDragBy)
    val latestDragStop by rememberUpdatedState(onDragStop)
    val latestDragCancel by rememberUpdatedState(onDragCancel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The card being dragged has to paint over its neighbours as it
            // passes them, and translate freely of its laid-out slot.
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY()
                val lift = if (isDragged) 1.03f else 1f
                scaleX = lift
                scaleY = lift
            }
            .shadow(
                elevation = if (isDragged) 12.dp else 2.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCompleted && !isDragged) CompletedSurfaceColor else CardSurface)
            // Press and hold anywhere on the card to pick it up. The
            // handle on the right does the same without the wait, for
            // anyone who already knows where they're going.
            .pointerInput(stop.refId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDragBy(amount.y)
                    },
                    onDragEnd = { latestDragStop() },
                    onDragCancel = { latestDragCancel() }
                )
            }
            // Dragging is a pointer gesture, so it doesn't exist for
            // anyone navigating by screen reader or switch access.
            // These give the same two moves as explicit actions.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Move earlier") {
                        onMoveUp(); true
                    },
                    CustomAccessibilityAction("Move later") {
                        onMoveDown(); true
                    }
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> CompletedContentColor
                        stop.isRecommended -> AccentGold
                        else -> Primary
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (index + 1).toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
        }

        Spacer(Modifier.width(10.dp))

        // The thumbnail doubles as "what was this place again?" — the
        // one question a built itinerary still leaves open, especially
        // for stops that arrived via Smart Recommendations.
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSoft)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "View details",
                    onClick = onOpenDetails
                )
        ) {
            if (stop.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = stop.imageUrl,
                    contentDescription = stop.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (isCompleted) 0.45f else 1f)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text = stop.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = if (isCompleted) TextTertiary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCompleted) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = CompletedContentColor) {
                        Text(
                            text = "Done",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (stop.isRecommended) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = PrimarySoft) {
                        Text(
                            text = "Suggested",
                            color = Primary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = if (isReordering) {
                    "Stop ${index + 1} of ${lastIndex + 1}"
                } else {
                    "Arrive ${formatClock(startTimeMinutes + scheduled.arrivalMinutesFromStart)}"
                },
                fontSize = 11.5.sp,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(Modifier.height(3.dp))

            // How to get here from the previous stop — the one number that
            // matters while walking the route. Always one line, so every
            // card is the same shape.
            // Commute: which rides, and a tap opens the step-by-step
            // directions (which jeepney, where to get on and off).
            val rides = com.histoury.app.ui.components.commuteRideSummary(commuteSteps)
            val canShowSteps = index > 0 && !isReordering && commuteSteps.isNotEmpty()

            Row(
                modifier = if (canShowSteps) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Show commute directions",
                        onClick = onShowCommute
                    )
                } else Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = if (index == 0) {
                        Icons.Default.Flag
                    } else {
                        when (travelMode) {
                            TravelMode.WALKING -> Icons.Default.DirectionsWalk
                            TravelMode.DRIVING -> Icons.Default.DirectionsCar
                            TravelMode.MOTORCYCLE -> Icons.Default.TwoWheeler
                            TravelMode.COMMUTE -> Icons.Default.DirectionsTransit
                        }
                    },
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(13.dp)
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = when {
                        index == 0 -> "First stop"
                        isReordering -> "Recalculating\u2026"
                        else -> travelSummary(
                            scheduled,
                            travelMode,
                            // Walking was quicker than any ride for this leg.
                            walkOnly = canShowSteps && rides == null
                        ) + (rides?.let { " \u00b7 $it" } ?: "")
                    },
                    fontSize = 11.sp,
                    color = if (canShowSteps) Primary else TextTertiary,
                    fontWeight = if (canShowSteps) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (canShowSteps) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder stop",
                tint = if (isDragged) Primary else TextTertiary,
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(stop.refId) {
                        detectDragGestures(
                            onDragStart = { latestDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                latestDragBy(amount.y)
                            },
                            onDragEnd = { latestDragStop() },
                            onDragCancel = { latestDragCancel() }
                        )
                    }
                    .padding(9.dp)
            )

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove stop",
                tint = TextTertiary,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    )
                    .padding(11.dp)
            )
        }
    }
}

/** "6 min walk · 450 m", with "~" and "est." when the leg has no real route. */
private fun travelSummary(
    scheduled: ItineraryPlanner.ScheduledStop,
    travelMode: TravelMode,
    walkOnly: Boolean = false
): String {
    val verb = if (walkOnly) "walk" else when (travelMode) {
        TravelMode.WALKING -> "walk"
        TravelMode.DRIVING -> "drive"
        TravelMode.MOTORCYCLE -> "ride"
        TravelMode.COMMUTE -> "commute"
    }
    return buildString {
        if (scheduled.travelIsApproximate) append("~")
        append(scheduled.travelMinutesToHere)
        append(" min ")
        append(verb)
        if (scheduled.travelMetersToHere > 0) {
            append(" \u00b7 ")
            append(formatDistance(scheduled.travelMetersToHere.toDouble()))
        }
        if (scheduled.travelIsApproximate) append(" (est.)")
    }
}

/**
 * Tells the visitor the cards are draggable, since a long press leaves no
 * mark on screen until it's tried. Swaps to a live instruction once a card
 * is actually in hand.
 */
@Composable
private fun ReorderHintRow(isReordering: Boolean) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isReordering) PrimarySoft else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = null,
            tint = if (isReordering) Primary else TextTertiary,
            modifier = Modifier.size(14.dp)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = if (isReordering) {
                "Drop it where you want to go"
            } else {
                "Press and hold a stop to reorder your day"
            },
            fontSize = 11.sp,
            fontWeight = if (isReordering) FontWeight.Bold else FontWeight.Medium,
            color = if (isReordering) Primary else TextTertiary
        )
    }
}

@Composable
private fun AddStopButton(onClick: () -> Unit) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrimarySoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add stop",
            tint = Primary,
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = "Add Stop",
            color = Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
    }
}

/** Neutral feedback banner, e.g. the "Optimize Order" result. Clears itself after a few seconds. */
@Composable
private fun RouteInfoBanner(
    message: String,
    onDismiss: () -> Unit
) {

    LaunchedEffect(message) {
        delay(5000)
        onDismiss()
    }

    Surface(
        color = PrimarySoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = TextPrimary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RouteErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = DangerSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Danger,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    uiState: ItineraryUiState,
    isEditingExisting: Boolean,
    onStartTrip: () -> Unit,
    onSave: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {

        if (uiState.isGeneratingRoute) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(26.dp))
            }

        } else if (uiState.itineraryStatus == ItineraryStatus.COMPLETED) {

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CompletedSurfaceColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Done · This summary will be automatically deleted 48 hours after completion.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            PrimaryButton(
                text = "Revisit Itinerary",
                onClick = onStartTrip
            )

        } else {

            Box(Modifier.tutorialTarget("route_start")) {
                PrimaryButton(
                    text = when {
                        uiState.generatedStops.any { it.status != StopStatus.PENDING } -> "Resume Trip"
                        else -> "Start Trip Now"
                    },
                    onClick = onStartTrip
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .tutorialTarget("route_save")
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceSoft)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !uiState.isSaving,
                        onClick = onSave
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = if (isEditingExisting) "Save Changes" else "Save for Later",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {

    var text by remember { mutableStateOf(currentName) }
    var touched by remember { mutableStateOf(false) }

    // Live check: a name is required and kept short enough to fit its card.
    val nameError = Validators.text(text, "Itinerary name", min = 1, max = ITINERARY_NAME_MAX)
    val shownError = if (touched) nameError else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Itinerary") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        touched = true
                    },
                    singleLine = true,
                    isError = shownError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        cursorColor = Primary
                    )
                )
                CharacterCounter(text.trim().length, ITINERARY_NAME_MAX)
                FieldErrorText(shownError)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Final check with the same rule.
                touched = true
                if (nameError == null) onConfirm(text.trim())
            }) {
                Text("Save", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Half-hour slots from 6 AM to 7 PM. A full clock picker would be more
 * flexible than anyone needs — Intramuros' sites keep daytime hours, and a
 * scrollable list of realistic start times is faster to use than a dial.
 */
@Composable
private fun StartTimeDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {

    val options = remember {
        (6 * 60..19 * 60 step 30).toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start the day at") },
        text = {

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {

                items(options.size) { index ->

                    val minutes = options[index]
                    val isSelected = minutes == currentMinutes

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimarySoft else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(minutes) }
                            .padding(vertical = 11.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = formatClock(minutes),
                            color = if (isSelected) Primary else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

// ---------------------------------------------------------------- add stop

@Composable
private fun AddStopSheet(
    viewModel: ItineraryViewModel,
    existingStopIds: Set<String>
) {

    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeAddStopSheet() },
        containerColor = CardSurface
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Tall enough to browse; the search and chips sit on top.
                .heightIn(max = 640.dp)
                .padding(bottom = 24.dp)
        ) {

            Text(
                text = "Add a Stop",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "Tap for details \u2022 + to add",
                fontSize = 11.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(4.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val userPosition by com.histoury.app.util.UserDistance.position.collectAsState()
            LaunchedEffect(Unit) { com.histoury.app.util.UserDistance.refresh(context) }

            var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
            var sort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(BuilderSort.NEAREST) }
            var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

            // Sites and every kind of place in one list, so the chips can
            // filter by type and search/sort treat them alike. Anything
            // already in the trip is left out.
            val options = remember(uiState.allSites, uiState.allPlaces, existingStopIds) {
                uiState.allSites
                    .filter { it.documentId !in existingStopIds && it.latitude != null && it.longitude != null }
                    .map { site ->
                        AddStopOption(
                            id = site.documentId, isSite = true, group = SITES_GROUP,
                            title = site.siteName, typeLabel = "\uD83C\uDFDB\uFE0F Historical Site",
                            location = site.location, description = site.description,
                            imageUrl = site.featuredImage, latitude = site.latitude, longitude = site.longitude
                        )
                    } +
                    uiState.allPlaces
                        .filter { it.documentId !in existingStopIds && it.latitude != null && it.longitude != null }
                        .map { place ->
                            AddStopOption(
                                id = place.documentId, isSite = false, group = placeCategoryGroup(place.category),
                                title = place.name, typeLabel = "${place.categoryEmoji} ${place.categoryLabel}",
                                location = place.location, description = place.description,
                                imageUrl = place.featuredImage, latitude = place.latitude, longitude = place.longitude
                            )
                        }
            }

            val chips = remember(options) {
                buildList {
                    val siteCount = options.count { it.isSite }
                    if (siteCount > 0) {
                        add(BuilderCategoryChip(SITES_GROUP, "Historical Sites", Icons.Default.AccountBalance, siteCount))
                    }
                    com.histoury.app.ui.components.ContentCategory.entries
                        .filter { it.key != "all" && it.key != "sites" }
                        .forEach { option ->
                            val count = options.count { !it.isSite && it.group == option.key }
                            if (count > 0) add(BuilderCategoryChip(option.key, option.label, option.icon, count))
                        }
                }
            }
            val activeGroup = group?.takeIf { key -> chips.any { it.key == key } }

            val shown = remember(options, query, sort, activeGroup, userPosition) {
                builderFilterAndSort(
                    items = options.filter { activeGroup == null || it.group == activeGroup },
                    query = query,
                    sort = sort,
                    from = userPosition,
                    name = { it.title },
                    latitude = { it.latitude },
                    longitude = { it.longitude },
                    location = { it.location },
                    description = { it.description }
                )
            }

            BuilderListControls(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search sites and places...",
                sort = sort,
                onSortChange = { sort = it },
                hasLocation = userPosition != null,
                resultCount = shown.size,
                categories = chips,
                selectedCategory = activeGroup,
                onCategoryChange = { group = it },
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                items(shown.size) { index ->
                    val option = shown[index]
                    val distance = com.histoury.app.util.UserDistance.labelFor(option.latitude, option.longitude, userPosition)
                    AddStopRow(
                        title = option.title,
                        subtitle = listOfNotNull(option.typeLabel, distance?.let { if (it == "Nearby") it else "$it away" })
                            .joinToString("  \u00B7  "),
                        imageUrl = option.imageUrl,
                        onClick = {
                            if (option.isSite) {
                                uiState.allSites.firstOrNull { it.documentId == option.id }?.let { viewModel.addStopFromSite(it) }
                            } else {
                                uiState.allPlaces.firstOrNull { it.documentId == option.id }?.let { viewModel.addStopFromPlace(it) }
                            }
                        },
                        onInfoClick = {
                            viewModel.openStopPreview(
                                refId = option.id,
                                refType = if (option.isSite) "site" else "place",
                                mode = StopPreviewMode.ADD_TO_ROUTE
                            )
                        }
                    )
                }

                if (shown.isEmpty()) {
                    items(1) {
                        Text(
                            text = when {
                                options.isEmpty() -> "Everything's already in this itinerary."
                                query.isNotBlank() -> "Nothing matches \"$query\"."
                                else -> "Nothing of this kind left to add."
                            },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStopRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    // The row (picture or name) opens details; only the + adds the stop.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onInfoClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface)
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add $title to trip",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- formatting

/** 0 -> "12:00 AM", 545 -> "9:05 AM", 780 -> "1:00 PM". */
internal fun formatClock(minutesPastMidnight: Int): String {

    val wrapped = ((minutesPastMidnight % 1440) + 1440) % 1440

    val hour24 = wrapped / 60
    val minute = wrapped % 60

    val suffix = if (hour24 < 12) "AM" else "PM"

    val hour12 = when {
        hour24 % 12 == 0 -> 12
        else -> hour24 % 12
    }

    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

/** 45 -> "45 min", 90 -> "1h 30m", 120 -> "2h". */
internal fun formatDuration(minutes: Int): String {

    if (minutes < 60) {
        return "$minutes min"
    }

    val hours = minutes / 60
    val remainder = minutes % 60

    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}

/** Height of the strip at each edge of the stop list that auto-scrolls while a card is held in it. */
private val AUTO_SCROLL_ZONE = 64.dp

/** Fastest auto-scroll, per frame, when the card is pushed right to the edge. */
private val AUTO_SCROLL_MAX_STEP = 12.dp

/** Longest itinerary name accepted in Rename. */
private const val ITINERARY_NAME_MAX = 60

/** Filter key for historical sites in the Add-a-Stop chips. */
private const val SITES_GROUP = "sites"

/** A site or a place, flattened so the Add-a-Stop sheet can search, sort and filter them together. */
private data class AddStopOption(
    val id: String,
    val isSite: Boolean,
    val group: String,
    val title: String,
    val typeLabel: String,
    val location: String,
    val description: String,
    val imageUrl: String,
    val latitude: Double?,
    val longitude: Double?
)

/**
 * Commute directions from one stop to the next: each walk and each ride
 * (which jeepney, bus or train, towards where, where to get on and off).
 */
@Composable
private fun CommuteStepsSheet(
    fromName: String?,
    toName: String,
    steps: List<com.histoury.app.data.repository.RouteStep>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Getting to $toName",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (fromName != null) {
                Text(
                    text = "From $fromName",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            com.histoury.app.ui.components.CommuteTimeline(steps, toName)
        }
    }
}

/**
 * "Getting there": from where the visitor is now to the first stop, in the
 * trip's travel mode. For commute, the rides it takes and a tap for the
 * step-by-step (which bus, jeepney or train; where to get on and off).
 */
@Composable
private fun GettingThereCard(
    leg: com.histoury.app.data.routing.RouteLeg?,
    isLoading: Boolean,
    travelMode: TravelMode,
    firstStopName: String?,
    onShowSteps: () -> Unit
) {
    val hasSteps = travelMode == TravelMode.COMMUTE && leg != null && leg.steps.isNotEmpty() &&
        !leg.isStraightLineFallback
    val rides = if (hasSteps) com.histoury.app.ui.components.commuteRideSummary(leg!!.steps) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PrimarySoft)
            .then(
                if (hasSteps) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Show directions",
                    onClick = onShowSteps
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Getting there" + (firstStopName?.let { " \u00b7 to $it" } ?: ""),
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    leg == null && isLoading -> "Finding the way from your location\u2026"
                    leg == null -> "From your location"
                    else -> buildString {
                        if (leg.isStraightLineFallback) append("~")
                        append(formatLegMinutes(leg.durationMinutes))
                        append(" \u00b7 ")
                        append(formatDistance(leg.distanceMeters.toDouble()))
                        if (leg.isStraightLineFallback) append(" (est.)")
                        rides?.let { append(" \u00b7 ").append(it) }
                    }
                },
                color = if (hasSteps) Primary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (hasSteps) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLoading && leg == null) {
            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else if (hasSteps) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** "45 min", "1 h 20 min". */
private fun formatLegMinutes(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
