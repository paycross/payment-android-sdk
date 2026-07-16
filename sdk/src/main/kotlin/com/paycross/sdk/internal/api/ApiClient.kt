package com.paycross.sdk.internal.api

import androidx.annotation.VisibleForTesting
import com.paycross.sdk.PayCross
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 30L
private const val WRITE_TIMEOUT_SECONDS = 30L

/**
 * Singleton factory for creating and caching the PayCross API client.
 *
 * The API instance is lazily created on first access and cached for reuse.
 * Call [reset] when SDK configuration changes to force recreation.
 *
 * No logging interceptor is installed: request bodies carry PAN/CVV and
 * must never reach logcat.
 */
internal object ApiClient {
    @Volatile
    private var api: PayCrossApi? = null

    /**
     * Returns the cached API instance or creates a new one.
     *
     * @throws IllegalStateException if PayCross SDK is not initialized.
     */
    fun get(): PayCrossApi = api ?: synchronized(this) {
        api ?: createApi().also { api = it }
    }

    private fun createApi(): PayCrossApi {
        val config = PayCross.requireConfig()

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(config.environment.baseUrl + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(PayCrossApi::class.java)
    }

    /**
     * Clears the cached API instance.
     *
     * Call this when SDK configuration changes to ensure the next [get] call
     * creates a fresh instance with updated settings.
     */
    @VisibleForTesting
    internal fun reset() {
        api = null
    }
}
