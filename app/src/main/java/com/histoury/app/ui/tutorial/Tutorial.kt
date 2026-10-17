package com.histoury.app.ui.tutorial

import com.histoury.app.theme.styledElevation
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// =============================================================================
// First-run spotlight tutorials.
//
// How it fits together:
//
//  - A screen marks the controls it wants to explain with
//    Modifier.tutorialTarget("some_key").
//  - The same screen declares its tour with TutorialTour("screen_id", steps).
//    The first time the visitor opens that screen, the tour runs: the page
//    is dimmed except for one control at a time, with a short bubble saying
//    what it is and what it does. Once shown, it never runs on its own again.
//  - TutorialOverlay, placed once at the root of the app (MainActivity),
//    draws whichever tour is running on top of everything.
//  - Menu > "Show app tutorials again" clears the "already seen" list.
// =============================================================================

/**
 * One stop of a tour.
 *
 * [target] is the key given to [tutorialTarget] on the control to spotlight.
 * Null means a general note shown in the middle of the screen with nothing
 * highlighted (good for a "Welcome to…" opener).
 */
data class TutorialStep(
    val target: String?,
    val title: String,
    val body: String
)

/** Which tours this phone has already shown. */
object TutorialPreference {

    private const val PREFS = "histoury_tutorials"
    private const val KEY_SEEN = "seen_tours"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSeen(context: Context, tourId: String): Boolean =
        tourId in prefs(context).getStringSet(KEY_SEEN, emptySet()).orEmpty()

    fun markSeen(context: Context, tourId: String) {
        val seen = prefs(context).getStringSet(KEY_SEEN, emptySet()).orEmpty()
        if (tourId in seen) return
        prefs(context).edit().putStringSet(KEY_SEEN, seen + tourId).apply()
    }

    /** Menu > "Show app tutorials again": every tour runs again on its next visit. */
    fun resetAll(context: Context) {
        prefs(context).edit().remove(KEY_SEEN).apply()
    }
}

/** The tour currently on screen (at most one at a time), and where the targets are. */
object Tutorial {

    class ActiveTour(val id: String, val steps: List<TutorialStep>) {
        var index by mutableIntStateOf(0)
        val step: TutorialStep get() = steps[index]
        val isLast: Boolean get() = index == steps.lastIndex
    }

    /** Where each marked control is, in window coordinates. */
    internal val targets = mutableStateMapOf<String, Rect>()

    var activeTour by mutableStateOf<ActiveTour?>(null)
        private set

    internal fun start(id: String, steps: List<TutorialStep>) {
        if (steps.isNotEmpty()) activeTour = ActiveTour(id, steps)
    }

    fun next() {
        val tour = activeTour ?: return
        if (tour.isLast) finish() else tour.index++
    }

    fun back() {
        val tour = activeTour ?: return
        if (tour.index > 0) tour.index--
    }

    fun finish() {
        activeTour = null
    }
}

/** Marks this control so a tour can spotlight it under [key]. */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
    DisposableEffect(key) {
        onDispose { Tutorial.targets.remove(key) }
    }
    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            if (bounds.width > 0f && bounds.height > 0f) {
                Tutorial.targets[key] = bounds
            }
        }
    }
}

/**
 * Runs [steps] the first time this screen is opened on this phone.
 *
 * Waits a moment for the screen to settle and its targets to be laid out,
 * drops any step whose control isn't on screen (e.g. a button that only
 * exists in another state), and waits its turn if another tour is running.
 * [enabled] lets a screen hold the tour back, e.g. until its content has
 * loaded so the spotlit controls actually exist.
 */
@Composable
fun TutorialTour(
    id: String,
    steps: List<TutorialStep>,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val currentSteps by rememberUpdatedState(steps)
    val windowSize = LocalWindowInfo.current.containerSize

    LaunchedEffect(id, enabled) {
        if (!enabled || TutorialPreference.isSeen(context, id)) return@LaunchedEffect

        delay(SETTLE_MS)

        val wanted = currentSteps.mapNotNull { it.target }
        withTimeoutOrNull(TARGET_WAIT_MS) {
            snapshotFlow { wanted.all { it in Tutorial.targets } }.first { it }
        }

        // One tour at a time: wait for another screen's tour to finish.
        while (Tutorial.activeTour != null) delay(300)

        // A control that's scrolled off screen (or not shown in this state)
        // can't be spotlit, so its step is left out.
        fun onScreen(rect: Rect): Boolean {
            val center = rect.center
            return center.x in 0f..windowSize.width.toFloat() &&
                center.y in 0f..windowSize.height.toFloat()
        }
        val available = currentSteps.filter { step ->
            val target = step.target ?: return@filter true
            Tutorial.targets[target]?.let(::onScreen) == true
        }
        // Only general notes left: the screen's actual controls aren't
        // there yet, so try again on a later visit rather than now.
        if (available.none { it.target != null } && currentSteps.any { it.target != null }) {
            return@LaunchedEffect
        }

        // Marked as soon as it's shown, so leaving halfway (or the app being
        // closed) doesn't make it run again every visit.
        TutorialPreference.markSeen(context, id)
        Tutorial.start(id, available)
    }

    // Leaving the screen ends its tour; the spotlit controls are gone.
    DisposableEffect(id) {
        onDispose {
            if (Tutorial.activeTour?.id == id) Tutorial.finish()
        }
    }
}

private const val SETTLE_MS = 650L
private const val TARGET_WAIT_MS = 2_500L

private val SpotPadding = 8.dp
private val BubbleGap = 14.dp
private val ScreenMargin = 16.dp

/**
 * Draws the running tour over the whole app. Place once, as the last child
 * of the app's root Box so it covers everything (including the bottom bar).
 */
@Composable
fun TutorialOverlay() {
    val tour = Tutorial.activeTour ?: return
    val step = tour.step
    val target = step.target?.let { Tutorial.targets[it] }

    val density = LocalDensity.current
    val padPx = with(density) { SpotPadding.toPx() }

    BackHandler { Tutorial.finish() }

    BoxWithConstraints(Modifier.fillMaxSize()) {

        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // With no target, the "hole" shrinks to a point in the middle, so
        // moving between a centered note and a spotlit control animates.
        val hole = target?.let {
            Rect(it.left - padPx, it.top - padPx, it.right + padPx, it.bottom + padPx)
        } ?: Rect(Offset(screen.width / 2f, screen.height / 2f), Size.Zero)

        val animatedHole by animateRectAsState(
            targetValue = hole,
            animationSpec = tween(durationMillis = 320),
            label = "tutorialHole"
        )

        val appear by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250),
            label = "tutorialAppear"
        )

        val ringPx = with(density) { 2.dp.toPx() }
        // Theme colors are read here: inside drawBehind isn't a composable
        // context, and the palette's getters are composable.
        val ringColor = Primary
        val cornerPx = with(density) { 16.dp.toPx() }

        // The dimmed page with a clear window over the control. Drawn
        // offscreen so BlendMode.Clear punches through the scrim only.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = appear
                }
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.72f))
                    if (animatedHole.width > 1f) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = animatedHole.topLeft,
                            size = animatedHole.size,
                            cornerRadius = CornerRadius(cornerPx),
                            blendMode = BlendMode.Clear
                        )
                        drawRoundRect(
                            color = ringColor,
                            topLeft = animatedHole.topLeft,
                            size = animatedHole.size,
                            cornerRadius = CornerRadius(cornerPx),
                            style = Stroke(width = ringPx)
                        )
                    }
                }
                // Swallows every touch so nothing underneath is pressed by
                // accident; a tap anywhere moves the tour on.
                .pointerInput(tour) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var up = false
                        while (!up) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            up = event.changes.all { !it.pressed }
                        }
                        Tutorial.next()
                    }
                }
        )

        BubbleLayout(
            target = target?.let { hole },
            screen = screen,
            modifier = Modifier.fillMaxSize()
        ) {
            TutorialBubble(tour)
        }
    }
}

/** Puts the bubble below the spotlight (or above it, if it's low on screen), clamped on screen. */
@Composable
private fun BubbleLayout(
    target: Rect?,
    screen: Size,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val marginPx = ScreenMargin.roundToPx()
        val gapPx = BubbleGap.roundToPx()
        val topInset = insets.getTop(density) + marginPx
        val bottomInset = insets.getBottom(density) + marginPx

        val maxWidth = (constraints.maxWidth - marginPx * 2).coerceAtLeast(0)
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth)
        )

        val x = if (target == null) {
            (constraints.maxWidth - placeable.width) / 2
        } else {
            (target.center.x - placeable.width / 2f).toInt()
                .coerceIn(marginPx, constraints.maxWidth - marginPx - placeable.width)
        }

        val rawY = when {
            target == null -> (constraints.maxHeight - placeable.height) / 2
            target.center.y < screen.height / 2f -> target.bottom.toInt() + gapPx
            else -> target.top.toInt() - gapPx - placeable.height
        }
        val y = rawY.coerceIn(
            topInset,
            (constraints.maxHeight - bottomInset - placeable.height).coerceAtLeast(topInset)
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(x, y)
        }
    }
}

@Composable
private fun TutorialBubble(tour: Tutorial.ActiveTour) {
    val step = tour.step
    val total = tour.steps.size

    Surface(
        modifier = Modifier.widthIn(max = 340.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        shadowElevation = styledElevation(8.dp)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {

            if (total > 1) {
                Text(
                    text = "STEP ${tour.index + 1} OF $total",
                    color = Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = step.title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = step.body,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!tour.isLast) {
                    BubbleTextButton("Skip tour") { Tutorial.finish() }
                }

                Spacer(Modifier.weight(1f))

                if (total > 1) {
                    ProgressDots(count = total, current = tour.index)
                    Spacer(Modifier.width(6.dp))
                }

                if (tour.index > 0) {
                    BubbleTextButton("Back") { Tutorial.back() }
                }

                BubblePrimaryButton(if (tour.isLast) "Got it" else "Next") { Tutorial.next() }
            }
        }
    }
}

@Composable
private fun ProgressDots(count: Int, current: Int) {
    // Past six steps the dots stop being readable; the step counter covers it.
    if (count > 6) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Primary else PrimarySoft)
            )
        }
    }
}

@Composable
private fun BubbleTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BubblePrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(label, color = OnColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
