package com.arashivision.sdk.demo.base

import android.annotation.SuppressLint
import android.app.Application

object AppContext {
    @SuppressLint("StaticFieldLeak")
    lateinit var application: Application
        private set

    fun init(app: Application) {
        application = app
    }
}
