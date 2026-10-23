package com.singam.lionlibrary.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.singam.lionlibrary.R

internal data class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    val icon: ImageVector,
)

internal val topLevelDestinations =
    listOf(
        TopLevelDestination(HomeRoute, R.string.home, Icons.Outlined.Home),
        TopLevelDestination(LibraryRoute(), R.string.library, Icons.Outlined.VideoLibrary),
        TopLevelDestination(SettingsRoute, R.string.settings, Icons.Outlined.Settings),
    )

internal fun NavHostController.openTopLevel(route: Any) {
    // Reselecting a tab must preserve its entry, scroll position and running animations.
    if (currentDestination?.hasRoute(route::class) == true) return
    if (route == HomeRoute) {
        // A stack saved against Home may contain filtered Library. Return to the anchor
        // directly so tapping Home cannot restore another tab in its place.
        if (!popBackStack<HomeRoute>(inclusive = false, saveState = true)) {
            navigate(HomeRoute) { launchSingleTop = true }
        }
        return
    }
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BottomNavBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val selectedIndex = topLevelDestinations.indexOfFirst {
        entry?.destination?.hasRoute(it.route::class) == true
    }
    BottomNavBarContent(
        selectedIndex,
        { navController.openTopLevel(topLevelDestinations[it].route) },
        modifier,
    )
}

@Composable
internal fun BottomNavBarContent(
    selectedIndex: Int,
    onDestinationClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enlargedText = LocalDensity.current.fontScale > 1.4f
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier =
                Modifier.align(Alignment.Center)
                    .widthIn(max = if (enlargedText) 600.dp else 280.dp)
                    .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)),
        ) {
            // At large text sizes, preserve whole labels and all three destinations.
            // Shrinking text or hiding labels would defeat the user's accessibility setting.
            if (enlargedText) {
                Column {
                    DestinationBar(listOf(0, 1), selectedIndex, onDestinationClick)
                    DestinationBar(listOf(2), selectedIndex, onDestinationClick)
                }
            } else {
                DestinationBar(
                    topLevelDestinations.indices.toList(),
                    selectedIndex,
                    onDestinationClick,
                )
            }
        }
    }
}

@Composable
private fun DestinationBar(
    indices: List<Int>,
    selectedIndex: Int,
    onDestinationClick: (Int) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.height(if (LocalDensity.current.fontScale > 1.4f) 80.dp else 64.dp),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        indices.forEach { index ->
            val item = topLevelDestinations[index]
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onDestinationClick(index) },
                icon = {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = {
                    Text(
                        stringResource(item.label),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                alwaysShowLabel = true,
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home selected")
@Composable
private fun HomeNavigationPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { BottomNavBarContent(0, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Library selected")
@Composable
private fun LibraryNavigationPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { BottomNavBarContent(1, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Settings selected, enlarged text",
    fontScale = 2f,
    widthDp = 360,
)
@Composable
private fun SettingsNavigationPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { BottomNavBarContent(2, {}) }
}
