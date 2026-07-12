package com.arashivision.sdk.demo.data.sensor.di

import android.content.Context
import com.arashivision.sdk.demo.core.sensorfusion.SensorFusionEngine
import com.arashivision.sdk.demo.data.sensor.AndroidGazeRepository
import com.arashivision.sdk.demo.domain.repository.GazeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensorDataModule {
    @Provides
    @Singleton
    fun provideSensorFusionEngine(): SensorFusionEngine = SensorFusionEngine()

    @Provides
    @Singleton
    fun provideGazeRepository(
        @ApplicationContext context: Context,
        engine: SensorFusionEngine,
    ): GazeRepository = AndroidGazeRepository(context, engine)
}
