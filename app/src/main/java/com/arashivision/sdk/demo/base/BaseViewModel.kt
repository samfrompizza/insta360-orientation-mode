package com.arashivision.sdk.demo.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.model.TemperatureLevel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

open class BaseViewModel : ViewModel(), ICameraChangedCallback {

    private val _event = MutableSharedFlow<BaseEvent>()
    val event: SharedFlow<BaseEvent> = _event.asSharedFlow()

    init {
        instaCameraManager.registerCameraChangedCallback(this)
    }

    override fun onCleared() {
        viewModelScope.cancel()
        instaCameraManager.unregisterCameraChangedCallback(this)
        super.onCleared()
    }

    protected fun emitEvent(event: BaseEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    override fun onCameraSDCardStateChanged(enabled: Boolean) {
        emitEvent(BaseEvent.CameraSDCardStateChangedEvent(enabled));
    }

    override fun onCameraBatteryLow() {
        emitEvent(BaseEvent.CameraBatteryLowEvent)
    }

    override fun onCameraBatteryUpdate(batteryLevel: Int, isCharging: Boolean) {
        emitEvent(BaseEvent.CameraBatteryUpdateEvent(batteryLevel, isCharging));
    }


    override fun onCameraStorageChanged(freeSpace: Long, totalSpace: Long) {
        emitEvent(BaseEvent.CameraStorageChangedEvent(freeSpace, totalSpace))
    }

    override fun onCameraTemperatureChanged(tempLevel: TemperatureLevel?) {
        super.onCameraTemperatureChanged(tempLevel)
    }


    override fun onCameraStatusChanged(enabled: Boolean, connectType: Int) {
        emitEvent(BaseEvent.CameraStatusChangedEvent(enabled,connectType))
    }
}