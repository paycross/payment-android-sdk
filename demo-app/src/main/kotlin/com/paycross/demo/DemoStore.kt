package com.paycross.demo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Local persistence for the test harness. Merchants (including their M2M
 * secrets) live in SharedPreferences on the test device — acceptable for
 * staging credentials, never store production secrets here.
 *
 * First run seeds from assets/merchants.json (gitignored; see
 * merchants.json.example).
 */
class DemoStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): DemoData = stored() ?: seed().also(::save)

    fun save(data: DemoData) {
        prefs.edit().putString(KEY_DATA, gson.toJson(data)).apply()
    }

    private fun stored(): DemoData? = prefs.getString(KEY_DATA, null)?.let {
        runCatching { gson.fromJson(it, DemoData::class.java) }.getOrNull()
    }

    private fun seed(): DemoData {
        val text = runCatching {
            context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return DemoData()

        val seeds = runCatching {
            gson.fromJson(text, Array<SeedMerchant>::class.java).toList()
        }.getOrNull() ?: return DemoData()

        val pairs = seeds.map { it.toMerchant() to (it.sandbox == true) }
        val merchants = pairs.map { it.first }
        val scenarios = pairs.flatMap { (merchant, sandbox) ->
            DemoSeeds.scenariosFor(merchant, sandbox)
        }
        return DemoData(merchants, scenarios, merchants.firstOrNull()?.id)
    }

    private data class SeedMerchant(
        val name: String,
        val environment: String?,
        val sandbox: Boolean?,
        @SerializedName("token_url") val tokenUrl: String,
        @SerializedName("client_id") val clientId: String,
        @SerializedName("client_secret") val clientSecret: String,
        @SerializedName("payment_api_url") val paymentApiUrl: String,
        @SerializedName("paycross_version") val paycrossVersion: String
    ) {
        fun toMerchant() = Merchant(
            name = name,
            environment = environment ?: Merchant.ENV_STAGING,
            tokenUrl = tokenUrl,
            clientId = clientId,
            clientSecret = clientSecret,
            paymentApiUrl = paymentApiUrl,
            paycrossVersion = paycrossVersion
        )
    }

    companion object {
        private const val PREFS_NAME = "paycross_demo"
        private const val KEY_DATA = "demo_data"
        private const val SEED_ASSET = "merchants.json"
    }
}
