package com.fashionapp.di

import com.fashionapp.data.location.DeviceLocation
import com.fashionapp.data.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    abstract fun bindDeviceLocation(impl: LocationProvider): DeviceLocation
}
