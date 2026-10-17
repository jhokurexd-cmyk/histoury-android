package com.histoury.app.ui.components

import com.histoury.app.theme.SheetSurface
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.histoury.app.data.offline.DownloadNetwork
import com.histoury.app.data.offline.DownloadPreferences
import com.histoury.app.data.offline.OfflineSiteManager
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The check every Download button goes through before a download is queued
 * (one site, Download All, Download for This Trip, the pop-up, the bell).
 *
 *  - "Download over" = Wi-Fi only and the phone is on mobile data:
 *    "Waiting for Wi-Fi" dialog — Cancel, or Use Mobile Data This Time
 *    (for this download only; the setting is not changed). For a group
 *    download that is large (or of unknown size) a second dialog confirms
 *    the data use.
 *  - Anything else: queued straight away. With no connection at all the
 *    site shows "Waiting for Wi-Fi" / "Waiting for connection" and starts by
 *    itself later (see OfflineSiteManager).
 *
 * Usage: `val gate = rememberDownloadGate()` in the screen (it also shows the
 * dialogs), then `gate.request { allowMobile -> ...start... }`.
 */
@Stable
class DownloadGate internal constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {

    internal sealed interface Dialog {
        val start: (allowMobile: Boolean) -> Unit

        data class WaitingForWifi(
            val bulk: Boolean,
            val size: (suspend () -> Long?)?,
            override val start: (Boolean) -> Unit
        ) : Dialog

        data class LargeMobileDownload(
            val bytes: Long?,
            override val start: (Boolean) -> Unit
        ) : Dialog
    }

    internal var dialog by mutableStateOf<Dialog?>(null)

    /**
     * @param bulk true for Download All / a trip (enables the large-download warning)
     * @param size total size, only asked for if the warning may be needed
     * @param start queues the download; `allowMobile` = the one-time override
     */
    fun request(
        bulk: Boolean = false,
        size: (suspend () -> Long?)? = null,
        start: (allowMobile: Boolean) -> Unit
    ) {
        val wifiOnly = DownloadPreferences.downloadOver(context) == DownloadNetwork.WIFI_ONLY
        val onMobile = ConnectivityObserver.currentKind(context) == ConnectivityObserver.NetworkKind.MOBILE
        if (wifiOnly && onMobile) {
            dialog = Dialog.WaitingForWifi(bulk, size, start)
        } else {
            start(false)
        }
    }

    internal fun useMobileData(request: Dialog.WaitingForWifi) {
        dialog = null
        if (!request.bulk) {
            request.start(true)
            return
        }
        scope.launch {
            val bytes = runCatching { request.size?.invoke() }.getOrNull()
            if (bytes == null || bytes >= LARGE_MOBILE_DOWNLOAD_BYTES) {
                dialog = Dialog.LargeMobileDownload(bytes, request.start)
            } else {
                request.start(true)
            }
        }
    }

    companion object {
        /** A group download at least this big asks once more before using mobile data. */
        const val LARGE_MOBILE_DOWNLOAD_BYTES = 100L * 1024L * 1024L
    }
}

@Composable
fun rememberDownloadGate(): DownloadGate {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val gate = remember(context, scope) { DownloadGate(context, scope) }

    when (val current = gate.dialog) {

        is DownloadGate.Dialog.WaitingForWifi -> AlertDialog(
            onDismissRequest = { gate.dialog = null },
            containerColor = SheetSurface,
            title = { Text("Waiting for Wi-Fi", color = TextPrimary) },
            text = {
                Text(
                    if (current.bulk) "These AR resources are set to download over Wi-Fi only."
                    else "This AR resource is set to download over Wi-Fi only.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { gate.useMobileData(current) }) {
                    Text("Use Mobile Data This Time", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gate.dialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )

        is DownloadGate.Dialog.LargeMobileDownload -> AlertDialog(
            onDismissRequest = { gate.dialog = null },
            containerColor = SheetSurface,
            title = { Text("Download using mobile data?", color = TextPrimary) },
            text = {
                Text(
                    current.bytes?.let {
                        "This download is about ${OfflineSiteManager.formatBytes(it)} and may use " +
                            "a large amount of your mobile data."
                    } ?: "This download may use a large amount of your mobile data.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    gate.dialog = null
                    current.start(true)
                }) {
                    Text("Download Anyway", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gate.dialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )

        null -> Unit
    }

    return gate
}
