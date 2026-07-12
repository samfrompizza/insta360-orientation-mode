package com.arashivision.sdk.demo.ui.capture

import androidx.lifecycle.viewModelScope
import com.arashivision.graphicpath.render.source.AssetInfo
import com.arashivision.insta360.basemedia.asset.WindowCropInfo
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.data.camera.CameraOfflineData
import com.arashivision.sdk.demo.data.camera.usecase.CaptureControlUseCase
import com.arashivision.sdk.demo.data.camera.usecase.InitializeCameraUseCase
import com.arashivision.sdk.demo.data.camera.usecase.LiveStreamUseCase
import com.arashivision.sdk.demo.data.camera.usecase.PreviewStreamUseCase
import com.arashivision.sdk.demo.data.camera.usecase.SwitchCaptureModeUseCase
import com.arashivision.sdk.demo.ext.connectivityManager
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.setCaptureSettingValue
import com.arashivision.sdk.demo.pref.Pref
import com.arashivision.sdk.demo.util.NetworkManager
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Objects
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        private val initializeCameraUseCase: InitializeCameraUseCase,
        private val switchCaptureModeUseCase: SwitchCaptureModeUseCase,
        private val captureControlUseCase: CaptureControlUseCase,
        private val liveStreamUseCase: LiveStreamUseCase,
        private val previewStreamUseCase: PreviewStreamUseCase,
    ) : BaseViewModel(), IPreviewStatusListener, ICaptureStatusListener {
        private val logger: Logger = XLog.tag(CaptureViewModel::class.java.simpleName).build()

        private val _state = MutableStateFlow(CaptureUiState())
        val state: StateFlow<CaptureUiState> = _state.asStateFlow()

        private var openPreviewStreamListener: ((Boolean) -> Unit)? = null
        private var isFetchingOptions: Boolean = false
        private var isStreamOpened: Boolean = false
        private var isLiving = false

        private var _cameraOfflineData: CameraOfflineData? = null
        val cameraOfflineData: CameraOfflineData
            get() = _cameraOfflineData ?: error("Camera not initialized. Call initCapture() first.")

        val isSingleClickAction: Boolean
            get() = _cameraOfflineData?.let { captureControlUseCase.isSingleClickAction(it.currentCaptureMode) } ?: false

        init {
            instaCameraManager.setPreviewStatusChangedListener(this)
            instaCameraManager.setCaptureStatusListener(this)
            instaCameraManager.setCameraLockScreen(true)
            initCapture()
        }

        fun getCaptureSettingSupportValueList(captureSetting: CaptureSetting): List<Any> {
            val captureMode = cameraOfflineData.currentCaptureMode
            return when (captureSetting) {
                CaptureSetting.EXPOSURE -> instaCameraManager.getSupportExposureList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.EV -> instaCameraManager.getSupportEVList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.EV_INTERVAL -> instaCameraManager.getSupportEVIntervalList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.SHUTTER -> instaCameraManager.getSupportShutterList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.SHUTTER_MODE -> instaCameraManager.getSupportShutterModeList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.ISO -> instaCameraManager.getSupportISOList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.ISO_TOP_LIMIT -> instaCameraManager.getSupportISOTopLimitList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.RECORD_RESOLUTION ->
                    instaCameraManager.getSupportRecordResolutionList(
                        captureMode,
                    ).sortedBy { it.nativeValue }
                CaptureSetting.PHOTO_RESOLUTION -> instaCameraManager.getSupportPhotoResolutionList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.WB -> instaCameraManager.getSupportWBList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.AEB -> instaCameraManager.getSupportAEBList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.INTERVAL -> instaCameraManager.getSupportIntervalList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.GAMMA_MODE -> instaCameraManager.getSupportGammaModeList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.RAW_TYPE -> instaCameraManager.getSupportRawTypeList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.RECORD_DURATION -> instaCameraManager.getSupportRecordDurationList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.DARK_EIS_ENABLE -> instaCameraManager.getSupportDarkEisList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.PANO_EXPOSURE_MODE -> instaCameraManager.getSupportPanoExposureList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.BURST_CAPTURE -> instaCameraManager.getSupportBurstCaptureList(captureMode).sortedBy { it.time }
                CaptureSetting.INTERNAL_SPLICING ->
                    instaCameraManager.getSupportInternalSplicingList(
                        captureMode,
                    ).sortedBy { it.nativeValue }
                CaptureSetting.HDR_STATUS -> instaCameraManager.getSupportHdrStatusList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.PHOTO_HDR_TYPE -> instaCameraManager.getSupportPhotoHdrTypeList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.LIVE_BITRATE -> instaCameraManager.getSupportLiveBitrateList(captureMode).sortedBy { it.nativeValue }
                CaptureSetting.I_LOG -> instaCameraManager.getSupportILogStatusList(captureMode).sortedBy { it.nativeValue }
            }
        }

        fun getCaptureParams(): CaptureParamsBuilderV2 =
            CaptureParamsBuilderV2().apply {
                stabCacheFrameNum = Pref.getStabCacheFrameNum()
                setStabType(InstaStabType.STAB_TYPE_OFF)
            }

        fun initCapture() {
            logger.d("initCapture function invoke")
            emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.START))
            _state.update { it.copy(isInitializing = true, initStep = null) }
            viewModelScope.launch {
                emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.PROGRESS, CaptureEvent.InitStep.CHECK_SENSOR))
                _state.update { it.copy(initStep = CaptureEvent.InitStep.CHECK_SENSOR) }
                val checkOk = initializeCameraUseCase.checkSensorMode()
                if (!checkOk) {
                    emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.FAILED, CaptureEvent.InitStep.CHECK_SENSOR))
                    _state.update { it.copy(isInitializing = false, isInitFailed = true) }
                    return@launch
                }

                emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.PROGRESS, CaptureEvent.InitStep.FETCH_CAMERA_OPTIONS))
                _state.update { it.copy(initStep = CaptureEvent.InitStep.FETCH_CAMERA_OPTIONS) }
                val fetchOk = initializeCameraUseCase.fetchOptions()
                if (!fetchOk) {
                    emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.FAILED, CaptureEvent.InitStep.FETCH_CAMERA_OPTIONS))
                    _state.update { it.copy(isInitializing = false, isInitFailed = true) }
                    return@launch
                }

                NetworkManager.cameraNet?.let {
                    connectivityManager.bindProcessToNetwork(it)
                } ?: run {
                    emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.FAILED, CaptureEvent.InitStep.INIT_SUPPORT_CONFIG))
                    _state.update { it.copy(isInitializing = false, isInitFailed = true) }
                    return@launch
                }

                emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.PROGRESS, CaptureEvent.InitStep.INIT_SUPPORT_CONFIG))
                _state.update { it.copy(initStep = CaptureEvent.InitStep.INIT_SUPPORT_CONFIG) }
                val configOk = initializeCameraUseCase.initSupportConfig()
                connectivityManager.bindProcessToNetwork(null)
                if (!configOk) {
                    emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.FAILED, CaptureEvent.InitStep.INIT_SUPPORT_CONFIG))
                    _state.update { it.copy(isInitializing = false, isInitFailed = true) }
                    return@launch
                }

                _cameraOfflineData = CameraOfflineData()

                emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.PROGRESS, CaptureEvent.InitStep.OPEN_PREVIEW_STREAM))
                _state.update { it.copy(initStep = CaptureEvent.InitStep.OPEN_PREVIEW_STREAM) }
                val streamOk = openPreviewStream()
                if (!streamOk) {
                    emitEvent(CaptureEvent.InitCaptureEvent(EventStatus.FAILED, CaptureEvent.InitStep.OPEN_PREVIEW_STREAM))
                    _state.update { it.copy(isInitializing = false, isInitFailed = true) }
                    return@launch
                }

                if (!instaCameraManager.supportConfig.supportNewCaptureControlFlow()) {
                    setOfflineCaptureSettingValueToCamera()
                }
                initializeCameraUseCase.fetchOptions()

                emitEvent(
                    CaptureEvent.InitCaptureEvent(
                        EventStatus.SUCCESS,
                        captureModeList = instaCameraManager.supportCaptureMode,
                        currentCaptureMode = cameraOfflineData.currentCaptureMode,
                    ),
                )
                _state.update {
                    it.copy(
                        isInitializing = false,
                        isInitFailed = false,
                        showRenderingLoading = true,
                        captureModeList = instaCameraManager.supportCaptureMode,
                        currentCaptureMode = cameraOfflineData.currentCaptureMode,
                    )
                }
            }
        }

        fun switchCaptureMode(position: Int) {
            emitEvent(CaptureEvent.SwitchCaptureModeEvent(EventStatus.START))
            viewModelScope.launch {
                val result = switchCaptureModeUseCase(position)
                result.onFailure {
                    emitEvent(CaptureEvent.SwitchCaptureModeEvent(EventStatus.FAILED))
                    return@launch
                }
                result.onSuccess { mode ->
                    if (!instaCameraManager.supportConfig.supportNewCaptureControlFlow()) {
                        setOfflineCaptureSettingValueToCamera()
                    }
                    emitEvent(CaptureEvent.SwitchCaptureModeEvent(EventStatus.SUCCESS))
                    _state.update { it.copy(currentCaptureMode = mode) }
                }
            }
        }

        fun startCapture() {
            val currentMode = cameraOfflineData.currentCaptureMode
            if (isSingleClickAction) {
                captureControlUseCase.startCapture(currentMode)
                return
            }
            when {
                currentMode.isLiveMode && !isLiving -> startLive()
                currentMode.isLiveMode && isLiving -> stopLive()
                !currentMode.isLiveMode && !instaCameraManager.isCameraWorking -> {
                    if (!instaCameraManager.isSdCardEnabled) {
                        emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.SD_DISABLE))
                        _state.update { it.copy(sdcardMissing = true) }
                        return
                    }
                    captureControlUseCase.startCapture(currentMode)
                }
                !currentMode.isLiveMode && instaCameraManager.isCameraWorking ->
                    captureControlUseCase.stopCapture(currentMode)
            }
        }

        private fun startLive() {
            val rtmp = Pref.getLiveRtmp()
            if (rtmp.isEmpty()) {
                emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.RTMP_EMPTY))
                return
            }
            emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.START_LIVE))
            liveStreamUseCase.startLive(
                rtmp,
                object : ILiveStatusListener {
                    override fun onLivePushStarted() {
                        isLiving = true
                        emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.PUSH_STARTED))
                        _state.update { it.copy(isLiveStreaming = true) }
                    }

                    override fun onLivePushFinished() {
                        isLiving = false
                        emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.PUSH_FINISHED))
                        _state.update { it.copy(isLiveStreaming = false) }
                    }

                    override fun onLivePushError(
                        error: Int,
                        desc: String?,
                    ) {
                        isLiving = false
                        emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.PUSH_ERROR))
                        _state.update { it.copy(isLiveStreaming = false, errorMessage = "PUSH_ERROR") }
                    }

                    override fun onLiveFpsUpdate(fps: Int) {}
                },
            )
        }

        private fun stopLive() {
            emitEvent(CaptureEvent.CameraLiveEvent(CaptureEvent.LiveStatus.STOP_LIVE))
            liveStreamUseCase.stopLive()
        }

        private suspend fun openPreviewStream(): Boolean =
            suspendCancellableCoroutine {
                logger.d("openPreviewStream function invoke")
                openPreviewStreamListener = { success ->
                    logger.d("openPreviewStream result : $success")
                    instaCameraManager.setStreamEncode()
                    it.resume(success)
                    openPreviewStreamListener = null
                }
                instaCameraManager.startPreviewStream(InstaCameraManager.PREVIEW_TYPE_NORMAL)
            }

        fun closePreviewStream() {
            instaCameraManager.closePreviewStream()
            instaCameraManager.setPreviewStatusChangedListener(null)
        }

        fun cameraPreviewStreamParamsChanged(playerView: InstaCapturePlayerView) {
            if (isStreamOpened &&
                instaCameraManager.isH265StreamEncode != (instaCameraManager.videoEncodeType == InstaCameraManager.ENCODE_265)
            ) {
                instaCameraManager.setStreamEncode()
                emitEvent(CaptureEvent.RestartPlayerViewEvent)
                return
            }

            if (playerView.isPlaying) {
                if (isPreviewFileTypeChange(playerView)) {
                    viewModelScope.launch {
                        initializeCameraUseCase.fetchOptions()
                        emitEvent(CaptureEvent.RestartPlayerViewEvent)
                    }
                    return
                }

                val captureMode = cameraOfflineData.currentCaptureMode
                val isFlowStateOn = instaCameraManager.isFlowstateOn(captureMode)
                val assetInfo = instaCameraManager.supportConfig.getConvertAssetInfo(captureMode, isFlowStateOn)
                val assetInfoStab = instaCameraManager.supportConfig.getStabConvertAssetInfo(captureMode, isFlowStateOn)
                val stabOffset = InstaCapturePlayerView.getPlayerOffsetData(assetInfoStab).offsetV1
                val shouldUpdate = shouldUpdateWindowCrop(playerView, assetInfo, stabOffset)

                val windowCropInfo = if (shouldUpdate) createWindowCropInfo(assetInfo) else null
                val offsetData = if (shouldUpdate) InstaCapturePlayerView.getPlayerOffsetData(assetInfo) else null

                val resolution =
                    instaCameraManager.curFirstStreamResolution?.takeIf {
                        it.width != playerView.previewWidth || it.height != playerView.previewHeight || it.fps != playerView.previewFps
                    }

                logger.d(
                    "cameraPreviewStreamParamsChanged   windowCropInfo=$windowCropInfo   offsetData=$offsetData   stabOffset=$stabOffset   resolution=$resolution",
                )
                emitEvent(CaptureEvent.UpdatePlayerViewParamsEvent(windowCropInfo, offsetData, stabOffset, resolution))
            }
        }

        private suspend fun setOfflineCaptureSettingValueToCamera(): Boolean =
            suspendCancellableCoroutine { cont ->
                with(instaCameraManager) {
                    beginSettingOptions()
                    getSupportCaptureSettingList(cameraOfflineData.currentCaptureMode).forEach { setting ->
                        val value = cameraOfflineData.getCaptureSetting(cameraOfflineData.currentCaptureMode, setting)
                        setCaptureSettingValue(cameraOfflineData.currentCaptureMode, setting, value)
                    }
                    if (supportConfig.supportNewCaptureControlFlow()) {
                        commitSettingOptions { code -> cont.resume(code == 0) }
                    } else {
                        commitSettingOptions(null)
                        cont.resume(true)
                    }
                }
            }

        private fun shouldUpdateWindowCrop(
            playerView: InstaCapturePlayerView,
            assetInfo: AssetInfo,
            stabOffset: String,
        ): Boolean {
            val cropInfo = playerView.windowCropInfo ?: return true
            return assetInfo.cropWindowSrcWidth != cropInfo.srcWidth ||
                assetInfo.cropWindowSrcHeight != cropInfo.srcHeight ||
                assetInfo.cropWindowDstWidth != cropInfo.desWidth ||
                assetInfo.cropWindowDstHeight != cropInfo.desHeight ||
                assetInfo.cropOffsetX != cropInfo.offsetX ||
                assetInfo.cropOffsetY != cropInfo.offsetY ||
                !Objects.equals(stabOffset, playerView.stabOffset)
        }

        private fun createWindowCropInfo(assetInfo: AssetInfo): WindowCropInfo =
            WindowCropInfo().apply {
                srcWidth = assetInfo.cropWindowSrcWidth
                srcHeight = assetInfo.cropWindowSrcHeight
                desWidth = assetInfo.cropWindowDstWidth
                desHeight = assetInfo.cropWindowDstHeight
                offsetX = assetInfo.cropOffsetX
                offsetY = assetInfo.cropOffsetY
            }

        private fun isPreviewFileTypeChange(playerView: InstaCapturePlayerView): Boolean {
            val isFlowStateOn = instaCameraManager.isFlowstateOn(cameraOfflineData.currentCaptureMode)
            return instaCameraManager.supportConfig.getPreviewFileType(
                cameraOfflineData.currentCaptureMode, isFlowStateOn,
            ) != playerView.fileType
        }

        // --- IPreviewStatusListener ---
        override fun onOpening() {
            logger.d("onOpening")
        }

        override fun onOpened() {
            logger.d("onOpened")
            isStreamOpened = true
            openPreviewStreamListener?.invoke(true)
        }

        override fun onIdle() {
            logger.d("onIdle")
            isStreamOpened = false
        }

        override fun onError() {
            logger.d("onError")
            isStreamOpened = false
            openPreviewStreamListener?.invoke(false)
        }

        // --- ICaptureStatusListener ---
        override fun onCaptureStarting() {
            emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.STARTING))
            _state.update { it.copy(isCapturing = true) }
        }

        override fun onCaptureWorking() {
            emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.WORKING))
            _state.update { it.copy(isRecording = true, isCaptureButtonVisible = false) }
        }

        override fun onCaptureStopping() {
            emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.STOPPING))
            _state.update { it.copy(isCapturing = false) }
        }

        override fun onCaptureFinish(paths: Array<String>?) {
            emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.FINISH))
            _state.update { it.copy(isCapturing = false, isRecording = false, isCaptureButtonVisible = true) }
            onCaptureFinishEnd()
        }

        override fun onCaptureError(code: Int) {
            emitEvent(CaptureEvent.CameraCaptureEvent(errorCode = code))
            _state.update { it.copy(isCapturing = false, isRecording = false, isCaptureButtonVisible = true) }
        }

        override fun onCaptureTimeChanged(captureTime: Long) {
            val timeLapseWorking = instaCameraManager.isCameraWorking(CaptureMode.TIMELAPSE)
            if (timeLapseWorking) {
                val recordResolution = instaCameraManager.getRecordResolution(CaptureMode.TIMELAPSE)
                val interval = instaCameraManager.getInterval(CaptureMode.TIMELAPSE)
                val videoTime = ((captureTime / interval.nativeValue) / recordResolution.fps) * 1000
                emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.RECORD_TIME, captureTime, videoTime))
            } else {
                emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.RECORD_TIME, recordTime = captureTime))
            }
            _state.update { it.copy(showRecordTime = true, recordTimeMs = captureTime) }
        }

        override fun onCaptureCountChanged(count: Int) {
            emitEvent(CaptureEvent.CameraCaptureEvent(CaptureEvent.CaptureStatus.CAPTURE_COUNT, captureCount = count))
            _state.update { it.copy(showCaptureCount = true, captureCount = count) }
        }

        override fun onCameraStatusChanged(
            enabled: Boolean,
            connectType: Int,
        ) {
            if (connectType == InstaCameraManager.CONNECT_TYPE_WIFI && !enabled) {
                emitEvent(CaptureEvent.CameraWiFiDisconnectEvent)
            }
        }

        override fun onCameraPreviewStreamParamsChanged(isChanged: Boolean) {
            if (instaCameraManager.cameraConnectedType != InstaCameraManager.CONNECT_TYPE_WIFI) return
            if (isFetchingOptions) return
            if (isChanged) emitEvent(CaptureEvent.CameraPreviewStreamParamsChangedEvent)
        }

        private fun onCaptureFinishEnd() {
            viewModelScope.launch {
                if (instaCameraManager.supportConfig.supportNewCaptureControlFlow()) return@launch
                val currentMode = cameraOfflineData.currentCaptureMode
                if (currentMode.isVideoMode || currentMode in arrayOf(CaptureMode.INTERVAL_SHOOTING, CaptureMode.STARLAPSE_SHOOTING)) {
                    previewStreamUseCase.reopenStream()
                    emitEvent(CaptureEvent.RestartPlayerViewEvent)
                } else {
                    if (currentMode.isPhotoMode) {
                        val oldIsH265 = instaCameraManager.isH265StreamEncode
                        initializeCameraUseCase.fetchOptions()
                        if (instaCameraManager.isH265StreamEncode != oldIsH265) {
                            previewStreamUseCase.reopenStream()
                            emitEvent(CaptureEvent.RestartPlayerViewEvent)
                        }
                    }
                    if (instaCameraManager.previewStatus == InstaCameraManager.PREVIEW_STATUS_OPENED) {
                        setOfflineCaptureSettingValueToCamera()
                    }
                }
            }
        }

        override fun onCleared() {
            isFetchingOptions = false
            instaCameraManager.setPreviewStatusChangedListener(null)
            instaCameraManager.setCaptureStatusListener(null)
            instaCameraManager.setCameraLockScreen(false)
            super.onCleared()
        }
    }
