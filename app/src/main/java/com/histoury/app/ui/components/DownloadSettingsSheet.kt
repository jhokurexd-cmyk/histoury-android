package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.data.offline.AutoUpdateMode
import com.histoury.app.data.offline.DownloadNetwork
import com.histoury.app.data.offline.DownloadPreferences
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Divider
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary

/**
 * Offline Downloads settings: "Download over" and "Automatic resource
 * updates". Single-choice radio rows (no dropdown), applied immediately.
 * Shown in the Offline Downloads screen and from Menu > Preferences.
 */
@Composable
fun DownloadSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember(context) { DownloadPreferences.load(context); true }

    val downloadOver by DownloadPreferences.downloadOver.collectAsState()
    val autoUpdates by DownloadPreferences.autoUpdates.collectAsState()
    val autoDownloadOnWifi by DownloadPreferences.autoDownloadOnWifi.collectAsState()

    Column(modifier = modifier) {

        // ----- Auto-download on Wi-Fi -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                    value = autoDownloadOnWifi,
                    role = Role.Switch,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onValueChange = { DownloadPreferences.setAutoDownloadOnWifi(context, it) }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-download on Wi-Fi",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Save AR sites automatically whenever you're connected to Wi-Fi. Never uses mobile data.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = autoDownloadOnWifi,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnColor,
                    checkedTrackColor = Primary,
                    checkedBorderColor = Primary
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = Divider,
            thickness = 0.5.dp
        )

        SettingsGroupTitle(
            title = "Download over",
            hint = "Used when you download AR resources."
        )
        Column(Modifier.selectableGroup()) {
            DownloadNetwork.entries.forEach { option ->
                RadioRow(
                    label = option.label,
                    selected = option == downloadOver,
                    onSelect = { DownloadPreferences.setDownloadOver(context, option) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = Divider,
            thickness = 0.5.dp
        )

        SettingsGroupTitle(
            title = "Automatic resource updates",
            hint = "Only for sites you've already downloaded. When off, updates wait for you in Offline Downloads."
        )
        Column(Modifier.selectableGroup()) {
            AutoUpdateMode.entries.forEach { option ->
                RadioRow(
                    label = option.label,
                    selected = option == autoUpdates,
                    onSelect = { DownloadPreferences.setAutoUpdates(context, option) }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String, hint: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = hint,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Primary,
                unselectedColor = TextTertiary
            ),
            modifier = Modifier.padding(12.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = TextPrimary
        )
    }
}

/** The same settings as a bottom sheet (Menu > Preferences, and the gear on Offline Downloads). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsSheet(onDismiss: () -> Unit) {
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
                text = "Offline Downloads",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose how AR models and narration are downloaded and kept up to date.",
                fontSize = 12.5.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            DownloadSettingsContent()
        }
    }
}

/** Short summary for a settings row, e.g. "Wi-Fi only". */
@Composable
fun downloadSettingsSummary(): String {
    val context = LocalContext.current
    remember(context) { DownloadPreferences.load(context); true }
    val downloadOver by DownloadPreferences.downloadOver.collectAsState()
    return downloadOver.label
}
