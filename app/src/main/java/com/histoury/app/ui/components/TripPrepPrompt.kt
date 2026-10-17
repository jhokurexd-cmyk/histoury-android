package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.data.offline.ResourcePack
import com.histoury.app.data.offline.TripPrep
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary

/**
 * "Prepare for your trip" — offered right after an itinerary is created
 * when some of its sites have AR that isn't downloaded yet. "Download for
 * This Trip" downloads only those sites, under the same Wi-Fi / mobile-data
 * rules as every other download. Placed once, over every screen.
 */
@Composable
fun TripPrepPrompt() {
    val context = LocalContext.current
    val gate = rememberDownloadGate()
    val request by TripPrep.pending.collectAsState()
    val current = request ?: return

    val count = current.sites.size
    val sizeText = current.bytes?.let { "About ${OfflineSiteManager.formatBytes(it)}" } ?: "Checking size…"

    AlertDialog(
        onDismissRequest = { TripPrep.dismiss() },
        containerColor = SheetSurface,
        title = { Text("Prepare for your trip", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Some destinations in your itinerary have AR experiences that are not downloaded yet.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${if (count == 1) "1 site" else "$count sites"} • $sizeText",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = current.sites.joinToString(", ") { it.siteName },
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sites = current.sites
                val known = current.bytes
                TripPrep.dismiss()
                gate.request(
                    bulk = true,
                    size = { known ?: ResourcePack.estimateBytes(sites) }
                ) { allowMobile ->
                    ResourcePack.downloadAll(context, sites, allowMobile)
                    Toast.makeText(
                        context,
                        "Downloading AR for your trip. Follow it in Menu > Offline Downloads.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }) {
                Text("Download for This Trip", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { TripPrep.dismiss() }) {
                Text("Maybe Later", color = TextSecondary)
            }
        }
    )
}
