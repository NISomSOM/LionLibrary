package com.singam.lionlibrary.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import kotlin.math.roundToInt

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
 * Two states only: full (labels, 280 x 64 dp) and [compact] (icons, 208 x 52 dp) while the page
 * scrolls down. One progress value drives width, height and the labels together, so the bar can
 * never rest between the two. Above 140% text scale the bar never compacts: labels stay visible.
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
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (enlargedText) {
            // At large text sizes, preserve whole labels and all three destinations.
            // Shrinking text or hiding labels would defeat the user's accessibility setting.
            FloatingBarSurface(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Column {
                    LargeTextDestinationBar(listOf(0, 1), selectedIndex, onDestinationClick)
                    LargeTextDestinationBar(listOf(2), selectedIndex, onDestinationClick)
                }
            }
        } else {
            CollapsingDestinationBar(selectedIndex, onDestinationClick, compact)
        }
    }
}

@Composable
private fun FloatingBarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)),
        content = content,
    )
}

@Composable
private fun CollapsingDestinationBar(
    selectedIndex: Int,
    onDestinationClick: (Int) -> Unit,
    compact: Boolean,
) {
    // 0 = full, 1 = compact. Read in layout and draw only, so the animation never recomposes.
    val collapse =
        animateFloatAsState(
            if (compact) 1f else 0f,
            tween(LionMotion.Medium, easing = LionMotion.Easing),
            label = "navCollapse",
        )
    // Labels leave the tree only once fully hidden; their names then move to the icons.
    val labelsVisible by remember { derivedStateOf { collapse.value < 1f } }
    FloatingBarSurface(
        Modifier.layout { measurable, constraints ->
            val progress = collapse.value
            val width = lerp(BarWidth, CompactBarWidth, progress).roundToPx()
            val height = lerp(BarHeight, CompactBarHeight, progress).roundToPx()
            val placeable =
                measurable.measure(
                    Constraints.fixed(width.coerceAtMost(constraints.maxWidth), height)
                )
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    ) {
        Row(Modifier.fillMaxSize().selectableGroup()) {
            topLevelDestinations.forEachIndexed { index, item ->
                CollapsingDestination(
                    item = item,
                    selected = selectedIndex == index,
                    labelVisible = labelsVisible,
                    collapse = { collapse.value },
                    onClick = { onDestinationClick(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.CollapsingDestination(
    item: TopLevelDestination,
    selected: Boolean,
    labelVisible: Boolean,
    collapse: () -> Float,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(item.label)
    val tint by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            tween(LionMotion.Fast),
            label = "navTint",
        )
    val indicator by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            tween(LionMotion.Fast),
            label = "navIndicator",
        )
    Column(
        Modifier.weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(IndicatorWidth, IndicatorHeight)
                .clip(CircleShape)
                .drawBehind { drawRect(indicator) }
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                // Hidden labels stay available to accessibility through the icon description.
                contentDescription = if (labelVisible) null else label,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
        if (labelVisible) {
            Text(
                label,
                // The label gives up its height and fades as the bar collapses, so the icon
                // glides to the centre instead of the text being cut by the shrinking bar.
                Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val progress = collapse()
                        val height = (placeable.height * (1f - progress)).roundToInt()
                        layout(placeable.width, height) {
                            placeable.placeWithLayer(0, 0) {
                                alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                            }
                        }
                    }
                    .padding(top = 4.dp),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun LargeTextDestinationBar(
    indices: List<Int>,
    selectedIndex: Int,
    onDestinationClick: (Int) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.height(80.dp),
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
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = {
                    Text(
                        stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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

private val BarWidth = 280.dp
private val CompactBarWidth = 208.dp
private val BarHeight = 64.dp
private val CompactBarHeight = 52.dp
private val IndicatorWidth = 56.dp
private val IndicatorHeight = 32.dp

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
