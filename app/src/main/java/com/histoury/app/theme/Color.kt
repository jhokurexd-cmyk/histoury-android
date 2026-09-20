package com.histoury.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Histoury's colour system.
 *
 * Every colour the app draws with is read through [LocalHistouryColors],
 * which [HistouryTheme] fills with either [LightHistouryColors] or
 * [DarkHistouryColors]. The names below are exposed as @Composable
 * properties rather than plain constants, so a screen written as
 * `color = TextPrimary` keeps working unchanged and simply resolves to
 * whichever palette is active.
 *
 * The one rule that comes with that: these names can only be read from
 * inside a composable. A top-level `private val Foo = SurfaceSoft` will not
 * compile — make it a `@Composable get()` property, or use a raw constant.
 */

// ---------------------------------------------------------------------------
// Raw palette
// ---------------------------------------------------------------------------
//
// Fixed values, safe to read anywhere. Nothing outside this file should
// need them; use the theme-aware names further down.

// --- Histoury brand: coral red ---
// The coral is the app's identity, so it survives the theme switch almost
// unchanged. It only lightens slightly in dark mode, where a fully
// saturated red on near-black vibrates against the background.
val CoralLight = Color(0xFFFB4D4C)
val CoralDark = Color(0xFFFF6B69)

private val CoralDeepLight = Color(0xFFFC4E4D)
private val CoralDeepDark = Color(0xFFFF8280)

// Soft coral tints, used for pinned toggles, badges, and icon chips.
private val CoralSoftLight = Color(0xFFFFEDEE)
private val CoralSoftStrongLight = Color(0xFFFFE0E2)

// The dark equivalents can't be tints of white, so they're the coral laid
// over the dark surface at low opacity instead.
private val CoralSoftDark = Color(0xFF3A2426)
private val CoralSoftStrongDark = Color(0xFF4C2A2D)

// --- Light neutrals ---
private val WhiteBackground = Color(0xFFFFFFFF)

/**
 * The page behind white cards in light mode.
 *
 * This used to be #F8F7F9, roughly 3% off white — close enough that cards
 * had no edge and the floating footer disappeared into the page entirely.
 * Dropping it to #F0EEF3 gives a ~6% step, which is what makes a white card
 * read as sitting *on* something. The faint violet lean keeps it from going
 * cold grey next to the coral.
 */
private val WarmOffWhite = Color(0xFFF0EEF3)

private val InkPrimary = Color(0xFF1F2937)
private val InkSecondary = Color(0xFF6B7280)
private val InkTertiary = Color(0xFF9CA3AF)

// Nudged a shade deeper alongside the background — the old values were
// calibrated against a near-white page and washed out against this one.
private val DividerLight = Color(0xFFE7E4EC)
private val OutlineLight = Color(0xFFDFDCE6)
private val CompletedSurfaceLight = Color(0xFFE8E5EC)

// --- Dark neutrals ---
//
// Three steps rather than two: the app leans on cards floating above a
// background, and a single dark grey for both flattens every screen. The
// page sits at 0x121013, cards a step above it, and raised things like
// sheets a step above those.
private val NightBackground = Color(0xFF121013)
private val NightSurface = Color(0xFF1C1A1E)
private val NightSurfaceRaised = Color(0xFF262329)
private val NightInkPrimary = Color(0xFFF3F1F4)
private val NightInkSecondary = Color(0xFFAEA8B4)
private val NightInkTertiary = Color(0xFF7C7683)
private val DividerDark = Color(0xFF2C2930)
private val OutlineDark = Color(0xFF35313A)
private val CompletedSurfaceDark = Color(0xFF232026)

// --- Accents, shared by both palettes ---

// AR call-to-action accent, kept separate from the coral primary so the
// "Travel Back in Time" button stays visually distinct.
private val Gold = Color(0xFFF5A623)
private val GoldDark = Color(0xFFFFBB4D)

private val StarYellow = Color(0xFFFFC107)

private val DangerLight = Color(0xFFB91C1C)
private val DangerDark = Color(0xFFFF7A7A)

// Success reverses between themes in the same way danger does: a deep green
// that reads on a pale tint, and a light green that reads on a dark one.
private val SuccessLight = Color(0xFF166534)
private val SuccessSurfaceLight = Color(0xFFDCFCE7)

// Danger's tint, paired with the DangerLight/DangerDark inks above. Stored
// as a pair for the same reason success is: the wash and the text on it are
// always used together, and deriving one from the other is how a warning
// ends up as dark red on dark red the first time the theme changes.
private val DangerSurfaceLight = Color(0xFFFDE6E6)
private val SuccessDark = Color(0xFF6BD99A)
private val SuccessSurfaceDark = Color(0xFF16301F)
private val DangerSurfaceDark = Color(0xFF3A1C1C)

private val CompletedInkLight = Color(0xFF9CA3AF)
private val CompletedInkDark = Color(0xFF6F6A76)

// ---------------------------------------------------------------------------
// Palette definition
// ---------------------------------------------------------------------------

@Immutable
data class HistouryColors(

    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val primarySoftStrong: Color,

    /** The page behind everything. */
    val background: Color,

    /** Cards, sheets, and anything that floats above [background]. */
    val surface: Color,

    /** Where the app wants a card to read as raised off another card. */
    val surfaceRaised: Color,

    /**
     * The app's scrolling background. In light mode a warm off-white that
     * keeps white cards from disappearing; in dark mode the darkest step,
     * for the same reason in reverse.
     */
    val surfaceSoft: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    val divider: Color,
    val outline: Color,

    val accentGold: Color,
    val ratingStar: Color,

    /** Destructive actions — logout, delete, remove. */
    val danger: Color,

    /**
     * Confirmations: a visited site, an accepted appeal, a completed stop.
     *
     * Two values because the pair is always used together — a tint behind a
     * label, with the label on top of it. Storing only one and deriving the
     * other is how a badge ends up with dark-green text on a dark-green
     * chip the first time somebody changes theme.
     */
    val success: Color,
    val successSurface: Color,

    /** The tint behind a destructive or warning message. */
    val dangerSurface: Color,

    /** A stop already visited: greyed back, not hidden. */
    val completedSurface: Color,
    val completedContent: Color,

    /**
     * The floating navigation bar is built from five layers rather than
     * one flat fill, because a single alpha value reads as a translucent
     * sticker, not as glass.
     *
     * [glassTop] and [glassBottom] are the ends of the body gradient: real
     * frosted glass is denser where it catches light and thins out below,
     * so the top is the more opaque of the two and the bottom is where
     * content ghosts through most.
     */
    val glassTop: Color,
    val glassBottom: Color,

    /**
     * A diagonal specular sweep across the upper-left, the reflection of
     * whatever light source the panel is under. This is the layer that
     * does most of the work — it's what the eye reads as a curved,
     * physical surface rather than a painted rectangle.
     */
    val glassSheen: Color,

    /** The lit top edge of the panel, brightest point of the rim. */
    val glassRim: Color,

    /**
     * The lower edge, where glass refracts rather than reflects. The rim
     * runs as a gradient from [glassRim] down to this, which is why the
     * bar's outline looks lit from above instead of uniformly stroked.
     */
    val glassEdge: Color,

    /**
     * Cast by the floating bar.
     *
     * Light mode needs one: a pale bar on a pale page has nothing but a
     * shadow to prove it's floating. Dark mode doesn't — there the bar is
     * *lighter* than the page it sits on, which already reads as raised,
     * and a shadow under a translucent surface only muddies it. Transparent
     * there, so the same modifier draws nothing.
     */
    val glassShadow: Color,

    /**
     * Scrim laid over photographs so white text stays readable on them.
     * Deeper in dark mode, where the surrounding UI gives the eye less
     * light to adapt from.
     */
    val imageScrim: Color,

    val isDark: Boolean
)

val LightHistouryColors = HistouryColors(
    primary = CoralLight,
    primaryDark = CoralDeepLight,
    primarySoft = CoralSoftLight,
    primarySoftStrong = CoralSoftStrongLight,
    background = WhiteBackground,
    surface = WhiteBackground,
    surfaceRaised = WhiteBackground,
    surfaceSoft = WarmOffWhite,
    textPrimary = InkPrimary,
    textSecondary = InkSecondary,
    textTertiary = InkTertiary,
    divider = DividerLight,
    outline = OutlineLight,
    accentGold = Gold,
    ratingStar = StarYellow,
    danger = DangerLight,
    success = SuccessLight,
    successSurface = SuccessSurfaceLight,
    dangerSurface = DangerSurfaceLight,
    completedSurface = CompletedSurfaceLight,
    completedContent = CompletedInkLight,
    // White glass. The body runs dense-to-thin down the panel, the sheen
    // sits over the top-left, and the rim is a bright white highlight
    // fading into a cool refracted edge at the bottom.
    glassTop = Color.White.copy(alpha = 0.93f),
    glassBottom = Color.White.copy(alpha = 0.78f),
    glassSheen = Color.White.copy(alpha = 0.5f),
    glassRim = Color.White.copy(alpha = 0.95f),
    glassEdge = Color(0xFFB4AEC2).copy(alpha = 0.45f),
    glassShadow = Color(0xFF2A2333),
    imageScrim = Color.Black.copy(alpha = 0.35f),
    isDark = false
)

val DarkHistouryColors = HistouryColors(
    primary = CoralDark,
    primaryDark = CoralDeepDark,
    primarySoft = CoralSoftDark,
    primarySoftStrong = CoralSoftStrongDark,
    background = NightBackground,
    surface = NightSurface,
    surfaceRaised = NightSurfaceRaised,
    surfaceSoft = NightBackground,
    textPrimary = NightInkPrimary,
    textSecondary = NightInkSecondary,
    textTertiary = NightInkTertiary,
    divider = DividerDark,
    outline = OutlineDark,
    accentGold = GoldDark,
    ratingStar = StarYellow,
    danger = DangerDark,
    success = SuccessDark,
    successSurface = SuccessSurfaceDark,
    dangerSurface = DangerSurfaceDark,
    completedSurface = CompletedSurfaceDark,
    completedContent = CompletedInkDark,
    // Smoked glass. Same five layers, all pulled far down: a dark panel
    // reflects a fraction of what a white one does, so a rim built at
    // light-mode strength would look like a drawn outline instead of an
    // edge catching light.
    glassTop = NightSurfaceRaised.copy(alpha = 0.88f),
    glassBottom = NightSurface.copy(alpha = 0.74f),
    glassSheen = Color.White.copy(alpha = 0.07f),
    glassRim = Color.White.copy(alpha = 0.16f),
    glassEdge = Color.White.copy(alpha = 0.04f),
    glassShadow = Color.Transparent,
    imageScrim = Color.Black.copy(alpha = 0.5f),
    isDark = true
)

/**
 * Static rather than dynamic: the palette swaps at most a handful of times
 * in a session, so there's nothing to gain from tracking reads
 * individually, and plenty to gain from not doing so on every colour in
 * every screen.
 */
val LocalHistouryColors = staticCompositionLocalOf { LightHistouryColors }

// ---------------------------------------------------------------------------
// Theme-aware names
// ---------------------------------------------------------------------------
//
// These keep the call sites the app already has. `color = TextPrimary` on
// line 300 of some screen written months ago now follows the theme without
// that screen being touched.

val Primary: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.primary

val PrimaryDark: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.primaryDark

val PrimarySoft: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.primarySoft

val PrimarySoftStrong: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.primarySoftStrong

val Background: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.background

val Surface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.surface

/**
 * What used to be a literal `Color.White` on every card, sheet and dialog.
 * In light mode it still is; in dark mode it's the step above the page.
 */
val CardSurface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.surface

val RaisedSurface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.surfaceRaised

val SurfaceSoft: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.surfaceSoft

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.textSecondary

val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.textTertiary

val Divider: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.divider

val Outline: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.outline

val AccentGold: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.accentGold

/**
 * Ink for text sitting on the gold AR button.
 *
 * Gold is a light surface in both themes, so its label stays dark in both —
 * unlike the coral button, whose label is always white. Getting this wrong
 * is how a call to action ends up invisible in one mode.
 */
val OnAccentGold: Color = Color(0xFF2B2118)

val RatingStar: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.ratingStar

val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.danger

val Success: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.success

val SuccessSurface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.successSurface

val DangerSurface: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.dangerSurface

val CompletedSurfaceColor: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.completedSurface

val CompletedContentColor: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.completedContent

val GlassTop: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassTop

val GlassBottom: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassBottom

val GlassSheen: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassSheen

val GlassRim: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassRim

val GlassEdge: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassEdge

val GlassShadow: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.glassShadow

val ImageScrim: Color
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.imageScrim

/**
 * For the handful of places that need to branch on the theme rather than
 * just pick a colour — the map's night style, mainly.
 */
val isDarkTheme: Boolean
    @Composable @ReadOnlyComposable get() = LocalHistouryColors.current.isDark

/**
 * White that stays white: text and icons sitting on the coral button or on
 * a photograph, where the surface underneath doesn't change with the theme.
 * Spelling it out separates "this is white on purpose" from "this was white
 * because the app only had a light mode".
 */
val OnColor: Color = Color.White
