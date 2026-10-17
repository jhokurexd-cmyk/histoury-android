package com.histoury.app.ui.screens.gallery

import com.histoury.app.theme.styledElevation
import com.histoury.app.theme.softShadow
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.navigation.navigateSafely
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.histoury.app.theme.Success
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.histoury.app.data.model.Gallery
import com.histoury.app.data.viewmodel.GalleryViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.AccentGold
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.BottomNavBarInset
import com.histoury.app.ui.components.BottomNavScaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.histoury.app.ui.components.ImageLightbox

/**
 * The Gallery tab: photos captured during AR experiences, per user.
 *
 * Reads the existing `gallery` collection. Nothing in the app writes to it
 * yet (photo capture ships with the AR feature), so real users will see the
 * empty state until then — but the tab is fully wired, not a placeholder.
 */
@Composable
fun GalleryScreen(
    navController: NavHostController,
    galleryViewModel: GalleryViewModel = viewModel()
) {

    val uiState by galleryViewModel.uiState.collectAsState()

    // Which photo the lightbox is showing, or null when it's closed.
    // Held as an index rather than a URL so paging between photos inside the
    // lightbox stays in step with the grid's own order.
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current

    // The photos a pending permission grant was going to save.
    //
    // Only ever non-empty on Android 9 and below, where writing to shared
    // storage still needs asking. Held here because the request is
    // asynchronous: the visitor's answer comes back long after the tap that
    // needed it, and without this the screen would know it had been granted
    // permission but not what it had wanted the permission for.
    var pendingSave by remember { mutableStateOf<List<Gallery>>(emptyList()) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSave.isNotEmpty()) {
            galleryViewModel.saveToDevice(context, pendingSave)
        } else if (!granted && pendingSave.isNotEmpty()) {
            // Declined or dismissed: say why nothing happened.
            galleryViewModel.onStoragePermissionDenied()
        }
        pendingSave = emptyList()
    }

    // Every save goes through here rather than straight to the view model,
    // so the permission question is asked in exactly one place however the
    // save was started.
    val requestSave: (List<Gallery>) -> Unit = { photos ->
        if (photos.isNotEmpty()) {
            if (galleryViewModel.requiresLegacyStoragePermission() &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingSave = photos
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                galleryViewModel.saveToDevice(context, photos)
            }
        }
    }

    LaunchedEffect(Unit) {
        galleryViewModel.loadGallery()
    }

    // Confirmations clear themselves. A save or a delete is a small, obvious
    // action and its receipt should not need dismissing.
    uiState.message?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2600)
            galleryViewModel.clearMessage()
        }
    }

    // Back leaves selection mode before it leaves the screen — otherwise a
    // visitor who long-pressed by accident has to either pick something or
    // navigate away to escape it.
    BackHandler(enabled = uiState.selectionMode) {
        galleryViewModel.clearSelection()
    }

    // Suppressed during selection, where a tap means "pick this one" rather
    // than "open this one".
    if (!uiState.selectionMode) {
        lightboxIndex?.let { index ->
            ImageLightbox(
                images = uiState.photos.map { it.imageUrl },
                startIndex = index,
                onDismiss = { lightboxIndex = null }
            )
        }
    }

    TutorialTour(
        id = "gallery",
        steps = listOf(
            TutorialStep(
                target = null,
                title = "Your AR photos",
                body = "Photos you take in AR at historical sites are kept here. Tap one to view it full screen."
            ),
            TutorialStep(
                target = null,
                title = "Select and delete",
                body = "Press and hold a photo to start selecting, tap others to add them, then delete them together."
            )
        )
    )

    BottomNavScaffold(
        navController = navController,
        containerColor = SurfaceSoft
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSoft)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 6.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Top-level tab: no back arrow (Back already returns to
                    // Home), same header as the other tabs — title on the
                    // left, menu on the right.
                    Text(
                        text = "My Gallery",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .softShadow(elevation = 2.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                navController.navigateSafely(Routes.Menu.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PrimarySoft,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Column {
                            Text(
                                text = uiState.photos.size.toString(),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Photos captured",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            when {

                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                uiState.photos.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PrimarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "No photos yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Photos you capture during AR experiences at historical sites will appear here.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 12.dp + BottomNavBarInset
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {

                        items(uiState.photos, key = { it.id }) { photo ->

                            GalleryPhotoTile(
                                photo = photo,
                                // Falls back to a neutral label rather than
                                // the raw id: a visitor should never be shown
                                // a Firestore key, even when a lookup fails.
                                siteName = uiState.siteNames[photo.siteId]
                                    ?: "AR Capture",
                                selectionMode = uiState.selectionMode,
                                isSelected = photo.id in uiState.selectedIds,
                                onClick = {
                                    if (uiState.selectionMode) {
                                        galleryViewModel.toggleSelection(photo.id)
                                    } else {
                                        lightboxIndex = uiState.photos.indexOf(photo)
                                    }
                                },
                                onLongClick = {
                                    // Closed, not just hidden. The lightbox
                                    // is suppressed while selecting rather
                                    // than unmounted, so leaving it open
                                    // here would pop it back up on an old
                                    // photo the moment the selection was
                                    // cancelled.
                                    lightboxIndex = null
                                    galleryViewModel.startSelection(photo.id)
                                },
                                downloadState = when {
                                    photo.id in uiState.downloadingIds -> PhotoDownloadState.DOWNLOADING
                                    photo.id in uiState.justDownloadedIds -> PhotoDownloadState.JUST_SAVED
                                    photo.id in uiState.downloadedIds -> PhotoDownloadState.SAVED
                                    else -> PhotoDownloadState.IDLE
                                },
                                onJustSavedShown = { galleryViewModel.clearJustDownloaded(photo.id) },
                                onSaveToDevice = { requestSave(listOf(photo)) }
                            )
                        }
                    }
                }
            }
        }

        // Selection action bar, above the grid rather than replacing the
        // header. Sitting at the bottom puts Save and Delete within a
        // thumb's reach on a tall phone, which is where a destructive action
        // is least likely to be hit by accident on the way to something
        // else.
        AnimatedVisibility(
            visible = uiState.selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomNavBarInset + 12.dp, start = 16.dp, end = 16.dp)
        ) {
            GallerySelectionBar(
                selectedCount = uiState.selectedIds.size,
                totalCount = uiState.photos.size,
                isWorking = uiState.isWorking,
                onSelectAll = { galleryViewModel.selectAll() },
                onSave = { requestSave(galleryViewModel.selectedPhotos()) },
                onDelete = { showDeleteConfirm = true },
                onCancel = { galleryViewModel.clearSelection() }
            )
        }

        uiState.message?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TextPrimary.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        }
    }

    // Deleting is the one action here that cannot be undone: the Firestore
    // record and the file in Storage both go, and Histoury is the only copy
    // unless the visitor also saved to their device. So it asks first, and
    // says how many.
    if (showDeleteConfirm) {

        val count = uiState.selectedIds.size

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = if (count == 1) "Delete this photo?" else "Delete $count photos?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This removes " +
                        (if (count == 1) "it" else "them") +
                        " from your Histoury gallery for good. Photos you've " +
                        "already saved to your device aren't affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        galleryViewModel.deleteSelected(galleryViewModel.selectedPhotos())
                    }
                ) {
                    Text("Delete", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * The bar that appears while photos are picked out.
 *
 * Cancel is a plain X rather than a labelled button, and Delete is the only
 * thing tinted — in a row of equally weighted controls the destructive one
 * gets chosen by accident, and here it is the only one that cannot be
 * undone.
 */
@Composable
private fun GallerySelectionBar(
    selectedCount: Int,
    totalCount: Int,
    isWorking: Boolean,
    onSelectAll: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {

    val enabled = selectedCount > 0 && !isWorking

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        shadowElevation = styledElevation(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel selection",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            Text(
                text = "$selectedCount selected",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (isWorking) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            // Hidden rather than disabled once everything is already picked:
            // a control that cannot change anything is just something else
            // to read.
            if (selectedCount < totalCount && !isWorking) {
                Text(
                    text = "Select all",
                    color = Primary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSelectAll
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onSave
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Save to device",
                    tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(19.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onDelete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = if (enabled) Primary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPhotoTile(
    photo: Gallery,
    siteName: String,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    downloadState: PhotoDownloadState,
    onJustSavedShown: () -> Unit,
    onSaveToDevice: () -> Unit
) {

    // The tick stays up long enough to register, then the corner clears.
    if (downloadState == PhotoDownloadState.JUST_SAVED) {
        LaunchedEffect(photo.id) {
            kotlinx.coroutines.delay(1400)
            onJustSavedShown()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSoft)
            // combinedClickable rather than clickable, for the long press
            // that starts a selection. It keeps the ordinary tap behaviour
            // and the accessibility semantics that come with it, which a
            // hand-rolled pointerInput would have thrown away.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {

        AsyncImage(
            model = photo.imageUrl,
            contentDescription = siteName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Picked photos are dimmed as well as ticked. The tick alone is a
        // 22dp mark on a busy photograph; dimming is what makes a selection
        // readable at a glance across a three-column grid.
        if (selectionMode && isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Primary.copy(alpha = 0.32f))
            )
        }

        // "AR" badge — every gallery photo comes from an AR capture.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(AccentGold),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AR",
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Top-right corner does double duty: the selection tick while
        // picking, and otherwise the per-photo save. They can share the spot
        // because they are never both meaningful at once — during a
        // selection, saving is the action bar's job.
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Primary else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = Color.White.copy(alpha = if (isSelected) 1f else 0.55f),
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            // Download -> spinner while saving -> a tick -> gone. Once a
            // copy is on the phone there's nothing left for the button to
            // do, so it stays hidden until that copy is deleted.
            AnimatedVisibility(
                visible = downloadState != PhotoDownloadState.SAVED,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                val isIdle = downloadState == PhotoDownloadState.IDLE
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (downloadState == PhotoDownloadState.JUST_SAVED) {
                                Success
                            } else {
                                Color.Black.copy(alpha = 0.45f)
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            // Only the idle button does anything; a tap while
                            // saving can't start a second download.
                            enabled = isIdle,
                            onClickLabel = "Save to device",
                            onClick = onSaveToDevice
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = downloadState,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f)) togetherWith
                                fadeOut(tween(100))
                        },
                        label = "photoDownloadState"
                    ) { state ->
                        when (state) {
                            PhotoDownloadState.DOWNLOADING -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .size(14.dp)
                                    .semantics { contentDescription = "Saving photo" }
                            )
                            PhotoDownloadState.JUST_SAVED, PhotoDownloadState.SAVED -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Saved to device",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            PhotoDownloadState.IDLE -> Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Save to device",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom scrim with site name + capture date, per the Figma.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {

            Text(
                text = siteName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = formatCaptureDate(photo.capturedAt),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

private fun formatCaptureDate(rawDate: Any?): String {

    val timestamp = rawDate as? Timestamp ?: return "--"

    val calendar = java.util.Calendar.getInstance()

    calendar.time = timestamp.toDate()

    val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val month = months[calendar.get(java.util.Calendar.MONTH)]

    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    val year = calendar.get(java.util.Calendar.YEAR)

    return "$month $day, $year"
}

enum class PhotoDownloadState { IDLE, DOWNLOADING, JUST_SAVED, SAVED }
