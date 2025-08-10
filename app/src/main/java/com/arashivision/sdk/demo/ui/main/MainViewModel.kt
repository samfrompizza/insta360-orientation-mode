package com.arashivision.sdk.demo.ui.main

import android.app.Activity
import android.os.Build
import com.arashivision.sdk.demo.InstaApp
import com.arashivision.sdk.demo.base.BaseViewModel
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions


class MainViewModel : BaseViewModel() {

    fun checkPermission(activity: Activity) {
        val permission = XXPermissions.with(activity)
            .permission(Permission.Group.BLUETOOTH)
            .permission(Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION)
        if (InstaApp.instance.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
            permission.permission(Permission.READ_MEDIA_IMAGES, Permission.READ_MEDIA_VIDEO, Permission.READ_MEDIA_AUDIO)
        } else {
            permission.permission(Permission.Group.STORAGE)
        }
        permission.request { _, allGranted ->
            if (allGranted) {
                emitEvent(MainEvent.PermissionGrantedEvent)
            } else {
                emitEvent(MainEvent.PermissionDeniedEvent)
            }
        }
    }

}
