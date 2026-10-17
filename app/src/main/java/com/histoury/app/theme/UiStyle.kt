package com.histoury.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of the app's surfaces, chosen in Menu > Theme.
 *
 * Separate from [ThemeMode] (light / dark / system) on purpose: style and
 * brightness are two different questions, so Neumorphism works in both
 * light and dark mode instead of being a fourth "mode".
 *
 *  - [DEFAULT]: white cards with ordinary drop shadows on a light page.
 *  - [NEUMORPHIC]: "soft UI". Cards are the SAME colour as the page and
 *    are shaped only by light: a highlight on the top-left edge and a
 *    shadow on the bottom-right, as if pressed out of the page. Inputs
 *    (the search bar) are the opposite, pressed IN.
 *  - [GLASS]: glassmorphism. The page is a soft wash of colour and the
 *    cards are see-through frosted panels on top of it, each with a thin
 *    bright rim where light catches the edge of the glass.
 */
enum class UiStyle {
    DEFAULT,
    NEUMORPHIC,
    GLASS;

    /** Label shown next to Theme in the menu. */
    val label: String
        get() = when (this) {
            DEFAULT -> "Default"
            NEUMORPHIC -> "Neumorphism"
            GLASS -> "Glassmorphism"
        }

    /** One line under each option in the Theme sheet. */
    val description: String
        get() = when (this) {
            DEFAULT -> "Clean white cards with soft shadows."
            NEUMORPHIC -> "Soft, raised surfaces that blend into the page."
            GLASS -> "See-through frosted cards over a soft colour wash."
        }

    companion object {
        fun fromStoredValue(value: String?): UiStyle =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Filled by [HistouryTheme]; read through [isNeumorphic]. */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.DEFAULT }

val isNeumorphic: Boolean
    @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.NEUMORPHIC

val isGlass: Boolean
    @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.GLASS

/**
 * Fill for bottom sheets and dialogs. The same as [CardSurface] in Default
 * and Neumorphism. In Glassmorphism cards are see-through, but a sheet
 * sliding up over a busy screen has to stay readable, so sheets use the
 * almost-solid frosted colour instead.
 */
val SheetSurface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.let {
        if (LocalUiStyle.current == UiStyle.GLASS) it.surfaceRaised else it.surface
    }

/**
 * Elevation for a Material `Surface(shadowElevation = …)`. Unchanged in
 * Default and Neumorphism; 0 in Glassmorphism, because a drop shadow
 * under a see-through card shows THROUGH it as a dark smudge.
 */
@Composable
@ReadOnlyComposable
fun styledElevation(elevation: Dp): Dp =
    if (LocalUiStyle.current == UiStyle.GLASS) 0.dp else elevation

// ---------------------------------------------------------------------------
// Neumorphic palettes
// ---------------------------------------------------------------------------
//
// Built from the normal palettes, so the coral, the text inks and the
// status colours stay exactly the same. Only the surfaces change: page,
// card and raised card become one colour, because in soft UI an element
// is told apart from the page by its light and shadow, not by its fill.

private val NeuLightBase = Color(0xFFE4E8EE)
private val NeuDarkBase = Color(0xFF2A2D33)

val NeumorphicLightColors = LightHistouryColors.copy(
    background = NeuLightBase,
    surface = NeuLightBase,
    surfaceRaised = NeuLightBase,
    // Most screens paint their page with surfaceSoft, so it has to match
    // the cards too, or the cards would sit on a different grey.
    surfaceSoft = NeuLightBase,
    divider = Color(0xFFD3D8E0),
    outline = Color(0xFFCDD3DC),
    // The bottom bar is glass in Default. Here it is a solid soft panel.
    glassTop = NeuLightBase,
    glassBottom = NeuLightBase,
    glassSheen = Color.Transparent,
    glassRim = Color.White.copy(alpha = 0.7f),
    glassEdge = Color.Transparent,
    glassShadow = Color(0xFF8E9BB0)
)

val NeumorphicDarkColors = DarkHistouryColors.copy(
    background = NeuDarkBase,
    surface = NeuDarkBase,
    surfaceRaised = NeuDarkBase,
    surfaceSoft = NeuDarkBase,
    divider = Color(0xFF363A41),
    outline = Color(0xFF3B3F47),
    glassTop = NeuDarkBase,
    glassBottom = NeuDarkBase,
    glassSheen = Color.Transparent,
    glassRim = Color.White.copy(alpha = 0.06f),
    glassEdge = Color.Transparent,
    glassShadow = Color.Black
)

// ---------------------------------------------------------------------------
// Glassmorphism palettes
// ---------------------------------------------------------------------------
//
// The page itself is transparent: MainActivity paints the colour wash
// ([styleBackdrop]) once behind every screen, and each screen's own
// background lets it show through. Cards are white at partial opacity,
// which reads as frosted glass over the wash. Text inks, coral and status
// colours are the normal ones, so contrast stays where it was.

private val GlassInkLine = Color(0xFF2A2333)

val GlassLightColors = LightHistouryColors.copy(
    background = Color.Transparent,
    surface = Color.White.copy(alpha = 0.58f),
    // Sheets and dialogs (see SheetSurface): nearly solid frosted white.
    surfaceRaised = Color(0xFFFAF8FD).copy(alpha = 0.97f),
    surfaceSoft = Color.White.copy(alpha = 0.28f),
    divider = GlassInkLine.copy(alpha = 0.10f),
    // Card borders become the glass rim.
    outline = Color.White.copy(alpha = 0.75f),
    glassTop = Color.White.copy(alpha = 0.66f),
    glassBottom = Color.White.copy(alpha = 0.44f),
    glassSheen = Color.White.copy(alpha = 0.45f),
    glassRim = Color.White.copy(alpha = 0.95f),
    glassEdge = Color.White.copy(alpha = 0.35f),
    glassShadow = Color(0xFF5B4B7A)
)

val GlassDarkColors = DarkHistouryColors.copy(
    background = Color.Transparent,
    surface = Color.White.copy(alpha = 0.08f),
    surfaceRaised = Color(0xFF231F2E).copy(alpha = 0.97f),
    surfaceSoft = Color.White.copy(alpha = 0.04f),
    divider = Color.White.copy(alpha = 0.09f),
    outline = Color.White.copy(alpha = 0.16f),
    glassTop = Color.White.copy(alpha = 0.13f),
    glassBottom = Color.White.copy(alpha = 0.06f),
    glassSheen = Color.White.copy(alpha = 0.08f),
    glassRim = Color.White.copy(alpha = 0.24f),
    glassEdge = Color.White.copy(alpha = 0.05f),
    glassShadow = Color.Black
)

/**
 * The colour wash behind every screen in Glassmorphism: a calm base with
 * a few large, very soft colour blobs (coral for the brand, violet and
 * teal for depth). Glass needs something behind it to be glass; on a flat
 * page a see-through card just looks grey.
 *
 * Does nothing in the other styles. Used once, on the root in MainActivity
 * (and in the Theme sheet's preview).
 */
fun Modifier.styleBackdrop(): Modifier = composed {
    if (LocalUiStyle.current != UiStyle.GLASS) return@composed this
    val dark = LocalHistouryColors.current.isDark
    this.drawBehind { drawGlassWash(dark) }
}

private fun DrawScope.drawGlassWash(dark: Boolean) {

    val base = if (dark) Color(0xFF15131C) else Color(0xFFF4F1F8)
    drawRect(base)

    val w = size.width
    val h = size.height
    val r = maxOf(w, h)
    val strength = if (dark) 0.38f else 0.55f

    fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(color.copy(alpha = strength), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
    }

    blob(Color(0xFFFF8A80), w * 0.05f, h * 0.08f, r * 0.55f)   // coral, top-left
    blob(Color(0xFFB39DFF), w * 1.0f, h * 0.35f, r * 0.55f)    // violet, right
    blob(Color(0xFF80DEEA), w * 0.15f, h * 0.78f, r * 0.50f)   // teal, lower-left
    blob(Color(0xFFFFCC80), w * 0.85f, h * 1.0f, r * 0.45f)    // peach, bottom-right
}

/** The two lights of soft UI: highlight (top-left) and shadow (bottom-right). */
private data class NeuLights(val highlight: Color, val shade: Color)

private fun neuLights(dark: Boolean) = if (dark) {
    NeuLights(highlight = Color.White.copy(alpha = 0.07f), shade = Color.Black.copy(alpha = 0.55f))
} else {
    NeuLights(highlight = Color.White.copy(alpha = 0.95f), shade = Color(0xFFA3B1C6).copy(alpha = 0.85f))
}

// ---------------------------------------------------------------------------
// Modifiers
// ---------------------------------------------------------------------------

/**
 * Drop-in replacement for Compose's `Modifier.shadow`, same parameters.
 *
 * Default style: exactly `Modifier.shadow(...)`, so nothing changes.
 * Neumorphic style: the raised soft-UI look, with the elevation deciding
 * how far the element seems to stand out of the page.
 *
 * Used for elements that sit on the page. Buttons floating over the map
 * or a photo keep the normal shadow in both styles, since a white
 * highlight on a map just looks like a glow.
 */
fun Modifier.softShadow(
    elevation: Dp,
    shape: Shape = RectangleShape,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = DefaultShadowColor,
    spotColor: Color = DefaultShadowColor
): Modifier = composed {
    when (LocalUiStyle.current) {
        UiStyle.NEUMORPHIC -> this
            .neuRaisedShadow(shape, elevation, LocalHistouryColors.current)
            .then(if (clip) Modifier.clip(shape) else Modifier)
        UiStyle.GLASS -> this
            .glassPanel(shape, elevation, LocalHistouryColors.current)
            .then(if (clip) Modifier.clip(shape) else Modifier)
        UiStyle.DEFAULT -> this.shadow(elevation, shape, clip, ambientColor, spotColor)
    }
}

/**
 * For elements floating over the map, a photo or the camera (map
 * buttons, the navigation panel, photo badges). Same parameters as
 * `Modifier.shadow`.
 *
 * Default and Neumorphism: the ordinary shadow (soft-UI light would just
 * glow on a map). Glassmorphism: the glass edge and an outside-only
 * shadow, since those elements are see-through glass there too.
 */
fun Modifier.overlayShadow(
    elevation: Dp,
    shape: Shape = RectangleShape,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = DefaultShadowColor,
    spotColor: Color = DefaultShadowColor
): Modifier = composed {
    if (LocalUiStyle.current == UiStyle.GLASS) {
        this
            .glassPanel(shape, elevation, LocalHistouryColors.current)
            .then(if (clip) Modifier.clip(shape) else Modifier)
    } else {
        this.shadow(elevation, shape, clip, ambientColor, spotColor)
    }
}

/**
 * For cards that are flat in Default (the menu sections): nothing in
 * Default, a raised soft-UI surface in Neumorphism, and a glass edge in
 * Glassmorphism.
 */
fun Modifier.neuRaised(shape: Shape, elevation: Dp = 6.dp): Modifier = composed {
    when (LocalUiStyle.current) {
        UiStyle.NEUMORPHIC -> this.neuRaisedShadow(shape, elevation, LocalHistouryColors.current)
        UiStyle.GLASS -> this.glassPanel(shape, elevation, LocalHistouryColors.current)
        UiStyle.DEFAULT -> this
    }
}

/**
 * Pressed-in soft-UI surface (inner shadow) in Neumorphic style, nothing in
 * Default. Put it right AFTER the element's background, so the inner
 * shadow is drawn over the fill but under the text and icons.
 */
fun Modifier.softInset(shape: Shape, depth: Dp = 4.dp): Modifier = composed {
    if (LocalUiStyle.current == UiStyle.NEUMORPHIC) {
        val colors = LocalHistouryColors.current
        this.drawBehind { drawInnerShadow(shape, depth, colors) }
    } else {
        this
    }
}

/**
 * Glass edge: a soft shadow drawn OUTSIDE the shape only (inside it would
 * show through the see-through card as a smudge), then the card itself,
 * then a 1dp rim over its edge, bright at the top-left where light hits
 * the glass and fading towards the bottom-right.
 */
private fun Modifier.glassPanel(shape: Shape, elevation: Dp, colors: HistouryColors): Modifier =
    this.drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }

        if (elevation > 0.dp) {
            val blur = (elevation.toPx() * 1.6f).coerceIn(6.dp.toPx(), 28.dp.toPx())
            val dy = (elevation.toPx() * 0.5f).coerceIn(1.dp.toPx(), 10.dp.toPx())
            val shade = if (colors.isDark) Color.Black.copy(alpha = 0.45f)
            else Color(0xFF5B4B7A).copy(alpha = 0.20f)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val androidPath = path.asAndroidPath()
                native.save()
                native.clipOutPath(androidPath)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = android.graphics.Color.BLACK
                    setShadowLayer(blur, 0f, dy, shade.toArgb())
                }
                native.drawPath(androidPath, paint)
                native.restore()
            }
        }

        drawContent()

        val rimTop = if (colors.isDark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.9f)
        val rimBottom = if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.25f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(rimTop, rimBottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            style = Stroke(width = 1.dp.toPx())
        )
    }

private fun Modifier.neuRaisedShadow(shape: Shape, elevation: Dp, colors: HistouryColors): Modifier {
    if (elevation <= 0.dp) return this
    return this.drawBehind { drawOuterShadow(shape, elevation, colors) }
}

/**
 * Draws the element's shape twice with a blurred shadow layer: once cast
 * up-left in the highlight colour, once down-right in the shadow colour.
 * The shape itself is filled with the page colour, which the element's own
 * background then covers.
 *
 * setShadowLayer on shapes needs hardware drawing support from Android 9
 * (API 28). On Android 8 the surfaces simply show flat, which still works.
 */
private fun DrawScope.drawOuterShadow(shape: Shape, elevation: Dp, colors: HistouryColors) {

    val lights = neuLights(colors.isDark)
    // Elevation in Default goes up to ~22dp for the bottom bar; soft UI
    // needs much less offset than that before it looks smeared.
    val offset = (elevation.toPx() * 0.45f).coerceIn(2.dp.toPx(), 9.dp.toPx())
    val blur = (elevation.toPx() * 1.1f).coerceIn(4.dp.toPx(), 20.dp.toPx())

    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawOuterShadow)) }
    val androidPath = path.asAndroidPath()

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = colors.surface.toArgb()
        }
        paint.setShadowLayer(blur, -offset, -offset, lights.highlight.toArgb())
        canvas.nativeCanvas.drawPath(androidPath, paint)
        paint.setShadowLayer(blur, offset, offset, lights.shade.toArgb())
        canvas.nativeCanvas.drawPath(androidPath, paint)
    }
}

/**
 * Inner shadow: everything OUTSIDE the shape is drawn (invisibly, since
 * it's clipped away) with a shadow layer, so only the shadow it casts
 * falls inside the shape's edges: dark along the top-left, light along
 * the bottom-right. That is what makes a field look pressed into the page.
 */
private fun DrawScope.drawInnerShadow(shape: Shape, depth: Dp, colors: HistouryColors) {

    val lights = neuLights(colors.isDark)
    val offset = depth.toPx() * 0.6f
    val blur = depth.toPx() * 1.5f
    val outline = shape.createOutline(size, layoutDirection, this)
    val inside = Path().apply { addOutline(outline) }

    val margin = blur * 4 + offset
    val frame = Path().apply {
        addRect(Rect(-margin, -margin, size.width + margin, size.height + margin))
        addOutline(outline)
        fillType = PathFillType.EvenOdd
    }
    val androidFrame = frame.asAndroidPath()

    clipPath(inside) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = colors.surface.toArgb()
            }
            paint.setShadowLayer(blur, offset, offset, lights.shade.toArgb())
            canvas.nativeCanvas.drawPath(androidFrame, paint)
            paint.setShadowLayer(blur, -offset, -offset, lights.highlight.toArgb())
            canvas.nativeCanvas.drawPath(androidFrame, paint)
        }
    }
}
