package com.fashionapp.di

import com.fashionapp.data.api.ApiClient
import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.DiagnosisApi
import com.fashionapp.data.api.OutfitApi
import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.api.ShoppingApi
import com.fashionapp.data.api.VtonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthApi(apiClient: ApiClient): AuthApi =
        apiClient.retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOutfitApi(apiClient: ApiClient): OutfitApi =
        apiClient.retrofit.create(OutfitApi::class.java)

    @Provides
    @Singleton
    fun provideClothesApi(apiClient: ApiClient): ClothesApi =
        apiClient.retrofit.create(ClothesApi::class.java)

    @Provides
    @Singleton
    fun provideDiagnosisApi(apiClient: ApiClient): DiagnosisApi =
        apiClient.retrofit.create(DiagnosisApi::class.java)

    @Provides
    @Singleton
    fun provideShoppingApi(apiClient: ApiClient): ShoppingApi =
        apiClient.retrofit.create(ShoppingApi::class.java)

    // Injected so date formatting in ViewModels is testable with a fixed zone
    @Provides
    fun provideZoneId(): java.time.ZoneId = java.time.ZoneId.systemDefault()

    @Provides
    @Singleton
    fun providePurchaseCheckApi(apiClient: ApiClient): PurchaseCheckApi =
        apiClient.retrofit.create(PurchaseCheckApi::class.java)

    @Provides
    @Singleton
    fun provideVtonApi(apiClient: ApiClient): VtonApi =
        apiClient.retrofit.create(VtonApi::class.java)
}
