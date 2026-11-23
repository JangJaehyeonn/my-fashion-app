package com.fashionapp.di

import com.fashionapp.data.image.JpegUploadImageCompressor
import com.fashionapp.data.image.UploadImageCompressor
import com.fashionapp.data.repository.ProductImageSource
import com.fashionapp.data.repository.ProductPageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Image picking and compression seams, bound to interfaces so ViewModels can be tested with fakes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    @Binds
    abstract fun bindUploadImageCompressor(impl: JpegUploadImageCompressor): UploadImageCompressor

    @Binds
    abstract fun bindProductImageSource(impl: ProductPageRepository): ProductImageSource
}
