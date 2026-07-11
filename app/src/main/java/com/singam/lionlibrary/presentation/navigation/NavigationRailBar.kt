package com.singam.lionlibrary.presentation.navigation

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.singam.lionlibrary.ui.theme.DarkSurface
import com.singam.lionlibrary.ui.theme.OrangeAccent
import com.singam.lionlibrary.ui.theme.TextSecondary
import com.singam.lionlibrary.ui.theme.TextTertiary

@Composable
fun NavigationRailBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationRail(
        containerColor = DarkSurface,
        modifier = Modifier.fillMaxHeight()
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = OrangeAccent,
                    selectedTextColor = TextSecondary,
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor = DarkSurface
                )
            )
        }
    }
}
