@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.screens.explore

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.histoury.app.ui.components.rememberThemedMapProperties
import com.histoury.app.data.itinerary.ItineraryPlanner
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
    var expandedStopId by remember { mutableStateOf<String?>(null) }

    val isEditingExisting = uiState.editingItineraryId != null

    val listState = rememberLazyListState()

    val haptics = LocalHapticFeedback.current

    // ----- drag-to-reorder -----
    //
    // The reorder is previewed locally and only committed to the ViewModel
    // on release. Reordering there discards the road-following path and
    // refetches it, so applying every intermediate position would fire a
    // burst of Routes API calls for orders nobody chose.
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }
    var reorderPreview by remember {
        mutableStateOf<List<ItineraryPlanner.ScheduledStop>?>(null)
    }

    val isReordering = draggedKey != null

    val displayedSchedule = reorderPreview ?: uiState.schedule

    fun beginDrag(index: Int, key: String) {
        expandedStopId = null
        reorderPreview = uiState.schedule
        draggedKey = key
        dragStartIndex = index
        dragCurrentIndex = index
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragBy(deltaY: Float) {

        val key = draggedKey ?: return
        val preview = reorderPreview ?: return

        dragOffsetY += deltaY

        val layout = listState.layoutInfo

        val current = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return

        // Where the dragged card is actually drawn right now: its laid-out
        // slot plus however far the finger has carried it.
        val draggedCenter = current.offset + (current.size / 2f) + dragOffsetY

        val stopKeys = preview.map { it.stop.refId }.toSet()

        val target = layout.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != key &&
                candidate.key in stopKeys &&
                draggedCenter >= candidate.offset &&
                draggedCenter <= candidate.offset + candidate.size
        } ?: return

        val toIndex = preview.indexOfFirst { it.stop.refId == target.key }

        if (toIndex < 0 || toIndex == dragCurrentIndex) return

        reorderPreview = preview.toMutableList().apply {
            add(toIndex, removeAt(dragCurrentIndex))
        }

        // The card is about to be re-laid-out into the slot it swapped
        // with, so cancel that jump out of the offset and it stays put
        // under the finger.
        dragOffsetY -= (target.offset - current.offset)

        dragCurrentIndex = toIndex

        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun endDrag(commit: Boolean) {

        if (commit && dragStartIndex >= 0 && dragCurrentIndex != dragStartIndex) {
            viewModel.moveStop(dragStartIndex, dragCurrentIndex)
        }

        draggedKey = null
        dragOffsetY = 0f
        dragStartIndex = -1
        dragCurrentIndex = -1
        reorderPreview = null
    }

    // Dragging a stop past the edge of the viewport scrolls the list rather
    // than dead-ending, so a stop can be moved further than one screenful.
    // Whatever the list actually consumes is added back to the drag offset,
    // which keeps the card pinned to the finger while the content moves
    // underneath it.
    LaunchedEffect(draggedKey) {

        if (draggedKey == null) return@LaunchedEffect

        while (true) {

            val layout = listState.layoutInfo

            val current = layout.visibleItemsInfo.firstOrNull { it.key == draggedKey }

            if (current != null) {

                val draggedCenter = current.offset + (current.size / 2f) + dragOffsetY

                val edge = current.size * 0.9f

                val step = when {
                    draggedCenter < layout.viewportStartOffset + edge -> -14f
                    draggedCenter > layout.viewportEndOffset - edge -> 14f
                    else -> 0f
                }

                if (step != 0f) {
                    dragOffsetY += listState.scrollBy(step)
                }
            }

            delay(16)
        }
    }

    val cameraPositionState = rememberCameraPositionState()

    // Frame the drawn route, not just the stops — a walking path that swings
    // around the walls can extend well past the pins at either end.
    LaunchedEffect(uiState.generatedStops, uiState.routePath) {

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSoft)
    ) {

        RouteMap(
            uiState = uiState,
            cameraPositionState = cameraPositionState,
            onBack = { viewModel.closeBuilder() }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                TripHeader(
                    name = uiState.itineraryName,
                    usedFallbackLocation = uiState.usedFallbackLocation,
                    onRename = { showRenameDialog = true }
                )
            }

            uiState.errorMessage?.let { message ->
                item {
                    RouteErrorBanner(
                        message = message,
                        onDismiss = viewModel::clearError
                    )
                }
            }

            item {
                TravelModeSelector(
                    selected = uiState.travelMode,
                    onSelect = { viewModel.setTravelMode(it) }
                )
            }

            item {
                TripSummaryCard(
                    uiState = uiState,
                    onStartTimeClick = { showStartTimePicker = true }
                )
            }

            if (uiState.routeIsApproximate && !uiState.isLoadingRoutePath) {
                item { ApproximateRouteNotice() }
            }

            item {
                SectionHeaderRow(
                    title = "Your Day",
                    actionLabel = "Optimize Order",
                    actionIcon = Icons.Default.Route,
                    onAction = { viewModel.optimizeStopOrder() }
                )
            }

            item { ReorderHintRow(isReordering = isReordering) }

            itemsIndexed(
                items = displayedSchedule,
                key = { _, scheduled -> scheduled.stop.refId }
            ) { index, scheduled ->

                val stopKey = scheduled.stop.refId

                TimelineStop(
                    index = index,
                    lastIndex = displayedSchedule.lastIndex,
                    scheduled = scheduled,
                    startTimeMinutes = uiState.startTimeMinutes,
                    travelMode = uiState.travelMode,
                    isCompleted = stopKey in uiState.completedStopIds,
                    isExpanded = expandedStopId == stopKey,
                    // Walk legs and arrival times belong to an order that is
                    // still being decided, so they're withheld mid-drag
                    // rather than shown wrong.
                    isReordering = isReordering,
                    isDragged = draggedKey == stopKey,
                    dragOffsetY = if (draggedKey == stopKey) dragOffsetY else 0f,
                    onDragStart = { beginDrag(index, stopKey) },
                    onDragBy = { delta -> onDragBy(delta) },
                    onDragStop = { endDrag(commit = true) },
                    onDragCancel = { endDrag(commit = false) },
                    onToggleExpanded = {
                        expandedStopId = if (expandedStopId == stopKey) null else stopKey
                    },
                    onVisitMinutesChange = { minutes ->
                        viewModel.setVisitMinutes(stopKey, minutes)
                    },
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
                AddStopButton(onClick = { viewModel.openAddStopSheet() })
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
                    navController.navigate(Routes.Map.createRoute(firstStopId))
                }
            },
            onSave = { viewModel.saveItinerary {} }
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

    if (uiState.showAddStopSheet) {
        AddStopSheet(
            viewModel = viewModel,
            existingStopIds = uiState.generatedStops.map { it.refId }.toSet()
        )
    }

    ItineraryStopPreviewHost(viewModel)
}

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

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = rememberThemedMapProperties(),
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

                    val destinationId = uiState.generatedStops
                        .getOrNull(leg.toIndex)
                        ?.refId
                    val destinationCompleted = destinationId != null &&
                        destinationId in uiState.completedStopIds

                    if (destinationCompleted) {

                        Polyline(
                            points = leg.points,
                            color = CompletedContentColor,
                            width = 10f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            zIndex = 2f
                        )

                    } else if (!leg.isStraightLineFallback) {

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

                    } else {

                        Polyline(
                            points = leg.points,
                            color = TextTertiary,
                            width = 8f,
                            pattern = listOf(Dash(22f), Gap(16f)),
                            jointType = JointType.ROUND,
                            zIndex = 2f
                        )
                    }
                }

            } else if (uiState.generatedStops.size >= 2) {

                // Pre-route placeholder. Dashed and grey on purpose — this is
                // the connect-the-dots line, and it should never be mistaken
                // for the walking route that replaces it a moment later.
                Polyline(
                    points = uiState.generatedStops.map { LatLng(it.latitude, it.longitude) },
                    color = TextTertiary,
                    width = 8f,
                    pattern = listOf(Dash(22f), Gap(16f)),
                    jointType = JointType.ROUND,
                    zIndex = 2f
                )
            }

            uiState.generatedStops.forEachIndexed { index, stop ->

                val isCompleted = stop.refId in uiState.completedStopIds
                val pinColor = when {
                    isCompleted -> CompletedContentColor
                    stop.isRecommended -> AccentGold
                    else -> Primary
                }

                val icon = remember(index, stop.isRecommended, isCompleted) {
                    numberedPin(number = index + 1, fillColor = pinColor.toArgb())
                }

                Marker(
                    state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                    title = "${index + 1}. ${stop.name}${if (isCompleted) " · Done" else ""}",
                    icon = icon,
                    anchor = Offset(0.5f, 1f),
                    zIndex = 3f
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(16.dp)
                .clip(CircleShape)
                .background(CardSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .align(Alignment.TopStart)
                // Sits over a full-bleed map, so it insets itself.
                .statusBarsPadding()
                .padding(10.dp)
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
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRename
                    )
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
        Triple(TravelMode.MOTORCYCLE, Icons.Default.TwoWheeler, "Ride")
    )

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
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Primary else TextSecondary
                )
            }
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
                    if (uiState.itineraryStatus == "completed") {
                        uiState.actualTripMinutes ?: uiState.totalTripMinutes
                    } else {
                        uiState.totalTripMinutes
                    }
                ),
                label = if (uiState.itineraryStatus == "completed") "Actual" else "Estimated"
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

@Composable
private fun ApproximateRouteNotice() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrimarySoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {

        Icon(
            imageVector = Icons.Default.Straighten,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "Some legs couldn't be routed and are shown as dashed " +
                "straight lines \u2014 those distances are estimates.",
            fontSize = 11.5.sp,
            color = TextPrimary,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {

            Icon(
                imageVector = actionIcon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )

            Spacer(Modifier.width(5.dp))

            Text(
                text = actionLabel,
                color = Primary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------- timeline

@Composable
private fun TimelineStop(
    index: Int,
    lastIndex: Int,
    scheduled: ItineraryPlanner.ScheduledStop,
    startTimeMinutes: Int,
    travelMode: TravelMode,
    isCompleted: Boolean,
    isExpanded: Boolean,
    isReordering: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragStop: () -> Unit,
    onDragCancel: () -> Unit,
    onToggleExpanded: () -> Unit,
    onVisitMinutesChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetails: () -> Unit
) {

    val stop = scheduled.stop

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The card being dragged has to paint over its neighbours as it
            // passes them, and translate freely of its laid-out slot.
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                val lift = if (isDragged) 1.03f else 1f
                scaleX = lift
                scaleY = lift
            }
    ) {

        // Travel leg into this stop, sitting between the two cards where the
        // walk actually happens. It keeps its place in the layout during a
        // drag — collapsing every leg at once would yank the list out from
        // under the finger — but its numbers are withheld, since they
        // describe an order that's still being decided.
        if (index > 0) {
            TravelLegRow(
                minutes = scheduled.travelMinutesToHere,
                meters = scheduled.travelMetersToHere,
                isApproximate = scheduled.travelIsApproximate,
                travelMode = travelMode,
                isPending = isReordering
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDragged) 12.dp else 2.dp,
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isDragged -> CardSurface
                        isCompleted -> CompletedSurfaceColor
                        else -> CardSurface
                    }
                )
                // Press and hold anywhere on the card to pick it up. The
                // handle on the right does the same without the wait, for
                // anyone who already knows where they're going.
                .pointerInput(stop.refId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDragBy(amount.y)
                        },
                        onDragEnd = { onDragStop() },
                        onDragCancel = { onDragCancel() }
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
            verticalAlignment = Alignment.Top
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
                        contentDescription = "View details",
                        tint = Primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = stop.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = if (isCompleted) TextTertiary else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = if (isReordering) {
                        "Stop ${index + 1} of ${lastIndex + 1}"
                    } else {
                        "${formatClock(startTimeMinutes + scheduled.arrivalMinutesFromStart)}" +
                            " \u2013 " +
                            formatClock(startTimeMinutes + scheduled.departureMinutesFromStart)
                    },
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(7.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (isCompleted) {
                        Surface(shape = RoundedCornerShape(50), color = CompletedContentColor) {
                            Text(
                                text = "Done",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // The visit duration doubles as the control that opens
                    // the picker — the number you want to change is the
                    // thing you tap.
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isExpanded) PrimarySoft else SurfaceSoft,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded
                        )
                    ) {
                        Text(
                            text = "${stop.estimatedVisitMinutes} min here",
                            color = if (isExpanded) Primary else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    if (stop.isRecommended) {

                        Spacer(Modifier.width(6.dp))

                        Surface(shape = RoundedCornerShape(50), color = PrimarySoft) {
                            Text(
                                text = "Recommended",
                                color = Primary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {

                    Column {

                        Spacer(Modifier.height(9.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                            ItineraryPlanner.visitMinuteOptions.forEach { minutes ->

                                val isSelected = minutes == stop.estimatedVisitMinutes

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isSelected) Primary else SurfaceSoft)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onVisitMinutesChange(minutes) }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = minutes.toString(),
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder stop",
                    tint = if (isDragged) Primary else TextTertiary,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(stop.refId) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDragBy(amount.y)
                                },
                                onDragEnd = { onDragStop() },
                                onDragCancel = { onDragCancel() }
                            )
                        }
                        .padding(9.dp)
                )

                Spacer(Modifier.height(2.dp))

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
private fun TravelLegRow(
    minutes: Int,
    meters: Int,
    isApproximate: Boolean,
    travelMode: TravelMode,
    isPending: Boolean = false
) {

    val verb = when (travelMode) {
        TravelMode.WALKING -> "walk"
        TravelMode.DRIVING -> "drive"
        TravelMode.MOTORCYCLE -> "ride"
    }

    Row(
        modifier = Modifier.padding(start = 13.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Stub of the connector line, so the cards read as one continuous day
        // rather than a stack of unrelated rows.
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(26.dp)
                .background(Outline)
        )

        Spacer(Modifier.width(12.dp))

        Icon(
            imageVector = when (travelMode) {
                TravelMode.WALKING -> Icons.Default.DirectionsWalk
                TravelMode.DRIVING -> Icons.Default.DirectionsCar
                TravelMode.MOTORCYCLE -> Icons.Default.TwoWheeler
            },
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(14.dp)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = if (isPending) {
                "recalculating"
            } else {
                buildString {
                    if (isApproximate) append("~")
                    append(minutes)
                    append(" min ")
                    append(verb)
                    if (meters > 0) {
                        append(" \u00b7 ")
                        append(formatDistance(meters.toDouble()))
                    }
                }
            },
            fontSize = 11.5.sp,
            color = if (isPending) TextTertiary else TextSecondary
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

        } else if (uiState.itineraryStatus == "completed") {

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

            PrimaryButton(
                text = when {
                    uiState.completedStopIds.isNotEmpty() -> "Resume Trip"
                    else -> "Start Trip Now"
                },
                onClick = onStartTrip
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Itinerary") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    cursorColor = Primary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
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
                .heightIn(max = 520.dp)
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
                text = "Tap \u2139 to see details \u2022 tap + to add",
                fontSize = 11.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(12.dp))

            val availableSites = uiState.allSites.filter { it.documentId !in existingStopIds }
            val availablePlaces = uiState.allPlaces.filter { it.documentId !in existingStopIds }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                items(availableSites.size) { index ->
                    val site = availableSites[index]
                    AddStopRow(
                        title = site.siteName,
                        subtitle = site.location,
                        imageUrl = site.featuredImage,
                        onClick = { viewModel.addStopFromSite(site) },
                        onInfoClick = {
                            viewModel.openStopPreview(
                                refId = site.documentId,
                                refType = "site",
                                mode = StopPreviewMode.ADD_TO_ROUTE
                            )
                        }
                    )
                }

                items(availablePlaces.size) { index ->
                    val place = availablePlaces[index]
                    AddStopRow(
                        title = place.name,
                        subtitle = place.categoryLabel,
                        imageUrl = place.featuredImage,
                        onClick = { viewModel.addStopFromPlace(place) },
                        onInfoClick = {
                            viewModel.openStopPreview(
                                refId = place.documentId,
                                refType = "place",
                                mode = StopPreviewMode.ADD_TO_ROUTE
                            )
                        }
                    )
                }

                if (availableSites.isEmpty() && availablePlaces.isEmpty()) {
                    items(1) {
                        Text(
                            text = "Everything's already in this itinerary.",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
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

        PreviewInfoButton(
            onClick = onInfoClick,
            background = CardSurface
        )

        Spacer(Modifier.width(2.dp))

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = Primary,
            modifier = Modifier.size(18.dp)
        )
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
