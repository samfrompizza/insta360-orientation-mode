package com.arashivision.sdk.demo.ui.player

import android.net.Uri
import com.arashivision.sdk.demo.base.BaseViewModel

class LocalSphericalPlayerViewModel : BaseViewModel() {

    var currentVideoUri: Uri? = null
        private set

    var sensorRotationEnabled: Boolean = true
        private set

    fun onVideoSelected(uri: Uri) {
        currentVideoUri = uri
    }

    fun toggleSensorRotation(): Boolean {
        sensorRotationEnabled = !sensorRotationEnabled
        return sensorRotationEnabled
    }
}
