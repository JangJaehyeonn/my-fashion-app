package com.fashionapp.ui.navigation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavEvent {
    object ToMain : NavEvent()
    object ToLogin : NavEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navEvent = MutableSharedFlow<NavEvent>()
    val navEvent = _navEvent.asSharedFlow()

    fun handleDeepLink(uri: Uri) {
        if (uri.scheme == "fashionapp" && uri.host == "app") {
            val accessToken = uri.getQueryParameter("accessToken") ?: return
            val refreshToken = uri.getQueryParameter("refreshToken") ?: return
            viewModelScope.launch {
                authRepository.saveTokens(accessToken, refreshToken)
                _navEvent.emit(NavEvent.ToMain)
            }
        }
    }

    fun checkLoginState(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = authRepository.accessToken.firstOrNull()
            onResult(token != null)
        }
    }
}
