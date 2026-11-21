package com.fashionapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.fashionapp.data.api.AuthInterceptor
import com.fashionapp.data.api.TokenAuthenticator
import com.fashionapp.data.api.imageOkHttpClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FashionApp : Application(), ImageLoaderFactory {

    @Inject lateinit var authInterceptor: AuthInterceptor
    @Inject lateinit var tokenAuthenticator: TokenAuthenticator

    // 모든 AsyncImage가 쓰는 앱 전역 로더. 백엔드의 /api/images/... 요청에만 Authorization 헤더를 붙인다
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient(authInterceptor, tokenAuthenticator))
            .build()
}
