package mihon.kit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ArrowDownward
import mihon.icons.materialsymbols.rounded.ArrowUpward
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * BottomSheet Dialog that displays Mihon / Tachiyomi filters dynamically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionFilterBottomSheet(
    filters: FilterList,
    onDismissRequest: () -> Unit,
    onApply: (FilterList) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var refreshKey by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    onReset()
                    refreshKey++
                }) {
                    Text("Reset")
                }

                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                )

                Button(onClick = {
                    onApply(filters)
                    onDismissRequest()
                }) {
                    Text("Apply")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Scrollable dynamic filter list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = filters.size,
                    key = { index -> "${filters[index].name}_$index" },
                ) { index ->
                    FilterItemView(
                        filter = filters[index],
                        refreshKey = refreshKey,
                        onUpdated = { refreshKey++ },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Renders any individual Mihon Filter item dynamically.
 */
@Composable
fun FilterItemView(
    filter: Filter<*>,
    refreshKey: Int,
    onUpdated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (filter) {
        is Filter.Header -> {
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        is Filter.Separator -> {
            HorizontalDivider(modifier = modifier.padding(vertical = 6.dp))
        }

        is Filter.CheckBox -> {
            var checkedState by remember(filter, refreshKey) { mutableStateOf(filter.state) }

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !checkedState
                        filter.state = newState
                        checkedState = newState
                        onUpdated()
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checkedState,
                    onCheckedChange = {
                        filter.state = it
                        checkedState = it
                        onUpdated()
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = filter.name, style = MaterialTheme.typography.bodyLarge)
            }
        }

        is Filter.TriState -> {
            var triState by remember(filter, refreshKey) { mutableIntStateOf(filter.state) }

            val cycleState = {
                val nextState = when (triState) {
                    Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                    Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                    else -> Filter.TriState.STATE_IGNORE
                }
                filter.state = nextState
                triState = nextState
                onUpdated()
            }

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { cycleState() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(
                    state = when (triState) {
                        Filter.TriState.STATE_INCLUDE -> ToggleableState.On
                        Filter.TriState.STATE_EXCLUDE -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { cycleState() },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = filter.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                when (triState) {
                    Filter.TriState.STATE_INCLUDE -> Icon(MaterialSymbols.Rounded.Check, contentDescription = "Included", tint = MaterialTheme.colorScheme.primary)
                    Filter.TriState.STATE_EXCLUDE -> Icon(MaterialSymbols.Rounded.Close, contentDescription = "Excluded", tint = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        }

        is Filter.Select<*> -> {
            var expanded by remember { mutableStateOf(false) }
            val options = filter.values
            var selectedIndex by remember(filter, refreshKey) { mutableIntStateOf(filter.state) }

            Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable { expanded = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = options.getOrNull(selectedIndex)?.toString() ?: "None",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Icon(MaterialSymbols.Rounded.KeyboardArrowDown, contentDescription = "Dropdown")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option.toString()) },
                                onClick = {
                                    filter.state = index
                                    selectedIndex = index
                                    expanded = false
                                    onUpdated()
                                },
                            )
                        }
                    }
                }
            }
        }

        is Filter.Text -> {
            var textState by remember(filter, refreshKey) { mutableStateOf(filter.state) }

            OutlinedTextField(
                value = textState,
                onValueChange = {
                    filter.state = it
                    textState = it
                    onUpdated()
                },
                label = { Text(filter.name) },
                singleLine = true,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        is Filter.Sort -> {
            var sortState by remember(filter, refreshKey) { mutableStateOf(filter.state) }

            Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                filter.values.forEachIndexed { index, sortOptionName ->
                    val isSelected = sortState?.index == index
                    val isAscending = sortState?.ascending == true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newAscending = if (isSelected) !isAscending else true
                                val newSort = Filter.Sort.Selection(index, newAscending)
                                filter.state = newSort
                                sortState = newSort
                                onUpdated()
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = sortOptionName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = if (isAscending) MaterialSymbols.Rounded.ArrowUpward else MaterialSymbols.Rounded.ArrowDownward,
                                contentDescription = if (isAscending) "Ascending" else "Descending",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        is Filter.Group<*> -> {
            var isExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = filter.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                imageVector = if (isExpanded) MaterialSymbols.Rounded.KeyboardArrowUp else MaterialSymbols.Rounded.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            filter.state.filterIsInstance<Filter<*>>().forEach { childFilter ->
                                FilterItemView(
                                    filter = childFilter,
                                    refreshKey = refreshKey,
                                    onUpdated = onUpdated,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
