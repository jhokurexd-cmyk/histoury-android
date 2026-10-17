package com.histoury.app.ui.screens.menu

import com.histoury.app.theme.GlassDarkColors
import com.histoury.app.theme.GlassLightColors
import com.histoury.app.theme.SheetSurface
import com.histoury.app.theme.styleBackdrop
import androidx.compose.material.icons.filled.School
import com.histoury.app.ui.tutorial.TutorialPreference
import com.histoury.app.ui.tutorial.TutorialStep
import com.histoury.app.ui.tutorial.TutorialTour
import com.histoury.app.ui.tutorial.tutorialTarget
import com.histoury.app.navigation.safePopBackStack
import com.histoury.app.navigation.navigateSafely
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import com.histoury.app.ui.screens.legal.LegalContent
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.histoury.app.data.viewmodel.MenuViewModel
import com.histoury.app.navigation.Routes
import com.histoury.app.theme.Danger
import com.histoury.app.theme.ThemeMode
import com.histoury.app.theme.ThemePreference
import com.histoury.app.theme.UiStyle
import com.histoury.app.theme.LocalUiStyle
import com.histoury.app.theme.LocalHistouryColors
import com.histoury.app.theme.LightHistouryColors
import com.histoury.app.theme.DarkHistouryColors
import com.histoury.app.theme.NeumorphicLightColors
import com.histoury.app.theme.NeumorphicDarkColors
import com.histoury.app.theme.isDarkTheme
import com.histoury.app.theme.neuRaised
import com.histoury.app.theme.softInset
import com.histoury.app.theme.softShadow
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.CompositionLocalProvider
import com.histoury.app.theme.Outline as DividerColor
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextPrimary as TextMain
import com.histoury.app.theme.SurfaceSoft as BackgroundGray
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.theme.CardSurface

// These file-local names predate the theme system; they now alias the
// shared palette so this screen follows dark mode with the rest of the app.

@Composable
fun MenuScreen(
    navController: NavHostController,
    menuViewModel: MenuViewModel = viewModel()
) {

    val context = LocalContext.current

    val themeMode by ThemePreference.mode.collectAsState()

    val favoriteIds by com.histoury.app.data.repository.FavoritesRepository.favoriteIds.collectAsState()
    val favoriteCount = favoriteIds.size

    var showAppearanceSheet by remember { mutableStateOf(false) }

    // Menu > Theme: Default or Neumorphism.
    val uiStyle by ThemePreference.style.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }

    if (showThemeSheet) {
        ThemeStyleSheet(
            current = uiStyle,
            onSelect = { style ->
                ThemePreference.setStyle(context, style)
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    // Settings > Offline Downloads: "Download over" and automatic updates.
    var showDownloadSettings by remember { mutableStateOf(false) }

    if (showDownloadSettings) {
        com.histoury.app.ui.components.DownloadSettingsSheet(
            onDismiss = { showDownloadSettings = false }
        )
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            current = themeMode,
            onSelect = { mode ->
                ThemePreference.set(context, mode)
                showAppearanceSheet = false
            },
            onDismiss = { showAppearanceSheet = false }
        )
    }
    val userState by menuViewModel.uiState.collectAsState()

    // Logging out is one tap away from "Appearance" at the bottom of a long
    // list — confirm it rather than signing someone out by accident.
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to see your itineraries, photos and visit history.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLogoutConfirm = false
                    menuViewModel.signOut()
                    navController.navigateSafely(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text("Log Out", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        menuViewModel.loadUser()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .statusBarsPadding()
    ) {
        // --- Custom Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Menu",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.safePopBackStack()
                    }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close menu",
                    tint = PrimaryPink, // Hint of coral
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // --- Profile Card ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = CardSurface,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .clickable { navController.navigateSafely(Routes.Profile.route) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(PrimaryPink.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userState.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = userState.photoUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryPink,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userState.displayName.ifBlank { "Tourist User" },
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        // No placeholder address: showing a made-up email
                        // for a moment read as someone else's account.
                        if (userState.email.isNotBlank()) {
                            Text(
                                text = userState.email,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PrimaryPink.copy(alpha = 0.4f), // Hint of coral
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Menu Sections ---

            TutorialTour(
                id = "menu",
                steps = listOf(
                    TutorialStep(
                        target = "menu_library",
                        title = "Site Information",
                        body = "Every historical site in one list, with the ones you've unlocked by visiting."
                    ),
                    TutorialStep(
                        target = "menu_favorites",
                        title = "Favorites",
                        body = "Sites you saved with the heart button."
                    ),
                    TutorialStep(
                        target = "menu_rankings",
                        title = "Site Rankings",
                        body = "The top-rated sites and what other visitors say about them."
                    ),
                    TutorialStep(
                        target = "menu_downloads",
                        title = "Offline Downloads",
                        body = "Save AR models and narration to your phone, so they work at the site even without signal."
                    ),
                    // Further down the page (possibly off screen), so
                    // explained without a spotlight.
                    TutorialStep(
                        target = null,
                        title = "Help is further down",
                        body = "Under Help & Support: the Support Center for questions, and Show App Tutorials Again to replay these tours anytime."
                    )
                )
            )
            MenuSection(title = "Explore") {
                Box(Modifier.tutorialTarget("menu_library")) {
                    MenuItemRow(
                        icon = Icons.Default.MenuBook,
                        label = "Site Information",
                        onClick = { navController.navigateSafely(Routes.SiteLibrary.route) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                Box(Modifier.tutorialTarget("menu_favorites")) {
                    MenuItemRow(
                        icon = Icons.Default.Favorite,
                        label = "Favorites",
                        secondaryText = if (favoriteCount > 0) favoriteCount.toString() else null,
                        onClick = { navController.navigateSafely(Routes.Favorites.route) }
                    )
                }
            }

            MenuSection(title = "Account") {
                MenuItemRow(
                    icon = Icons.Default.Person,
                    label = "View Profile",
                    onClick = { navController.navigateSafely(Routes.Profile.route) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                Box(Modifier.tutorialTarget("menu_rankings")) {
                    MenuItemRow(
                        icon = Icons.Default.EmojiEvents,
                        label = "Site Rankings",
                        onClick = { navController.navigateSafely(Routes.Rankings.route) }
                    )
                }
            }

            MenuSection(title = "Storage") {
                Box(Modifier.tutorialTarget("menu_downloads")) {
                    MenuItemRow(
                        icon = Icons.Default.Download,
                        label = "Offline Downloads",
                        onClick = { navController.navigateSafely(Routes.Downloads.route) }
                    )
                }
            }

            // The on-site "Site AR Test" (Streetscape Geometry check) is no
            // longer listed here: it showed in every debuggable build, which
            // is what testers and visitors were installing. The tool itself
            // (StreetscapeDebugScreen, Routes.StreetscapeDebug) is kept for
            // internal use; to reach it during development, temporarily add
            // a MenuItemRow here that navigates to Routes.StreetscapeDebug.

            MenuSection(title = "Help & Support") {
                Box(Modifier.tutorialTarget("menu_support")) {
                    MenuItemRow(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "Support Center",
                        onClick = { navController.navigateSafely(Routes.Support.route) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                Box(Modifier.tutorialTarget("menu_tutorials")) {
                    MenuItemRow(
                        icon = Icons.Default.School,
                        label = "Show App Tutorials Again",
                        onClick = {
                            TutorialPreference.resetAll(context)
                            android.widget.Toast.makeText(
                                context,
                                "Tutorials will show again as you open each screen.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            MenuSection(title = "Legal") {
                MenuItemRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy Policy",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_PRIVACY))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                MenuItemRow(
                    icon = Icons.Default.Description,
                    label = "Terms & Conditions",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_TERMS))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                MenuItemRow(
                    icon = Icons.Default.Info,
                    label = "Legal Notices & Credits",
                    onClick = {
                        navController.navigateSafely(Routes.Legal.createRoute(LegalContent.TYPE_NOTICES))
                    }
                )
            }

            MenuSection(title = "Preferences") {
                MenuItemRow(
                    icon = if (themeMode == ThemeMode.DARK) {
                        Icons.Default.DarkMode
                    } else {
                        Icons.Default.LightMode
                    },
                    label = "Appearance",
                    secondaryText = themeMode.label,
                    onClick = { showAppearanceSheet = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = DividerColor,
                    thickness = 0.5.dp
                )
                MenuItemRow(
                    icon = Icons.Default.Palette,
                    label = "Theme",
                    secondaryText = uiStyle.label,
                    onClick = { showThemeSheet = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = DividerColor,
                    thickness = 0.5.dp
                )
                MenuItemRow(
                    icon = Icons.Default.Download,
                    label = "Offline Downloads",
                    secondaryText = com.histoury.app.ui.components.downloadSettingsSummary(),
                    onClick = { showDownloadSettings = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = DividerColor,
                    thickness = 0.5.dp
                )
                MenuItemRow(
                    icon = Icons.Default.Logout,
                    label = "Logout",
                    labelColor = Danger,
                    iconTint = Danger,
                    showChevron = false,
                    onClick = { showLogoutConfirm = true }
                )
            }

            // --- Version Info ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Histoury v1.0.0",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Explore Intramuros, Manila",
                    color = TextSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MenuSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            // Lifts out of the page in Neumorphism; flat as before in Default.
            modifier = Modifier
                .fillMaxWidth()
                .neuRaised(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = CardSurface
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = TextMain,
    iconTint: Color = PrimaryPink, // Coral accent on icons
    showChevron: Boolean = true,
    secondaryText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BackgroundGray)
                // Pressed into the card in Neumorphism; no effect in Default.
                .softInset(RoundedCornerShape(10.dp), depth = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            color = labelColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (secondaryText != null) {
            Text(
                text = secondaryText,
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PrimaryPink.copy(alpha = 0.4f), // Hint of coral
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Appearance picker.
 *
 * Three options rather than a switch, because "match system" is a real
 * answer and not the absence of one — a switch would force the visitor to
 * pick a side and then stop tracking their phone's own day/night setting.
 *
 * Selecting applies immediately: the palette is a CompositionLocal, so the
 * whole app recomposes into the new theme with the sheet still open, and
 * the choice can be judged rather than guessed at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    // Not named `selected`: the row below assigns to the semantics property
    // of that name, and a parameter would shadow it.
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {

            Text(
                text = "Appearance",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Choose how Histoury looks on this device.",
                fontSize = 12.5.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(16.dp))

            ThemeMode.entries.forEach { mode ->

                val isSelected = mode == current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) BackgroundGray else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(mode) }
                        )
                        .semantics { selected = isSelected }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        },
                        contentDescription = null,
                        tint = if (isSelected) PrimaryPink else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(14.dp))

                    Text(
                        text = mode.label,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = TextMain,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = PrimaryPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Theme picker: Default or Neumorphism.
 *
 * Works with Appearance rather than replacing it. Appearance answers
 * "light or dark", Theme answers "what do the surfaces look like", so
 * Neumorphism has both a light and a dark version.
 *
 * Each option shows a small preview drawn with that style's real palette
 * and shadows, in the current light/dark mode, so the visitor sees what
 * they're picking before they pick it. Like Appearance, it applies at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeStyleSheet(
    current: UiStyle,
    onSelect: (UiStyle) -> Unit,
    onDismiss: () -> Unit
) {

    val dark = isDarkTheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {

            Text(
                text = "Theme",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Choose the style of cards and buttons.",
                fontSize = 12.5.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(16.dp))

            UiStyle.entries.forEach { style ->

                val isSelected = style == current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) BackgroundGray else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(style) }
                        )
                        .semantics { selected = isSelected }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    ThemeStylePreview(style = style, dark = dark)

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = TextMain
                        )
                        Text(
                            text = style.description,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = PrimaryPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A tiny page with one card and one coral dot, drawn in [style]. */
@Composable
private fun ThemeStylePreview(style: UiStyle, dark: Boolean) {

    val palette = when (style) {
        UiStyle.DEFAULT -> if (dark) DarkHistouryColors else LightHistouryColors
        UiStyle.NEUMORPHIC -> if (dark) NeumorphicDarkColors else NeumorphicLightColors
        UiStyle.GLASS -> if (dark) GlassDarkColors else GlassLightColors
    }

    CompositionLocalProvider(
        LocalHistouryColors provides palette,
        LocalUiStyle provides style
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                // Glassmorphism's colour wash; nothing for the other styles.
                .styleBackdrop()
                .background(palette.surfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .size(width = 42.dp, height = 24.dp)
                    .softShadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.textTertiary.copy(alpha = 0.5f))
                )
            }
        }
    }
}
