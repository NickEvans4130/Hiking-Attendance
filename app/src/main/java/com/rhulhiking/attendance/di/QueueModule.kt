package com.rhulhiking.attendance.di

import android.content.Context
import androidx.room.Room
import com.rhulhiking.attendance.queue.PendingCheckInDao
import com.rhulhiking.attendance.queue.PendingQueue
import com.rhulhiking.attendance.queue.QueueDatabase
import com.rhulhiking.attendance.queue.RoomPendingQueue
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QueueModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QueueDatabase =
        Room.databaseBuilder(context, QueueDatabase::class.java, "queue.db").build()

    @Provides
    fun provideDao(db: QueueDatabase): PendingCheckInDao = db.pendingCheckIns()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QueueBindings {
    @Binds
    abstract fun bindQueue(impl: RoomPendingQueue): PendingQueue
}
