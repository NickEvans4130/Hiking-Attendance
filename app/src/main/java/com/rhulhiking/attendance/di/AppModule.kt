package com.rhulhiking.attendance.di

import com.rhulhiking.attendance.util.Clock
import com.rhulhiking.attendance.util.SystemClockImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideClock(): Clock = SystemClockImpl
}
