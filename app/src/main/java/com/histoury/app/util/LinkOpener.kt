package com.histoury.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens a web link (a site's source, a credit) safely.
 *
 * Starting ACTION_VIEW on the raw text crashed the app in two cases:
 *  - a link saved without a scheme ("www.nhcp.gov.ph/...") parses to a Uri
 *    no app can open -> ActivityNotFoundException
 *  - a phone with no browser at all -> the same exception
 *
 * Now a missing scheme gets "https://", anything that isn't http(s) is
 * refused, and a phone that can't open it gets a message instead of a crash.
 */
object LinkOpener {

    /** The https Uri for [raw], or null if it isn't a usable web link. */
    fun toWebUri(raw: String?): Uri? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val withScheme = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("//") -> "https:$text"
            // "mailto:", "javascript:", "intent:" and the like: not a web page.
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text) -> return null
            else -> "https://$text"
        }

        val uri = Uri.parse(withScheme)
        return uri.takeIf { !it.host.isNullOrBlank() }
    }

    fun openWebLink(context: Context, raw: String?) {
        val uri = toWebUri(raw)
        if (uri == null) {
            Toast.makeText(context, "This link can't be opened.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No browser is installed to open this link.", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "This link can't be opened.", Toast.LENGTH_SHORT).show()
        }
    }
}
