package com.rhulhiking.attendance.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.rhulhiking.attendance.auth.AccessTokenProvider
import com.rhulhiking.attendance.auth.GoogleAuthManager
import com.rhulhiking.attendance.sheets.RestSheetsRepository
import com.rhulhiking.attendance.sheets.SheetsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        // Fail fast on bad signal so taps get queued instead of hanging.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    fun provideTokenProvider(auth: GoogleAuthManager): AccessTokenProvider = auth

    @Provides
    @Singleton
    fun provideSheetsRepository(client: OkHttpClient, tokens: AccessTokenProvider): SheetsRepository =
        RestSheetsRepository(client, tokens, RestSheetsRepository.DEFAULT_BASE_URL.toHttpUrl()) { delay(it) }
}
