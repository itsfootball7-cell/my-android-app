package com.nitro.tvplayer.di

import android.content.Context
import com.nitro.tvplayer.data.api.XtremeCodesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        // ── 50MB HTTP cache on disk ──
        val cacheDir  = File(context.cacheDir, "http_cache")
        val httpCache = Cache(cacheDir, 50L * 1024 * 1024)

        return OkHttpClient.Builder()
            .cache(httpCache)
            // ── Keep 10 connections alive for 5 minutes ──
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            // ── Tight timeouts for IPTV APIs ──
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // ── Cache responses for 5 minutes ──
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Cache-Control", "max-age=300")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): XtremeCodesApi =
        retrofit.create(XtremeCodesApi::class.java)
}
