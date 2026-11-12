package com.paycross.sdk.internal.api

import com.paycross.sdk.PayCross
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 30L
private const val WRITE_TIMEOUT_SECONDS = 30L

// Request bodies carry PAN/CVV, so no logging interceptor is installed.
internal object ApiClient {
    @Volatile
    private var api: PayCrossApi? = null

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

    // PayCross.init calls this on an environment change, so it is not test-only.
    internal fun reset() {
        api = null
    }
}
