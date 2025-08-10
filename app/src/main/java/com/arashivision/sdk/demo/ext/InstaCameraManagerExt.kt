package com.arashivision.sdk.demo.ext

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.InstaCameraManager.IDependChecker
import com.arashivision.sdkcamera.camera.model.AEB
import com.arashivision.sdkcamera.camera.model.BurstCapture
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkcamera.camera.model.CaptureSetting.*
import com.arashivision.sdkcamera.camera.model.DarkEisType
import com.arashivision.sdkcamera.camera.model.EV
import com.arashivision.sdkcamera.camera.model.EVInterval
import com.arashivision.sdkcamera.camera.model.Exposure
import com.arashivision.sdkcamera.camera.model.GammaMode
import com.arashivision.sdkcamera.camera.model.HdrStatus
import com.arashivision.sdkcamera.camera.model.ILogStatus
import com.arashivision.sdkcamera.camera.model.ISO
import com.arashivision.sdkcamera.camera.model.ISOTopLimit
import com.arashivision.sdkcamera.camera.model.InternalSplicing
import com.arashivision.sdkcamera.camera.model.Interval
import com.arashivision.sdkcamera.camera.model.LiveBitrate
import com.arashivision.sdkcamera.camera.model.PanoExposureMode
import com.arashivision.sdkcamera.camera.model.PhotoResolution
import com.arashivision.sdkcamera.camera.model.RawType
import com.arashivision.sdkcamera.camera.model.RecordDuration
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkcamera.camera.model.Shutter
import com.arashivision.sdkcamera.camera.model.ShutterMode
import com.arashivision.sdkcamera.camera.model.WB
import com.arashivision.sdkcamera.camera.model.proto.PhotoHdrType


val instaCameraManager: InstaCameraManager = InstaCameraManager.getInstance()

fun isPrimaryConnect(connectType: Int) = connectType in arrayOf(
    InstaCameraManager.CONNECT_TYPE_USB,
    InstaCameraManager.CONNECT_TYPE_WIFI
)

fun setCaptureSettingValue(
    captureMode: CaptureMode,
    captureSetting: CaptureSetting,
    value: Any,
    checker: IDependChecker? = null
) {
    return when (captureSetting) {
        EXPOSURE -> instaCameraManager.setExposure(captureMode, value as Exposure, checker)
        EV -> instaCameraManager.setEv(captureMode, value as EV, checker)
        EV_INTERVAL -> instaCameraManager.setEVInterval(
            captureMode, value as EVInterval, checker
        )

        SHUTTER -> instaCameraManager.setShutter(captureMode, value as Shutter, checker)
        SHUTTER_MODE -> instaCameraManager.setShutterMode(
            captureMode, value as ShutterMode, checker
        )

        ISO -> instaCameraManager.setISO(captureMode, value as ISO, checker)
        ISO_TOP_LIMIT -> instaCameraManager.setISOTopLimit(
            captureMode, value as ISOTopLimit, checker
        )

        RECORD_RESOLUTION -> instaCameraManager.setRecordResolution(
            captureMode, value as RecordResolution, checker
        )

        PHOTO_RESOLUTION -> instaCameraManager.setPhotoResolution(
            captureMode, value as PhotoResolution, checker
        )

        WB -> instaCameraManager.setWB(captureMode, value as WB, checker)
        AEB -> instaCameraManager.setAEB(captureMode, value as AEB, checker)
        INTERVAL -> instaCameraManager.setInterval(captureMode, value as Interval, checker)
        GAMMA_MODE -> instaCameraManager.setGammaMode(captureMode, value as GammaMode, checker)
        RAW_TYPE -> instaCameraManager.setRawType(captureMode, value as RawType, checker)
        RECORD_DURATION -> instaCameraManager.setRecordDuration(
            captureMode, value as RecordDuration, checker
        )

        DARK_EIS_ENABLE -> instaCameraManager.setDarkEisType(
            captureMode, value as DarkEisType, checker
        )

        PANO_EXPOSURE_MODE -> instaCameraManager.setPanoExposureMode(
            captureMode, value as PanoExposureMode, checker
        )

        BURST_CAPTURE -> instaCameraManager.setBurstCapture(
            captureMode, value as BurstCapture, checker
        )

        INTERNAL_SPLICING -> instaCameraManager.setInternalSplicingEnable(
            captureMode, value as InternalSplicing, checker
        )

        HDR_STATUS -> instaCameraManager.setHdrStatus(captureMode, value as HdrStatus, checker)
        PHOTO_HDR_TYPE -> instaCameraManager.setPhotoHdrType(captureMode, value as PhotoHdrType, checker)

        LIVE_BITRATE -> instaCameraManager.setLiveBitrate(
            captureMode, value as LiveBitrate, checker
        )

        I_LOG -> instaCameraManager.setILogStatus(captureMode, value as ILogStatus, checker)
    }
}

fun getCaptureSettingValue(captureMode: CaptureMode, captureSetting: CaptureSetting): Any {
    println("getCaptureSettingValue   captureMode=$captureMode   captureSetting=$captureSetting")
    return when (captureSetting) {
        EXPOSURE -> instaCameraManager.getExposure(captureMode)
        EV -> instaCameraManager.getEv(captureMode)
        EV_INTERVAL -> instaCameraManager.getEVInterval(captureMode)
        SHUTTER -> instaCameraManager.getShutter(captureMode)
        SHUTTER_MODE -> instaCameraManager.getShutterMode(captureMode)
        ISO -> instaCameraManager.getISO(captureMode)
        ISO_TOP_LIMIT -> instaCameraManager.getISOTopLimit(captureMode)
        RECORD_RESOLUTION -> instaCameraManager.getRecordResolution(captureMode)
        PHOTO_RESOLUTION -> instaCameraManager.getPhotoResolution(captureMode)
        WB -> instaCameraManager.getWB(captureMode)
        AEB -> instaCameraManager.getAEB(captureMode)
        INTERVAL -> instaCameraManager.getInterval(captureMode)
        GAMMA_MODE -> instaCameraManager.getGammaMode(captureMode)
        RAW_TYPE -> instaCameraManager.getRawType(captureMode)
        RECORD_DURATION -> instaCameraManager.getRecordDuration(captureMode)
        DARK_EIS_ENABLE -> instaCameraManager.getDarkEisType(captureMode)
        PANO_EXPOSURE_MODE -> instaCameraManager.getPanoExposureMode(captureMode)
        BURST_CAPTURE -> instaCameraManager.getBurstCapture(captureMode)
        INTERNAL_SPLICING -> instaCameraManager.getInternalSplicingEnable(captureMode)
        HDR_STATUS -> instaCameraManager.getHdrStatus(captureMode)
        PHOTO_HDR_TYPE -> {
            val photoHdrType = instaCameraManager.getPhotoHdrType(captureMode)
            println("photoHdrType ==>$photoHdrType")
            photoHdrType
        }
        LIVE_BITRATE -> instaCameraManager.getLiveBitrate(captureMode)
        I_LOG -> instaCameraManager.getILogStatus(captureMode)
    }
}

fun getCaptureSettingSupportList(
    captureMode: CaptureMode,
    captureSetting: CaptureSetting
): List<Any> {
    return when (captureSetting) {
        EXPOSURE -> instaCameraManager.getSupportExposureList(captureMode)
            .sortedBy { it.nativeValue }

        EV -> instaCameraManager.getSupportEVList(captureMode)
            .sortedBy { it.nativeValue }

        EV_INTERVAL -> instaCameraManager.getSupportEVIntervalList(captureMode)
            .sortedBy { it.nativeValue }

        SHUTTER -> instaCameraManager.getSupportShutterList(captureMode)
            .sortedBy { it.nativeValue }

        SHUTTER_MODE -> instaCameraManager.getSupportShutterModeList(captureMode)
            .sortedBy { it.nativeValue }

        ISO -> instaCameraManager.getSupportISOList(captureMode)
            .sortedBy { it.nativeValue }

        ISO_TOP_LIMIT -> instaCameraManager.getSupportISOTopLimitList(captureMode)
            .sortedBy { it.nativeValue }

        RECORD_RESOLUTION -> instaCameraManager.getSupportRecordResolutionList(
            captureMode
        ).sortedBy { it.nativeValue }

        PHOTO_RESOLUTION -> instaCameraManager.getSupportPhotoResolutionList(
            captureMode
        ).sortedBy { it.nativeValue }

        WB -> instaCameraManager.getSupportWBList(captureMode)
            .sortedBy { it.nativeValue }

        AEB -> instaCameraManager.getSupportAEBList(captureMode)
            .sortedBy { it.nativeValue }

        INTERVAL -> instaCameraManager.getSupportIntervalList(captureMode)
            .sortedBy { it.nativeValue }

        GAMMA_MODE -> instaCameraManager.getSupportGammaModeList(captureMode)
            .sortedBy { it.nativeValue }

        RAW_TYPE -> instaCameraManager.getSupportRawTypeList(captureMode)
            .sortedBy { it.nativeValue }

        RECORD_DURATION -> instaCameraManager.getSupportRecordDurationList(
            captureMode
        ).sortedBy { it.nativeValue }

        DARK_EIS_ENABLE -> instaCameraManager.getSupportDarkEisList(captureMode)
            .sortedBy { it.nativeValue }

        PANO_EXPOSURE_MODE -> instaCameraManager.getSupportPanoExposureList(
            captureMode
        ).sortedBy { it.nativeValue }

        BURST_CAPTURE -> instaCameraManager.getSupportBurstCaptureList(captureMode)
            .sortedBy { it.time }

        INTERNAL_SPLICING -> instaCameraManager.getSupportInternalSplicingList(
            captureMode
        ).sortedBy { it.nativeValue }

        HDR_STATUS -> instaCameraManager.getSupportHdrStatusList(captureMode)
            .sortedBy { it.nativeValue }

        PHOTO_HDR_TYPE -> instaCameraManager.getSupportPhotoHdrTypeList(captureMode)
            .sortedBy { it.nativeValue }

        LIVE_BITRATE -> instaCameraManager.getSupportLiveBitrateList(captureMode)
            .sortedBy { it.nativeValue }

        I_LOG -> instaCameraManager.getSupportILogStatusList(captureMode)
            .sortedBy { it.nativeValue }
    }
}