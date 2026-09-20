package com.histoury.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextSecondary

/**
 * Histoury's content categories — kept in sync with the admin panel's
 * Places Management module (`placeCategories.js`). Adding a category on
 * the admin side means adding the matching entry here too.
 *
 * ALL is optional — Explore uses it to show everything at once; Home's
 * pinned chips skip it since a section is always selected there.
 */
enum class ContentCategory(val key: String, val label: String, val icon: ImageVector) {
    ALL("all", "All", Icons.Default.Apps),
    SITES("sites", "Historical Sites", Icons.Default.AccountBalance),
    FOOD("food", "Food & Drink", Icons.Default.Restaurant),
    PARKS("park", "Parks", Icons.Default.Park),
    HOTELS("hotel", "Hotels", Icons.Default.Hotel),
    SOUVENIR_SHOPS("souvenir_shop", "Souvenir Shops", Icons.Default.Storefront),
    SCHOOLS("school", "Schools", Icons.Default.School),
    BANKS("bank", "Banks", Icons.Default.AccountBalanceWallet)
}

@Composable
fun ContentCategoryChips(
    categories: List<ContentCategory>,
    selectedKey: String,
    onSelect: (ContentCategory) -> Unit,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        categories.forEach { category ->

            val isSelected = category.key == selectedKey

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Primary else CardSurface,
                animationSpec = tween(200),
                label = "chipBackground"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) OnColor else TextSecondary,
                animationSpec = tween(200),
                label = "chipContent"
            )

            val interactionSource = remember { MutableInteractionSource() }

            Row(
                modifier = Modifier
                    .pressScale(interactionSource)
                    .clip(RoundedCornerShape(50))
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        onSelect(category)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = category.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
