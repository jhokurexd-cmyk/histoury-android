@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.histoury.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.histoury.app.theme.Outline
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.PrimarySoft
import com.histoury.app.theme.SurfaceSoft
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.screens.legal.LegalContent

/**
 * Modal for reviewing the Terms & Conditions and Privacy Policy before
 * consenting. Both documents live in one sheet behind a segmented tab
 * switcher instead of two separate screens, so reading either (or both)
 * only takes one open/close.
 *
 * [onDismiss] fires for every way this can close — the "Got it" button,
 * the X, tapping the scrim, or swiping the sheet down — since opening it
 * at all is the acknowledgment this is gating.
 */
@Composable
fun ConsentModalSheet(
    onDismiss: () -> Unit
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var selectedType by remember { mutableStateOf(LegalContent.TYPE_TERMS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        dragHandle = null
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {

            // Grab handle
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Outline)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Terms & Privacy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceSoft)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(PrimarySoft)
                    .padding(4.dp)
            ) {

                ConsentTab(
                    label = "Terms & Conditions",
                    isSelected = selectedType == LegalContent.TYPE_TERMS,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = LegalContent.TYPE_TERMS }
                )

                ConsentTab(
                    label = "Privacy Policy",
                    isSelected = selectedType == LegalContent.TYPE_PRIVACY,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = LegalContent.TYPE_PRIVACY }
                )
            }

            val document = remember(selectedType) { LegalContent.documentFor(selectedType) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {

                item {

                    Text(
                        text = "Effective ${document.effectiveDate}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = document.intro,
                        fontSize = 13.5.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(18.dp))
                }

                items(document.sections.size) { index ->

                    val section = document.sections[index]

                    Text(
                        text = section.heading,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = section.body,
                        fontSize = 13.5.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(16.dp))
                }

                item {

                    Text(
                        text = document.closing,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                PrimaryButton(
                    text = "Got It, Continue",
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ConsentTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) Primary else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else TextSecondary,
            maxLines = 1
        )
    }
}
