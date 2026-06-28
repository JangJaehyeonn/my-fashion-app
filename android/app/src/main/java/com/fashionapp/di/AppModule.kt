package com.fashionapp.di

import com.fashionapp.data.api.ApiClient
import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.OutfitApi
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
    fun provideClothesApi(apiClient: ApiClient): ClothesApi =
        apiClient.retrofit.create(ClothesApi::class.java)

    @Provides
    @Singleton
    fun provideOutfitApi(apiClient: ApiClient): OutfitApi =
        apiClient.retrofit.create(OutfitApi::class.java)
}
