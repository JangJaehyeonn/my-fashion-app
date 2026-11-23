package com.fashionapp.di

import com.fashionapp.data.datastore.AppOpenPrefs
import com.fashionapp.data.datastore.AppOpenStore
import com.fashionapp.data.datastore.TokenDataStore
import com.fashionapp.data.datastore.TokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    abstract fun bindTokenStore(impl: TokenDataStore): TokenStore

    @Binds
    abstract fun bindAppOpenStore(impl: AppOpenPrefs): AppOpenStore
}
