package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.util.DeviceLocation

/** Whether the phone's location service is on, kept up to date while shown. */
@Composable
fun rememberDeviceLocationEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember(context) { DeviceLocation.observe(context) }
    val enabled by flow.collectAsState(initial = DeviceLocation.isEnabled(context))
    return enabled
}

/**
 * Returns an action that turns the location service on.
 *
 * It first asks Google Play services for its one-tap "Turn on device
 * location?" dialog, so the visitor never leaves the app. Only if that isn't
 * available (no Play services, or the dialog can't be shown) does it fall
 * back to the system's Location settings page.
 *
 * Nothing needs to happen on return: screens watch
 * [rememberDeviceLocationEnabled] and pick up the change themselves.
 */
@Composable
fun rememberTurnOnLocation(): () -> Unit {
    val context = LocalContext.current

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    return remember(context, resolutionLauncher) {
        {
            val request = LocationSettingsRequest.Builder()
                .addLocationRequest(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
                )
                .setAlwaysShow(true)
                .build()

            LocationServices.getSettingsClient(context)
                .checkLocationSettings(request)
                .addOnFailureListener { error ->
                    val opened = error is ResolvableApiException && runCatching {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(error.resolution).build()
                        )
                    }.isSuccess
                    if (!opened) openLocationSettings(context)
                }
        }
    }
}

private fun openLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Location is off" pop-up. Shown when the app has location permission but
 * the phone's location service is switched off, so routes, live distance,
 * ETA and arrival detection can't work.
 */
@Composable
fun TurnOnLocationDialog(
    message: String,
    onDismiss: () -> Unit
) {
    val turnOnLocation = rememberTurnOnLocation()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                "Turn on location",
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                message,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                turnOnLocation()
            }) {
                Text("Turn On Location", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = TextSecondary)
            }
        }
    )
}
