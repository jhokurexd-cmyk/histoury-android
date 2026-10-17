package com.histoury.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.histoury.app.theme.CardSurface
import com.histoury.app.theme.OnColor
import com.histoury.app.theme.Outline
import com.histoury.app.theme.Primary
import com.histoury.app.theme.TextPrimary
import com.histoury.app.theme.TextSecondary
import com.histoury.app.ui.components.SearchBar
import com.histoury.app.util.SearchMatcher
import com.histoury.app.util.UserDistance

/** How the builder's lists are ordered. Nearest first by default. */
enum class BuilderSort(val label: String, val icon: ImageVector) {
    NEAREST("Nearest", Icons.Default.NearMe),
    NAME("A–Z", Icons.Default.SortByAlpha),
    // Historical sites only (places have no ratings).
    TOP_RATED("Top rated", Icons.Default.Star)
}

/** One category chip ("Parks · 4"). */
data class BuilderCategoryChip(val key: String, val label: String, val icon: ImageVector?, val count: Int)

/**
 * Search box, sort chips and (optionally) category chips above a builder
 * list. Holds no state: the step keeps it (rememberSaveable), so it
 * survives rotation and opening a stop's details.
 */
@Composable
fun BuilderListControls(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    sort: BuilderSort,
    onSortChange: (BuilderSort) -> Unit,
    hasLocation: Boolean,
    resultCount: Int,
    modifier: Modifier = Modifier,
    categories: List<BuilderCategoryChip> = emptyList(),
    selectedCategory: String? = null,
    onCategoryChange: (String?) -> Unit = {},
    sortOptions: List<BuilderSort> = listOf(BuilderSort.NEAREST, BuilderSort.NAME)
) {

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        Spacer(Modifier.height(4.dp))

        // Search and sort share one row, so the list starts high on the
        // screen instead of under three stacked rows of controls.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                showFilterButton = false,
                height = 46.dp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            SortMenuButton(sort = sort, options = sortOptions, onSortChange = onSortChange)
        }

        if (categories.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuilderChip(
                    label = "All \u00B7 ${categories.sumOf { it.count }}",
                    icon = null,
                    selected = selectedCategory == null,
                    compact = true,
                    onClick = { onCategoryChange(null) }
                )
                categories.forEach { chip ->
                    BuilderChip(
                        label = "${chip.label} \u00B7 ${chip.count}",
                        icon = chip.icon,
                        selected = selectedCategory == chip.key,
                        compact = true,
                        onClick = { onCategoryChange(if (selectedCategory == chip.key) null else chip.key) }
                    )
                }
            }
        }

        // One small line, only when it says something: how many matched a
        // search, or why "Nearest" is showing A-Z.
        val note = when {
            sort == BuilderSort.NEAREST && !hasLocation ->
                "Turn on location to sort by distance \u2014 showing A\u2013Z for now."
            query.isNotBlank() -> if (resultCount == 1) "1 match" else "$resultCount matches"
            else -> null
        }
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = note, fontSize = 11.5.sp, color = TextSecondary)
        }
    }
}

/** "Nearest ▾": the current order; tap for the list of orders. */
@Composable
private fun SortMenuButton(
    sort: BuilderSort,
    options: List<BuilderSort>,
    onSortChange: (BuilderSort) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .border(1.dp, Outline, RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Change order"
                ) { open = true }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = sort.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = sort.label,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(option.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = Primary, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        onSortChange(option)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BuilderChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Primary else CardSurface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Outline, RoundedCornerShape(50)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) OnColor else Primary,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            color = if (selected) OnColor else TextPrimary,
            fontSize = if (compact) 11.5.sp else 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Search, then order: nearest first (when the visitor's position is known,
 * otherwise A–Z), or A–Z. Search uses the same word-start rules as Home.
 */
fun <T> builderFilterAndSort(
    items: List<T>,
    query: String,
    sort: BuilderSort,
    from: LatLng?,
    name: (T) -> String,
    latitude: (T) -> Double?,
    longitude: (T) -> Double?,
    location: (T) -> String = { "" },
    description: (T) -> String = { "" },
    rating: (T) -> Double = { 0.0 }
): List<T> {

    val matching = if (query.isBlank()) items else items.filter {
        SearchMatcher.score(query, name(it), location(it), description(it)) > 0
    }

    val byName = compareBy<T> { SearchMatcher.normalize(name(it)) }

    if (sort == BuilderSort.TOP_RATED) {
        return matching.sortedWith(compareByDescending<T> { rating(it) }.then(byName))
    }

    if (sort == BuilderSort.NAME || from == null) return matching.sortedWith(byName)

    // Unknown coordinates go last rather than first.
    return matching.sortedWith(
        compareBy<T> { UserDistance.metersTo(latitude(it), longitude(it), from) ?: Float.MAX_VALUE }
            .then(byName)
    )
}

/** A place's category for filtering: cafés and restaurants are both "food". */
fun placeCategoryGroup(category: String): String = when (category.lowercase()) {
    "food", "cafe", "restaurant" -> "food"
    else -> category.lowercase()
}
