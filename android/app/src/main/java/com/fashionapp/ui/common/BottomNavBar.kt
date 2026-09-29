package com.fashionapp.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fashionapp.ui.navigation.Route
import com.fashionapp.ui.theme.WearonColors

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem(Route.HOME, "홈", Icons.Default.Home),
        BottomNavItem(Route.CLOSET, "옷장", Icons.Default.Checkroom),
        BottomNavItem(Route.FITTING, "피팅", Icons.Default.Style),
        BottomNavItem(Route.MYPAGE, "마이", Icons.Default.Person),
    )

    val currentRoute by navController.currentBackStackEntryAsState().let {
        val state = it.value
        androidx.compose.runtime.remember(state) { androidx.compose.runtime.mutableStateOf(state?.destination?.route) }
    }

    NavigationBar(containerColor = WearonColors.White, tonalElevation = 0.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Route.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WearonColors.Ink,
                    selectedTextColor = WearonColors.Ink,
                    indicatorColor = WearonColors.Beige,
                    unselectedIconColor = WearonColors.SubText,
                    unselectedTextColor = WearonColors.SubText
                )
            )
        }
    }
}
