package com.histoury.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.histoury.app.data.model.Tag
import com.histoury.app.theme.Primary as PrimaryPink
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Outline

// These file-local names predate the theme system; they now alias the
// shared palette so this screen follows dark mode with the rest of the app.

@Composable
fun TagFilterDialog(
    allTags: List<Tag>,
    appliedTagIds: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {

    var draftSelectedTagIds by remember(appliedTagIds) {
        mutableStateOf(appliedTagIds)
    }

    Dialog(onDismissRequest = onDismiss) {

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardSurface
        ) {

            Column(
                modifier = Modifier.padding(24.dp)
            ) {

                Text(
                    text = "Filter by Tags",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    allTags.forEach { tag ->

                        val isChecked = tag.id in draftSelectedTagIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    draftSelectedTagIds = if (isChecked) {
                                        draftSelectedTagIds - tag.id
                                    } else {
                                        draftSelectedTagIds + tag.id
                                    }
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isChecked) PrimaryPink else CardSurface
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isChecked) {
                                            PrimaryPink
                                        } else {
                                            Outline
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {

                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedButton(
                        onClick = {
                            draftSelectedTagIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Reset", color = Color.Black)
                    }

                    Button(
                        onClick = {
                            onApply(draftSelectedTagIds)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPink
                        )
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
