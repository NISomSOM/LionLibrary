package com.singam.lionlibrary.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun NavigationRailBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.weight(1f))
        topLevelDestinations.forEach { item ->
            NavigationRailItem(
                selected = entry?.destination?.hasRoute(item.route::class) == true,
                onClick = { navController.openTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        stringResource(item.label),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
