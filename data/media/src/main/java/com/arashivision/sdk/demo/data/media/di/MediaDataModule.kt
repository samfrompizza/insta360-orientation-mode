package com.arashivision.sdk.demo.data.media.di

import com.arashivision.sdk.demo.data.media.Insta360MediaRepository
import com.arashivision.sdk.demo.domain.repository.MediaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaDataModule {
    @Provides
    @Singleton
    fun provideMediaRepository(): MediaRepository = Insta360MediaRepository()
}
