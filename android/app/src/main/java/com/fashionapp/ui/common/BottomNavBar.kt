package com.fashionapp.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fashionapp.ui.navigation.Route

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem(Route.WARDROBE, "옷장", Icons.Default.Checkroom),
        BottomNavItem(Route.RECOMMEND, "추천", Icons.Default.Star),
        BottomNavItem(Route.CALENDAR, "캘린더", Icons.Default.CalendarMonth),
        BottomNavItem(Route.MYPAGE, "마이", Icons.Default.Person),
    )

    val currentRoute by navController.currentBackStackEntryAsState().let {
        val state = it.value
        androidx.compose.runtime.remember(state) { androidx.compose.runtime.mutableStateOf(state?.destination?.route) }
    }

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Route.WARDROBE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
