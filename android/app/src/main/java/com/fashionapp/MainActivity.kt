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
        intent?.data?.let { mainViewModel.handleDeepLink(it) }
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
