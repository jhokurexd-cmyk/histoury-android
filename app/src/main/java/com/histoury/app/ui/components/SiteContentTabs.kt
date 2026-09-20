package com.histoury.app.ui.components

import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.Outline
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import com.histoury.app.theme.CardSurface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.SourceEntry
import com.histoury.app.data.model.StoryEntry
import com.histoury.app.data.model.TimelineEntry
import com.histoury.app.data.viewmodel.SiteDetailsTab
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

/**
 * A site's written content — overview, timeline, stories and sources.
 *
 * Shared between the Site Details page and the panel inside the AR camera,
 * because those two are meant to show the same thing. They were separate
 * before: the AR sheet had a short hand-written summary while the real
 * content lived here, so a visitor standing in front of the fort with the
 * model on screen got less than one sitting at home with the app open.
 *
 * Keeping one implementation is the only way they stay identical. Two
 * copies drift the first time a tab is added or a heading is reworded, and
 * the copy nobody is looking at is always the one that goes stale.
 */

/**
 * Prompts the visitor to use headphones before starting narration.
 */
@Composable
private fun HeadphoneHintDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Headset,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Better with headphones",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "For a better listening experience, we recommend using earphones or headphones.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary)
                ) {
                    Text(
                        text = "Continue",
                        color = OnColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    )
}

/**
 * Play/pause control for one section's narration.
 *
 * Absent rather than disabled when a section has no audio. A greyed-out
 * button on three quarters of the sites would read as something broken;
 * nothing at all reads as a feature that has not been filled in yet, which
 * is the truth.
 */
@Composable
private fun VoiceoverButton(
    section: String,
    url: String?,
    controller: VoiceoverController?
) {

    if (url.isNullOrBlank() || controller == null) return

    val isActive = controller.activeSection == section
    val isPlaying = isActive && controller.isPlaying
    val isLoading = controller.loadingSection == section

    var hintAccepted by rememberSaveable { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    if (showHint) {
        HeadphoneHintDialog(
            onContinue = {
                showHint = false
                hintAccepted = true
                controller.toggle(section, url)
            },
            onDismiss = { showHint = false }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(50))
            .background(if (isPlaying) Primary else PrimarySoft)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (!isActive && !hintAccepted) showHint = true
                else controller.toggle(section, url)
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .animateContentSize(animationSpec = tween(150)),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AnimatedContent(
            targetState = when {
                isLoading -> "loading"
                isPlaying -> "playing"
                else -> "idle"
            },
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(initialScale = 0.85f, animationSpec = tween(120))) togetherWith
                    (fadeOut(tween(100)) + scaleOut(targetScale = 0.85f, animationSpec = tween(100)))
            },
            label = "voiceoverButtonIcon"
        ) { state ->
            when (state) {
                "loading" -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Primary,
                    modifier = Modifier.size(13.dp)
                )
                "playing" -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Stop narration",
                    tint = OnColor,
                    modifier = Modifier.size(15.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Listen",
                    tint = Primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Text(
            text = when {
                isLoading -> "Loading"
                isPlaying -> "Stop"
                else -> "Listen"
            },
            color = if (isPlaying) OnColor else Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * The narration player shown under "About This Site".
 *
 * A full row rather than the small pill the stories use, because this is the
 * one a visitor is most likely to start — standing in front of a building
 * with the phone held up, reading is the harder option. Elapsed and total
 * time are shown because the first question anyone asks before starting
 * audio is how long it will take.
 */
@Composable
private fun VoiceoverPlayer(
    section: String,
    url: String?,
    controller: VoiceoverController?
) {

    if (url.isNullOrBlank() || controller == null) return

    val isActive = controller.activeSection == section
    val isPlaying = isActive && controller.isPlaying
    val isLoading = controller.loadingSection == section

    var hintAccepted by rememberSaveable { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    if (showHint) {
        HeadphoneHintDialog(
            onContinue = {
                showHint = false
                hintAccepted = true
                controller.toggle(section, url)
            },
            onDismiss = { showHint = false }
        )
    }

    // The whole row is no longer tappable. It used to be, which meant a
    // finger landing anywhere near the progress bar toggled playback instead
    // of seeking. Only the play button toggles now.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // Neutral, not a coral tint.
            //
            // Coral is the app's action colour, so a coral panel makes the
            // whole row look tappable and leaves the play button nothing to
            // stand out against. A grey well reads as a container, and lets
            // the one coral element in it be the control.
            .background(SurfaceSoft)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val playButtonInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .size(40.dp)
                .pressScale(playButtonInteractionSource)
                .clip(CircleShape)
                .background(PrimarySoft)
                .clickable(
                    interactionSource = playButtonInteractionSource,
                    indication = null
                ) {
                    if (!isActive && !hintAccepted) showHint = true
                    else controller.toggle(section, url)
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    isLoading -> "loading"
                    isPlaying -> "playing"
                    else -> "idle"
                },
                transitionSpec = {
                    (fadeIn(tween(120)) + scaleIn(initialScale = 0.85f, animationSpec = tween(120))) togetherWith
                        (fadeOut(tween(100)) + scaleOut(targetScale = 0.85f, animationSpec = tween(100)))
                },
                label = "voiceoverPlayerIcon"
            ) { state ->
                when (state) {
                    "loading" -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    "playing" -> Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause narration",
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play narration",
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = "Listen to the historical narration",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(Modifier.height(7.dp))

            // Drawn rather than a Slider: this is a progress indicator, not
            // a control. A Slider invites scrubbing, and MediaPlayer seeking
            // on a streamed file is unreliable enough that offering it would
            // promise something the playback cannot deliver.
            // Taller hit area than the visible bar. A 3dp target is far
            // below the ~48dp minimum for a finger, so the bar is drawn thin
            // inside a box that is comfortable to hit.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .pointerInput(section, isActive) {
                        if (!isActive) return@pointerInput
                        detectTapGestures { offset ->
                            controller.seekTo(offset.x / size.width.toFloat())
                        }
                    }
                    .pointerInput(section, isActive) {
                        if (!isActive) return@pointerInput
                        detectHorizontalDragGestures { change, _ ->
                            controller.seekTo(change.position.x / size.width.toFloat())
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {

            // NOT animated: this box is also the drag-to-seek target
            // (detectHorizontalDragGestures above). Smoothing the displayed
            // position would lag a few frames behind the finger during an
            // active drag, which feels wrong for direct manipulation - the
            // one place Emil's own rules call for skipping animation.
            val displayedProgress = if (isActive) controller.progress else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    // A neutral track, so the coral is only the part that has
                    // played. A coral-on-coral bar makes elapsed and
                    // remaining hard to tell apart at a glance.
                    .background(Outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Primary)
                )
            }

            // The knob sits in its own zero-height box over the track so it
            // can overhang without being clipped by the track's rounded
            // corners. It marks where a drag picks up from — without it the
            // bar reads as decoration rather than something to grab.
            if (isActive) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (maxWidth * displayedProgress) - 5.dp,
                                y = (-7).dp
                            )
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Primary)
                    )
                }
            }
            }

            Spacer(Modifier.height(5.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatClock(if (isActive) controller.positionMs else 0),
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (controller.durationMs > 0 && isActive) {
                        formatClock(controller.durationMs)
                    } else {
                        "--:--"
                    },
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SiteContentTabRow(
    selectedTab: SiteDetailsTab,
    onTabSelected: (SiteDetailsTab) -> Unit,
    modifier: Modifier = Modifier
) {

    val tabs = listOf(
        SiteDetailsTab.OVERVIEW to "Overview",
        SiteDetailsTab.TIMELINE to "Timeline",
        SiteDetailsTab.STORIES to "Stories",
        SiteDetailsTab.SOURCES to "Sources"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            // Same reasoning as the player: a neutral container makes the
            // selected pill the only coral thing in the row, which is what
            // makes it read as selected rather than as one of four tints.
            .background(SurfaceSoft)
            .padding(4.dp)
    ) {

        tabs.forEach { (tab, label) ->

            val isSelected = tab == selectedTab

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Primary else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTabSelected(tab)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    // OnColor rather than a literal white: the pill behind it
                    // is the brand colour in both themes, so its label has to
                    // be the matching on-brand ink in both too.
                    color = if (isSelected) OnColor else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Renders whichever tab is selected.
 *
 * One entry point rather than four, so a caller cannot accidentally support
 * three tabs and leave the fourth rendering nothing.
 */
@Composable
fun SiteContentTabBody(
    selectedTab: SiteDetailsTab,
    content: SiteContent?,
    fallbackOverview: String,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Optional, so a caller that does not want narration — a preview, a
     * test — simply passes nothing and the controls disappear rather than
     * the component needing a second variant.
     */
    voiceover: VoiceoverController? = null
) {

    Column(modifier = modifier) {

        when (selectedTab) {

            // The site's own short description stands in when the content
            // document has no overview written yet — better a teaser than an
            // empty tab on the screen a visitor opened to read something.
            SiteDetailsTab.OVERVIEW -> OverviewTabContent(
                overview = content?.overview?.ifBlank { fallbackOverview } ?: fallbackOverview,
                quote = content?.quote.orEmpty(),
                audioUrl = content?.audioUrls?.get("overview"),
                voiceover = voiceover
            )

            SiteDetailsTab.TIMELINE -> TimelineTabContent(content?.timeline ?: emptyList())

            SiteDetailsTab.STORIES -> StoriesTabContent(
                entries = content?.stories ?: emptyList(),
                audioUrls = content?.audioUrls ?: emptyMap(),
                voiceover = voiceover
            )

            SiteDetailsTab.SOURCES -> SourcesTabContent(
                entries = content?.sources ?: emptyList(),
                onSourceClick = onSourceClick
            )
        }
    }
}

@Composable
private fun OverviewTabContent(
    overview: String,
    quote: String = "",
    audioUrl: String? = null,
    voiceover: VoiceoverController? = null
) {

    // Collapsed by default. A full site history runs several hundred words,
    // and an unbroken wall of text pushes the AR button — the thing the
    // screen exists to offer — off the bottom of the phone.
    var expanded by remember(overview) { mutableStateOf(false) }

    Column {

        Text(
            text = "About This Site",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.height(12.dp))

        VoiceoverPlayer("overview", audioUrl, voiceover)

        if (!audioUrl.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
        }

        if (quote.isNotBlank()) {

            // Absent rather than empty when no quote is written. A blank
            // tinted panel would read as content that failed to load.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimarySoft)
                    .padding(14.dp)
            ) {

                Text(
                    text = "\u201C",
                    color = Primary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    // A quotation mark's glyph sits high in its line box, so
                    // left alone it floats well above the text it opens.
                    modifier = Modifier
                        .offset(y = (-10).dp)
                        .height(24.dp)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = quote,
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = overview.ifBlank { "No overview available yet." },
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_OVERVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize(animationSpec = tween(220))
        )

        // Only offered when there is actually more to read. Showing it on a
        // two-line overview would be a control that does nothing.
        if (overview.length > COLLAPSED_OVERVIEW_CHARS) {

            Spacer(Modifier.height(8.dp))

            val chevronRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(200),
                label = "chevronRotation"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        expanded = !expanded
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    }
}

/**
 * Where the overview is cut when collapsed.
 *
 * The character count is a separate threshold from the line count on
 * purpose: lines depend on the device's width and font scale, so it cannot
 * be used to decide whether the control is needed at all.
 */
private const val COLLAPSED_OVERVIEW_LINES = 7
private const val COLLAPSED_OVERVIEW_CHARS = 420

@Composable
private fun TimelineTabContent(entries: List<TimelineEntry>) {

    if (entries.isEmpty()) {
        EmptyTabState(message = "No timeline entries available yet.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        entries.forEach { entry ->

            Row {

                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Primary)
                )

                Spacer(Modifier.width(12.dp))

                Column {

                    Text(
                        text = entry.year.ifBlank { "Undated" },
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = entry.event,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StoriesTabContent(
    entries: List<StoryEntry>,
    audioUrls: Map<String, String> = emptyMap(),
    voiceover: VoiceoverController? = null
) {

    if (entries.isEmpty()) {
        EmptyTabState(message = "No stories available yet.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        entries.forEachIndexed { index, entry ->

            Column {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = entry.title.ifBlank { "Untitled Story" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // Keyed the way the Cloud Function writes it: story_0,
                    // story_1. Index-based, so reordering stories in the
                    // admin panel would pair the wrong audio with the wrong
                    // text — worth knowing before that feature is added.
                    VoiceoverButton("story_$index", audioUrls["story_$index"], voiceover)
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = entry.content,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun SourcesTabContent(
    entries: List<SourceEntry>,
    onSourceClick: (String) -> Unit
) {

    if (entries.isEmpty()) {
        EmptyTabState(message = "No sources listed yet.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        entries.forEach { entry ->

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSourceClick(entry.url)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = entry.title.ifBlank { entry.url },
                    color = Primary,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTabState(message: String) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}
