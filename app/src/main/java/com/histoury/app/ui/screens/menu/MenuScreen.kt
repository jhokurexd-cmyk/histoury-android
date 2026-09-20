package com.histoury.app.ui.screens.menu

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Shield
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

    var showAppearanceSheet by remember { mutableStateOf(false) }

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
                        navController.popBackStack()
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
                        .clickable { navController.navigate(Routes.Profile.route) }
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
                        Text(
                            text = userState.email.ifBlank { "tourist@example.com" },
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
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
            MenuSection(title = "Explore") {
                MenuItemRow(
                    icon = Icons.Default.MenuBook,
                    label = "Site Information",
                    onClick = { navController.navigate(Routes.SiteLibrary.route) }
                )
            }

            MenuSection(title = "Account") {
                MenuItemRow(
                    icon = Icons.Default.Person,
                    label = "View Profile",
                    onClick = { navController.navigate(Routes.Profile.route) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                MenuItemRow(
                    icon = Icons.Default.EmojiEvents,
                    label = "Site Rankings",
                    onClick = { navController.navigate(Routes.Rankings.route) }
                )
            }

            MenuSection(title = "Storage") {
                MenuItemRow(
                    icon = Icons.Default.Download,
                    label = "Offline Downloads",
                    onClick = { navController.navigate(Routes.Downloads.route) }
                )
            }

            // TEMPORARY — on-site test tool for checking whether ARCore's
            // Streetscape Geometry can see building shapes at Fort
            // Santiago and Baluarte de San Diego. Remove this section (and
            // Routes.StreetscapeDebug / its NavGraph entry / the
            // StreetscapeDebugScreen.kt file) once on-site testing is done
            // and a decision has been made about Streetscape Geometry.
            MenuSection(title = "Developer (Temporary)") {
                MenuItemRow(
                    icon = Icons.Default.BugReport,
                    label = "Site AR Test",
                    secondaryText = "Streetscape check",
                    onClick = { navController.navigate(Routes.StreetscapeDebug.route) }
                )
            }

            MenuSection(title = "Help & Support") {
                MenuItemRow(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = "Support Center",
                    onClick = { navController.navigate(Routes.Support.route) }
                )
            }

            MenuSection(title = "Legal") {
                MenuItemRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy Policy",
                    onClick = {
                        navController.navigate(Routes.Legal.createRoute(LegalContent.TYPE_PRIVACY))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
                MenuItemRow(
                    icon = Icons.Default.Description,
                    label = "Terms & Conditions",
                    onClick = {
                        navController.navigate(Routes.Legal.createRoute(LegalContent.TYPE_TERMS))
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
                    icon = Icons.Default.Logout,
                    label = "Logout",
                    labelColor = Danger,
                    iconTint = Danger,
                    showChevron = false,
                    onClick = {
                        menuViewModel.signOut()
                        navController.navigate(Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
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
            modifier = Modifier.fillMaxWidth(),
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
                .background(BackgroundGray),
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
        containerColor = CardSurface
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
