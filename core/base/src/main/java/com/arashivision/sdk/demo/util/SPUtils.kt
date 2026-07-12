package com.arashivision.sdk.demo.util

import android.content.Context
import android.content.SharedPreferences
import com.arashivision.sdk.demo.base.AppContext

object SPUtils {
    private const val INSTA_SP_NAME = "insta_sp"

    private val sp: SharedPreferences by lazy {
        AppContext.application.getSharedPreferences(
            INSTA_SP_NAME,
            Context.MODE_PRIVATE,
        )
    }

    private val editor: SharedPreferences.Editor by lazy { sp.edit() }

    fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = sp.getBoolean(key, defValue)

    fun putString(
        key: String,
        value: String,
    ) {
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(
        key: String,
        defValue: String,
    ): String = sp.getString(key, defValue) ?: ""

    fun putInt(
        key: String,
        value: Int,
    ) {
        editor.putInt(key, value)
        editor.apply()
    }

    fun getInt(
        key: String,
        defValue: Int,
    ): Int = sp.getInt(key, defValue)

    fun putFloat(
        key: String,
        value: Float,
    ) {
        editor.putFloat(key, value)
        editor.apply()
    }

    fun getFloat(
        key: String,
        defValue: Float,
    ): Float = sp.getFloat(key, defValue)

    fun putLong(
        key: String,
        value: Long,
    ) {
        editor.putLong(key, value)
        editor.apply()
    }

    fun getLong(
        key: String,
        defValue: Long,
    ): Long = sp.getLong(key, defValue)

    fun remove(key: String) {
        editor.remove(key)
        editor.apply()
    }

    fun clear() {
        editor.clear()
        editor.apply()
    }

    fun loadUserSettings(context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", "default_user")
        val notificationsEnabled = sharedPreferences.getBoolean("notifications", true)
        val theme = sharedPreferences.getString("theme", "light")
    }
}
