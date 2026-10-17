package com.histoury.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextSecondary

/**
 * "Book or check rates" for a hotel: opens the hotel's own booking page
 * (the link an admin set in Places Management) in the browser or the
 * booking app that handles it.
 *
 * Histoury only refers the visitor. Dates, rooms, prices and payment all
 * happen on the hotel's side, which the note under the button says, so no
 * one expects a booking to show up in the app.
 */
@Composable
fun BookStayButton(bookingUrl: String, modifier: Modifier = Modifier) {

    if (bookingUrl.isBlank()) return
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open the hotel's booking page"
                ) { openBookingLink(context, bookingUrl) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = OnColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Book or check rates",
                color = OnColor,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = OnColor.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Opens the hotel's booking page. Reservations and payment are handled by the hotel, not Histoury.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Opens [raw] (adds https:// when missing); explains instead of crashing if nothing can. */
fun openBookingLink(context: Context, raw: String) {
    val value = raw.trim()
    if (value.isEmpty()) return
    val uri = Uri.parse(
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app on this phone can open that link.", Toast.LENGTH_SHORT).show()
    }
}
