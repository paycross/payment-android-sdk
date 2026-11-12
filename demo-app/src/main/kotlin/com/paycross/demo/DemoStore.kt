package com.paycross.demo

import android.content.Context
import com.google.gson.Gson

/** Local harness persistence. Merchants are entered by hand and kept in SharedPreferences: staging credentials only. Do not store production secrets here. */
class DemoStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): DemoData = prefs.getString(KEY_DATA, null)?.let {
        runCatching { gson.fromJson(it, DemoData::class.java) }.getOrNull()
    } ?: DemoData()

    fun save(data: DemoData) {
        prefs.edit().putString(KEY_DATA, gson.toJson(data)).apply()
    }

    companion object {
        private const val PREFS_NAME = "paycross_demo"
        private const val KEY_DATA = "demo_data"
    }
}
