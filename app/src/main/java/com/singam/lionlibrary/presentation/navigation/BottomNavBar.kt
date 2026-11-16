package com.singam.lionlibrary.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion

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

/**
 * [compact] (while the page scrolls down) hides labels and narrows the bar; scrolling up or
 * reaching the top restores it. Above 140% text scale the bar never compacts: labels stay visible.
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val entry by navController.currentBackStackEntryAsState()
    val selectedIndex = topLevelDestinations.indexOfFirst {
        entry?.destination?.hasRoute(it.route::class) == true
    }
    BottomNavBarContent(
        selectedIndex,
        { navController.openTopLevel(topLevelDestinations[it].route) },
        modifier,
        compact,
    )
}

@Composable
internal fun BottomNavBarContent(
    selectedIndex: Int,
    onDestinationClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val enlargedText = LocalDensity.current.fontScale > 1.4f
    val collapsed = compact && !enlargedText
    val maxWidth by
        animateDpAsState(
            if (enlargedText) 600.dp else if (collapsed) 208.dp else 280.dp,
            tween(LionMotion.Medium, easing = LionMotion.Easing),
            label = "navWidth",
        )
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier =
                Modifier.align(Alignment.Center)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .animateContentSize(tween(LionMotion.Medium, easing = LionMotion.Easing)),
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
                    showLabels = !collapsed,
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
    showLabels: Boolean = true,
) {
    NavigationBar(
        modifier =
            Modifier.height(
                when {
                    LocalDensity.current.fontScale > 1.4f -> 80.dp
                    showLabels -> 64.dp
                    else -> 52.dp
                }
            ),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        indices.forEach { index ->
            val item = topLevelDestinations[index]
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onDestinationClick(index) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = if (showLabels) null else stringResource(item.label),
                        modifier = Modifier.size(22.dp),
                    )
                },
                // Hidden labels stay available to accessibility through the icon description.
                label =
                    if (showLabels) {
                        {
                            Text(
                                stringResource(item.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else null,
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

@Preview(name = "Home selected")
@Composable
private fun HomeNavigationPreview() {
    LionLibraryTheme { BottomNavBarContent(0, {}) }
}

@Preview(name = "Library selected")
@Composable
private fun LibraryNavigationPreview() {
    LionLibraryTheme { BottomNavBarContent(1, {}) }
}

@Preview(
    name = "Settings selected, enlarged text",
    fontScale = 2f,
    widthDp = 360,
)
@Composable
private fun SettingsNavigationPreview() {
    LionLibraryTheme { BottomNavBarContent(2, {}) }
}

@Preview(name = "Compact while scrolling")
@Composable
private fun CompactNavigationPreview() {
    LionLibraryTheme { BottomNavBarContent(0, {}, compact = true) }
}

@Preview(name = "Compact request ignored at large text", fontScale = 2f, widthDp = 360)
@Composable
private fun CompactLargeTextNavigationPreview() {
    LionLibraryTheme { BottomNavBarContent(1, {}, compact = true) }
}
