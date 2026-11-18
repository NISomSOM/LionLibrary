package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

/**
 * A compact filter button that opens a menu below it, in place of a row of chips. [label] says what
 * is chosen; [active] highlights the button when it narrows or reorders the results. The menu's
 * items receive a `close` callback: single-choice items call it, multi-choice items leave it open.
 */
@Composable
internal fun LibraryDropdown(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    // Compose-internal: whether the menu is open has no meaning outside this button.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        LibraryFilterChip(
            selected = active,
            onClick = { expanded = true },
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Long genre lists scroll inside the menu instead of covering the whole screen.
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            items { expanded = false }
        }
    }
}

/** One choice in a [LibraryDropdown]; a check marks the selected ones. */
@Composable
internal fun LibraryDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier =
            modifier.semantics {
                role = Role.Checkbox
                this.selected = selected
            },
        leadingIcon = {
            Box(Modifier.size(20.dp)) {
                if (selected)
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

@Preview(name = "Dropdown buttons")
@Preview(name = "Dropdown buttons, large text", widthDp = 320, fontScale = 2f)
@Composable
private fun LibraryDropdownPreview() {
    LionLibraryTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryDropdown("Title A–Z", active = false) {}
            LibraryDropdown("Drama +2", active = true) {}
        }
    }
}

@Preview(name = "Menu items")
@Composable
private fun LibraryDropdownItemPreview() {
    LionLibraryTheme {
        Column {
            LibraryDropdownItem("All", selected = false, onClick = {})
            LibraryDropdownItem("Drama", selected = true, onClick = {})
            LibraryDropdownItem("Science Fiction & Fantasy", selected = false, onClick = {})
        }
    }
}
