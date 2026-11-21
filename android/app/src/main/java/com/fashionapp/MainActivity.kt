package com.fashionapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fashionapp.ui.navigation.AppNavigation
import com.fashionapp.ui.navigation.MainViewModel
import com.fashionapp.ui.theme.FashionAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 회전 등으로 Activity가 다시 만들어질 때 같은 로그인 딥링크를 다시 처리하지 않도록 최초 생성 시에만
        if (savedInstanceState == null) intent?.data?.let { mainViewModel.handleDeepLink(it) }
        setContent {
            FashionAppTheme {
                AppNavigation(mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { mainViewModel.handleDeepLink(it) }
    }
}
