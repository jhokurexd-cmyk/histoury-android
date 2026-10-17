package com.histoury.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.Danger
import com.histoury.app.theme.Primary
import com.histoury.app.theme.Success
import com.histoury.app.theme.TextSecondary
import com.histoury.app.theme.TextTertiary
import com.histoury.app.util.AsyncCheck
import com.histoury.app.util.Validators

/**
 * The message under a field. Slides open/closed (160ms) instead of popping,
 * so the form doesn't jump as errors come and go while typing, and keeps
 * showing the last text while it closes. Announced by TalkBack.
 */
@Composable
fun FieldErrorText(
    error: String?,
    modifier: Modifier = Modifier
) {
    var lastError by remember { mutableStateOf(error) }
    if (error != null) lastError = error

    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)) +
            expandVertically(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)),
        exit = fadeOut(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)) +
            shrinkVertically(tween(HistouryMotion.DurationShort, easing = HistouryMotion.EaseOut)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(start = 4.dp, top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = lastError.orEmpty(),
                color = Danger,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Live password checklist: each line turns green with a tick as it's met.
 * Required lines that aren't met stay neutral (not red) until the field has
 * been touched, so an untouched form isn't a wall of errors.
 */
@Composable
fun PasswordChecklist(
    password: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = 4.dp, top = 8.dp)) {
        Validators.passwordRequirements(password).forEach { requirement ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (requirement.met) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (requirement.met) "Met" else "Not met",
                    tint = if (requirement.met) Success else TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = requirement.label,
                    color = if (requirement.met) Success else TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

/** "Checking…" / "Available" line under a field for a debounced server check. */
@Composable
fun AsyncCheckLine(
    status: AsyncCheck,
    availableText: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = status == AsyncCheck.CHECKING || status == AsyncCheck.AVAILABLE,
        enter = fadeIn(tween(HistouryMotion.DurationShort)) + expandVertically(tween(HistouryMotion.DurationShort)),
        exit = fadeOut(tween(HistouryMotion.DurationShort)) + shrinkVertically(tween(HistouryMotion.DurationShort)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 4.dp, top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            if (status == AsyncCheck.CHECKING) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Checking…", color = TextSecondary, fontSize = 12.5.sp)
            } else {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(availableText, color = Success, fontSize = 12.5.sp)
            }
        }
    }
}

/**
 * "120 / 500" under a text area. Turns red over the limit (and a little
 * before it, as a heads-up) so the limit is never a surprise on submit.
 */
@Composable
fun CharacterCounter(
    length: Int,
    max: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$length / $max",
        color = when {
            length > max -> Danger
            length >= max * 0.9 -> com.histoury.app.theme.AccentGold
            else -> TextTertiary
        },
        fontSize = 11.5.sp,
        modifier = modifier.padding(top = 4.dp)
    )
}

