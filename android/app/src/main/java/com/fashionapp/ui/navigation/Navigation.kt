package com.fashionapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fashionapp.ui.common.BottomNavBar
import com.fashionapp.ui.diagnosis.DiagnosisScreen
import com.fashionapp.ui.login.LoginScreen
import com.fashionapp.ui.mypage.BodyProfileScreen
import com.fashionapp.ui.mypage.MyPageScreen
import com.fashionapp.ui.recommend.RecommendScreen
import com.fashionapp.ui.shopping.ShoppingScreen

object Route {
    const val LOGIN = "login"
    const val RECOMMEND = "recommend"
    const val DIAGNOSIS = "diagnosis"
    const val SHOPPING = "shopping"
    const val MYPAGE = "mypage"
    const val BODY_PROFILE = "body_profile"
}

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        mainViewModel.checkLoginState { loggedIn ->
            startDestination = if (loggedIn) Route.RECOMMEND else Route.LOGIN
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.navEvent.collect { event ->
            when (event) {
                NavEvent.ToMain -> navController.navigate(Route.RECOMMEND) {
                    popUpTo(Route.LOGIN) { inclusive = true }
                }
                NavEvent.ToLogin -> navController.navigate(Route.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in listOf(Route.RECOMMEND, Route.DIAGNOSIS, Route.SHOPPING, Route.MYPAGE)

    startDestination?.let { start ->
        Scaffold(
            bottomBar = {
                if (showBottomBar) BottomNavBar(navController = navController)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = start,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Route.LOGIN) { LoginScreen() }
                composable(Route.RECOMMEND) { RecommendScreen() }
                composable(Route.DIAGNOSIS) { DiagnosisScreen() }
                composable(Route.SHOPPING) { ShoppingScreen() }
                composable(Route.MYPAGE) {
                    MyPageScreen(
                        onLogout = {
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEditBodyProfile = { navController.navigate(Route.BODY_PROFILE) }
                    )
                }
                composable(Route.BODY_PROFILE) {
                    BodyProfileScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
