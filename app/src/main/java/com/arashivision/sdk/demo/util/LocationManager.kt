package com.arashivision.sdk.demo.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationProvider
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * Created by dingming on 2017/4/1.
 */
object LocationManager {
    private var mLocationManager: android.location.LocationManager? = null
    private var mLocationListener: LocationListener? = null
    private var mLastGpslocation: Location? = null
    private var mLastNetworklocation: Location? = null

    private val isRegistered: Boolean
        get() {
            synchronized(this) {
                return mLocationManager != null
            }
        }

    fun registerLocation(context: Context) {
        synchronized(this) {
            if (isRegistered) {
                return
            }
            mLocationListener = object : LocationListener {
                /**
                 * 位置信息变化时触发
                 */
                /**
                 * 位置信息变化时触发
                 */
                override fun onLocationChanged(location: Location) {
                    if (location.provider == android.location.LocationManager.GPS_PROVIDER) {
                        Log.v(
                            TAG, "update gps location $mLastGpslocation"
                        )
                        mLastGpslocation = location
                    }
                    if (location.provider == android.location.LocationManager.NETWORK_PROVIDER) {
                        Log.v(
                            TAG, "update network location $mLastNetworklocation"
                        )
                        mLastNetworklocation = location
                    }
                }

                /**
                 * GPS状态变化时触发
                 */
                /**
                 * GPS状态变化时触发
                 */
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
                    when (status) {
                        LocationProvider.AVAILABLE -> Log.d(TAG, "当前GPS状态为可见状态")
                        LocationProvider.OUT_OF_SERVICE -> Log.d(TAG, "当前GPS状态为服务区外状态")
                        LocationProvider.TEMPORARILY_UNAVAILABLE -> Log.d(
                            TAG, "当前GPS状态为暂停服务状态"
                        )
                    }
                }

                /**
                 * GPS开启时触发
                 */
                /**
                 * GPS开启时触发
                 */
                override fun onProviderEnabled(provider: String) {
                    if (provider == android.location.LocationManager.GPS_PROVIDER) {
                        Log.d(TAG, "gps provider enabled")
                        mLastGpslocation = getLastLocation(provider)
                        requestLocationUpdates(provider)
                    }
                    if (provider == android.location.LocationManager.NETWORK_PROVIDER) {
                        Log.d(TAG, "network provider enabled")
                        mLastNetworklocation = getLastLocation(provider)
                        requestLocationUpdates(provider)
                    }
                }

                /**
                 * GPS禁用时触发
                 */
                /**
                 * GPS禁用时触发
                 */
                override fun onProviderDisabled(provider: String) {
                    if (provider == android.location.LocationManager.GPS_PROVIDER) {
                        Log.d(TAG, "gps provider disabled")
                        mLastGpslocation = null
                    }
                    if (provider == android.location.LocationManager.NETWORK_PROVIDER) {
                        Log.d(TAG, "network provider disabled")
                        mLastNetworklocation = null
                    }
                }
            }

            mLocationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (mLocationManager != null) {
                if (mLocationManager!!.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    Log.d(
                        TAG, "get last gps location $mLastGpslocation"
                    )
                    mLastGpslocation =
                        getLastLocation(android.location.LocationManager.GPS_PROVIDER)
                    requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER)
                }
                if (mLocationManager!!.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                    Log.d(
                        TAG, "get last network location $mLastNetworklocation"
                    )
                    mLastNetworklocation =
                        getLastLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER)
                }
            }
        }
    }

    fun unregisterLocation() {
        synchronized(this) {
            if (!isRegistered) {
                return
            }
            try {
                mLocationManager!!.removeUpdates(mLocationListener!!)
            } catch (e: Exception) {
                // Fatal Exception: java.lang.NullPointerException: Attempt to read from field 'java.lang.String com.android.server.location.gnss.datacollect.UseGnssAppBean.packageName' on a null object reference
                e.printStackTrace()
            }
            mLocationManager = null
            mLocationListener = null
        }
    }

    val currentLocation: Location?
        get() {
            if (mLastGpslocation != null) {
                Log.d(TAG, "return gps location $mLastGpslocation")
                return mLastGpslocation
            }
            if (mLastNetworklocation != null) {
                Log.d(TAG, "return network location $mLastNetworklocation")
                return mLastNetworklocation
            }
            Log.d(TAG, "return no location")
            return null
        }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun getLastLocation(provider: String): Location? {
        try {
            if (mLocationManager != null) {
                return mLocationManager!!.getLastKnownLocation(provider)
            }
        } catch (e: Exception) {
            // Fatal Exception: java.lang.NullPointerException: Attempt to invoke virtual method 'void android.location.Location.setLatitude(double)' on a null object reference
            e.printStackTrace()
        }
        return null
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun requestLocationUpdates(provider: String) {
        if (mLocationManager != null && mLocationListener != null) {
            try {
                mLocationManager!!.requestLocationUpdates(provider, 10, 0f, mLocationListener!!)
            } catch (e: Exception) {
                //Fatal Exception: java.lang.SecurityException
                //uid 10247 does not have android.permission.ACCESS_COARSE_LOCATION or android.permission.ACCESS_FINE_LOCATION.
                e.printStackTrace()
            }
        }
    }

    enum class LocationType {
        ANY, GPS, NETWORK, PASSIVE
    }

    private val TAG: String = LocationManager::class.java.simpleName
    private val GPS_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * 手机是否支持定位服务功能
     */
    fun isSystemSupportLocationService(context: Context, locationType: LocationType): Boolean {
        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            return when (locationType) {
                LocationType.ANY -> locationManager.allProviders.isNotEmpty()
                LocationType.GPS -> locationManager.getProvider(android.location.LocationManager.GPS_PROVIDER) != null

                LocationType.NETWORK -> locationManager.getProvider(android.location.LocationManager.NETWORK_PROVIDER) != null

                LocationType.PASSIVE -> locationManager.getProvider(android.location.LocationManager.PASSIVE_PROVIDER) != null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 手机是否开启定位服务
     */
    fun isLocationServiceEnable(context: Context, locationType: LocationType): Boolean {
        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            when (locationType) {
                LocationType.ANY -> {
                    val allProviders = locationManager.allProviders
                    if (allProviders.isNotEmpty()) {
                        for (provider in allProviders) {
                            if (locationManager.isProviderEnabled(provider)) {
                                return true
                            }
                        }
                    }
                    return false
                }

                LocationType.GPS -> return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                LocationType.NETWORK -> return locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                LocationType.PASSIVE -> return locationManager.isProviderEnabled(android.location.LocationManager.PASSIVE_PROVIDER)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 跳转到系统定位服务设置页
     */
    fun gotoSystemLocationSetting(context: Context) {
        val intent = Intent()
        intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            // The Android SDK doc says that the location settings activity
            // may not be found. In that case show the general settings.
            // General settings activity
            intent.setAction(Settings.ACTION_SETTINGS)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
