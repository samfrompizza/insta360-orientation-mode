package com.arashivision.sdk.demo.data.camera.di

import com.arashivision.sdk.demo.data.camera.Insta360CameraRepository
import com.arashivision.sdk.demo.domain.repository.CameraRepository
import com.arashivision.sdkcamera.camera.InstaCameraManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraDataModule {
    @Provides
    @Singleton
    fun provideInstaCameraManager(): InstaCameraManager = InstaCameraManager.getInstance()

    @Provides
    @Singleton
    fun provideCameraRepository(): CameraRepository = Insta360CameraRepository()
}
