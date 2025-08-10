package com.arashivision.sdk.demo.util

import android.content.Context
import android.content.SharedPreferences
import com.arashivision.sdk.demo.InstaApp

object SPUtils {

    private const val INSTA_SP_NAME = "insta_sp"

    private val sp: SharedPreferences by lazy {
        InstaApp.instance.getSharedPreferences(
            INSTA_SP_NAME,
            Context.MODE_PRIVATE
        )
    }

    private val editor: SharedPreferences.Editor by lazy { sp.edit() }

    // 保存布尔类型数据
    fun putBoolean(key: String, value: Boolean) {
        editor.putBoolean(key, value)
        editor.apply()
    }

    // 获取布尔类型数据，若不存在则返回默认值
    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return sp.getBoolean(key, defValue)
    }

    // 保存字符串类型数据
    fun putString(key: String, value: String) {
        editor.putString(key, value)
        editor.apply()
    }

    // 获取字符串类型数据，若不存在则返回默认值
    fun getString(key: String, defValue: String): String {
        return sp.getString(key, defValue) ?: ""
    }

    // 保存整型数据
    fun putInt(key: String, value: Int) {
        editor.putInt(key, value)
        editor.apply()
    }

    // 获取整型数据，若不存在则返回默认值
    fun getInt(key: String, defValue: Int): Int {
        return sp.getInt(key, defValue)
    }

    // 保存浮点型数据
    fun putFloat(key: String, value: Float) {
        editor.putFloat(key, value)
        editor.apply()
    }

    // 获取浮点型数据，若不存在则返回默认值
    fun getFloat(key: String, defValue: Float): Float {
        return sp.getFloat(key, defValue)
    }

    // 保存长整型数据
    fun putLong(key: String, value: Long) {
        editor.putLong(key, value)
        editor.apply()
    }

    // 获取长整型数据，若不存在则返回默认值
    fun getLong(key: String, defValue: Long): Long {
        return sp.getLong(key, defValue)
    }

    // 删除指定键的数据
    fun remove(key: String) {
        editor.remove(key)
        editor.apply()
    }

    // 清除所有数据
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
