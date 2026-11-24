package com.fashionapp.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fashionapp.ui.closet.ClosetScreen
import com.fashionapp.ui.closet.ClosetViewModel
import com.fashionapp.ui.closet.ClothesDetailScreen
import com.fashionapp.ui.closet.ClothesDetailViewModel
import com.fashionapp.ui.common.BottomNavBar
import com.fashionapp.ui.fitting.FittingScreen
import com.fashionapp.ui.diagnosis.DiagnosisScreen
import com.fashionapp.ui.home.HomeScreen
import com.fashionapp.ui.login.LoginScreen
import com.fashionapp.ui.mypage.BodyProfileScreen
import com.fashionapp.ui.mypage.ChangePasswordScreen
import com.fashionapp.ui.mypage.CommuteSettingsScreen
import com.fashionapp.ui.mypage.MyPageScreen
import com.fashionapp.ui.onboarding.OnboardingScreen
import com.fashionapp.ui.purchase.PurchaseCheckScreen
import com.fashionapp.ui.purchase.PurchaseCheckViewModel
import com.fashionapp.ui.purchase.PurchaseSource
import com.fashionapp.ui.shopping.ShoppingScreen
import com.fashionapp.ui.signup.AgeGateScreen
import com.fashionapp.ui.signup.SignupScreen
import com.fashionapp.ui.vton.VtonScreen

object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CLOSET = "closet"
    const val FITTING = "fitting"
    const val DIAGNOSIS = "diagnosis"
    const val SHOPPING = "shopping"
    const val MYPAGE = "mypage"
    const val BODY_PROFILE = "body_profile"
    const val SIGNUP = "signup"
    const val CHANGE_PASSWORD = "change_password"
    const val COMMUTE_SETTINGS = "commute_settings"
    const val AGE_GATE = "age_gate"
    const val ONBOARDING = "onboarding"
    const val VTON = "vton/{garmentDesc}"
    // Argument name must match ClothesDetailViewModel.ARG_CLOTHES_ID
    const val CLOTHES_DETAIL = "clothes/{clothesId}"
    // Argument name must match PurchaseCheckViewModel.ARG_SOURCE
    const val PURCHASE_CHECK = "purchase_check/{source}"

    // Set on the closet back stack entry when the detail screen saved or reclassified a clothes
    const val RESULT_CLOTHES_CHANGED = "clothes_changed"

    fun clothesDetail(id: String) = "clothes/${Uri.encode(id)}"

    fun purchaseCheck(source: PurchaseSource) = "purchase_check/${source.name}"

    fun vton(garmentDesc: String) = "vton/${Uri.encode(garmentDesc)}"
}

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        mainViewModel.checkLoginState { loggedIn ->
            startDestination = if (loggedIn) Route.HOME else Route.LOGIN
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in listOf(Route.HOME, Route.CLOSET, Route.SHOPPING, Route.FITTING, Route.MYPAGE)

    // 메인 탭에 들어오면 만 14세 이상 확인 여부를 본다 (소셜 첫 가입·기존 사용자). 확인됐으면 아무 일도 없음
    LaunchedEffect(showBottomBar) {
        if (showBottomBar) {
            mainViewModel.ensureAgeConfirmed()
            // FR-20: APP_OPENED once a day (a failed or not-yet-allowed send is retried next time)
            mainViewModel.reportAppOpened()
        }
    }

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
                composable(Route.LOGIN) {
                    val loginMessage by mainViewModel.loginMessage.collectAsState()
                    val isLoggingIn by mainViewModel.isLoggingIn.collectAsState()
                    LoginScreen(
                        message = loginMessage,
                        isLoggingIn = isLoggingIn,
                        onLoginStart = mainViewModel::clearLoginMessage,
                        onLoggedIn = {
                            navController.navigate(Route.HOME) { popUpTo(0) { inclusive = true } }
                        },
                        onSignUp = { navController.navigate(Route.SIGNUP) }
                    )
                }
                composable(Route.AGE_GATE) {
                    val working by mainViewModel.ageGateWorking.collectAsState()
                    val error by mainViewModel.ageGateError.collectAsState()
                    val agreements by mainViewModel.ageGateAgreements.collectAsState()
                    AgeGateScreen(
                        agreements = agreements,
                        isWorking = working,
                        error = error,
                        onAllChange = mainViewModel::onAgeGateAllChange,
                        onItemChange = mainViewModel::onAgeGateAgreementChange,
                        onConfirm = mainViewModel::confirmAge,
                        onUnder14 = mainViewModel::declineUnder14
                    )
                }
                composable(Route.SIGNUP) {
                    SignupScreen(
                        onBack = { navController.popBackStack() },
                        onSignedUp = {
                            navController.navigate(Route.HOME) { popUpTo(0) { inclusive = true } }
                        }
                    )
                }
                composable(Route.ONBOARDING) {
                    OnboardingScreen(
                        onLater = {
                            navController.navigate(Route.HOME) { popUpTo(0) { inclusive = true } }
                        },
                        onGoShopping = {
                            // Home stays the start of the tab back stack, like a bottom tab switch (BottomNavBar)
                            navController.navigate(Route.HOME) { popUpTo(0) { inclusive = true } }
                            navController.navigate(Route.SHOPPING) {
                                popUpTo(Route.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Route.HOME) {
                    HomeScreen(
                        onGoToCloset = {
                            // 하단 탭 전환과 동일한 방식으로 이동 (BottomNavBar 참고)
                            navController.navigate(Route.CLOSET) {
                                popUpTo(Route.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Route.CLOSET) { entry ->
                    val closetViewModel: ClosetViewModel = hiltViewModel()
                    val changed by entry.savedStateHandle
                        .getStateFlow(Route.RESULT_CLOTHES_CHANGED, false)
                        .collectAsState()
                    LaunchedEffect(changed) {
                        if (changed) {
                            entry.savedStateHandle[Route.RESULT_CLOTHES_CHANGED] = false
                            closetViewModel.loadClothes()
                        }
                    }
                    ClosetScreen(
                        onOpenClothes = { clothes -> navController.navigate(Route.clothesDetail(clothes.id)) },
                        viewModel = closetViewModel
                    )
                }
                composable(
                    route = Route.CLOTHES_DETAIL,
                    arguments = listOf(navArgument(ClothesDetailViewModel.ARG_CLOTHES_ID) { type = NavType.StringType })
                ) {
                    ClothesDetailScreen(
                        onBack = { changed ->
                            // Ignore a second tap while the pop animation runs, so the closet is not popped too
                            if (navController.currentBackStackEntry?.destination?.route != Route.CLOTHES_DETAIL) {
                                return@ClothesDetailScreen
                            }
                            if (changed) {
                                navController.previousBackStackEntry?.savedStateHandle
                                    ?.set(Route.RESULT_CLOTHES_CHANGED, true)
                            }
                            navController.popBackStack()
                        }
                    )
                }
                composable(Route.FITTING) { FittingScreen() }
                composable(Route.DIAGNOSIS) { DiagnosisScreen() }
                composable(Route.SHOPPING) {
                    ShoppingScreen(
                        onCapture = { navController.navigate(Route.purchaseCheck(PurchaseSource.CAMERA)) },
                        onGallery = { navController.navigate(Route.purchaseCheck(PurchaseSource.GALLERY)) },
                        onProductUrl = { navController.navigate(Route.purchaseCheck(PurchaseSource.URL)) },
                        onGoToCloset = {
                            // 하단 탭 전환과 동일한 방식으로 이동 (BottomNavBar 참고)
                            navController.navigate(Route.CLOSET) {
                                popUpTo(Route.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenFitting = { navController.navigate(Route.FITTING) { launchSingleTop = true } }
                    )
                }
                composable(
                    route = Route.PURCHASE_CHECK,
                    arguments = listOf(navArgument(PurchaseCheckViewModel.ARG_SOURCE) { type = NavType.StringType })
                ) {
                    PurchaseCheckScreen(
                        onClose = {
                            // Ignore a second close while the pop animation runs, so the shopping tab is not popped too
                            if (navController.currentBackStackEntry?.destination?.route == Route.PURCHASE_CHECK) {
                                navController.popBackStack()
                            }
                        },
                        // FR-18 입어보기: the result stays below, so back returns to it
                        onOpenFitting = { navController.navigate(Route.FITTING) { launchSingleTop = true } }
                    )
                }
                composable(
                    route = Route.VTON,
                    arguments = listOf(navArgument("garmentDesc") { type = NavType.StringType })
                ) {
                    VtonScreen(onBack = { navController.popBackStack() })
                }
                composable(Route.MYPAGE) {
                    MyPageScreen(
                        onLogout = {
                            mainViewModel.resetAgeCheck()
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEditBodyProfile = { navController.navigate(Route.BODY_PROFILE) },
                        onChangePassword = { navController.navigate(Route.CHANGE_PASSWORD) },
                        onEditCommute = { navController.navigate(Route.COMMUTE_SETTINGS) }
                    )
                }
                composable(Route.COMMUTE_SETTINGS) {
                    CommuteSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable(Route.CHANGE_PASSWORD) {
                    ChangePasswordScreen(
                        onBack = { navController.popBackStack() },
                        onChanged = { navController.popBackStack() }
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

        // NavHost가 그래프를 설정한 뒤에만 이동 이벤트를 받음 — 앱이 꺼진 상태에서 로그인 딥링크로 시작하면
        // 그래프 설정 전에 이벤트가 와서 navigate가 IllegalArgumentException으로 크래시했음.
        // navEvent는 Channel이라 그 전에 보낸 이벤트도 여기서 받아 처리된다
        LaunchedEffect(Unit) {
            mainViewModel.navEvent.collect { event ->
                when (event) {
                    NavEvent.ToMain -> navController.navigate(Route.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                    NavEvent.ToLogin -> navController.navigate(Route.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                    NavEvent.ToAgeGate -> navController.navigate(Route.AGE_GATE) {
                        popUpTo(0) { inclusive = true }
                    }
                    NavEvent.ToOnboarding -> navController.navigate(Route.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
}
