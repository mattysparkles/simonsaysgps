package com.simonsaysgps.di

import com.simonsaysgps.BuildConfig
import com.simonsaysgps.data.remote.NominatimApi
import com.simonsaysgps.data.remote.OsrmApi
import com.simonsaysgps.data.repository.DataStoreSettingsRepository
import com.simonsaysgps.data.repository.NominatimGeocodingRepository
import com.simonsaysgps.data.repository.OsrmRoutingRepository
import com.simonsaysgps.domain.repository.GeocodingRepository
import com.simonsaysgps.domain.repository.RoutingRepository
import com.simonsaysgps.domain.repository.SettingsRepository
import com.simonsaysgps.domain.service.NavigationForegroundServiceController
import com.simonsaysgps.domain.service.VoicePromptManager
import com.simonsaysgps.service.AndroidNavigationForegroundServiceController
import com.simonsaysgps.service.AndroidVoicePromptManager
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "SimonSaysGPS/1.0 (https://github.com/example/simonsaysgps)")
                    .build()
            )
        }
        .build()

    @Provides
    @Singleton
    @Named("nominatim")
    fun provideNominatimRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.NOMINATIM_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    @Named("osrm")
    fun provideOsrmRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.OSRM_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideNominatimApi(@Named("nominatim") retrofit: Retrofit): NominatimApi = retrofit.create(NominatimApi::class.java)

    @Provides
    @Singleton
    fun provideOsrmApi(@Named("osrm") retrofit: Retrofit): OsrmApi = retrofit.create(OsrmApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindRoutingRepository(impl: OsrmRoutingRepository): RoutingRepository

    @Binds
    abstract fun bindGeocodingRepository(impl: NominatimGeocodingRepository): GeocodingRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindVoicePromptManager(impl: AndroidVoicePromptManager): VoicePromptManager

    @Binds
    abstract fun bindNavigationForegroundServiceController(impl: AndroidNavigationForegroundServiceController): NavigationForegroundServiceController
}
