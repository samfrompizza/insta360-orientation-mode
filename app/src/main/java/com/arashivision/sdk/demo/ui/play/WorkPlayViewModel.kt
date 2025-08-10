package com.arashivision.sdk.demo.ui.play

import androidx.lifecycle.viewModelScope
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.util.SPUtils
import com.arashivision.sdk.demo.util.StorageUtils
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkmedia.export.ExportImageParamsBuilder
import com.arashivision.sdkmedia.export.ExportUtils
import com.arashivision.sdkmedia.export.ExportUtils.ExportMode
import com.arashivision.sdkmedia.export.ExportVideoParamsBuilder
import com.arashivision.sdkmedia.export.IExportCallback
import com.arashivision.sdkmedia.params.PlayerParamsBuilder
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.image.ImageParamsBuilder
import com.arashivision.sdkmedia.player.image.InstaImagePlayerView
import com.arashivision.sdkmedia.player.offset.OffsetType
import com.arashivision.sdkmedia.player.video.InstaVideoPlayerView
import com.arashivision.sdkmedia.player.video.VideoParamsBuilder
import com.arashivision.sdkmedia.stitch.StitchUtils
import com.arashivision.sdkmedia.work.WorkWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

class WorkPlayViewModel : BaseViewModel(), IExportCallback {

    private var mPlayerParamsBuilder: PlayerParamsBuilder<*>? = null

    private var gestureEnabled: Boolean
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.isGestureEnabled
            return SPUtils.getBoolean("GestureEnabled", true)
        }
        set(enable) {
            mPlayerParamsBuilder!!.isGestureEnabled = enable
            SPUtils.putBoolean("GestureEnabled", enable)
        }

    var isImageFusion: Boolean
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.isImageFusion
            return SPUtils.getBoolean("ImageFusion", false)
        }
        set(enable) {
            mPlayerParamsBuilder!!.setImageFusion(enable)
            SPUtils.putBoolean("ImageFusion", enable)
        }


    var isDePurpleFilterOn: Boolean
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.isDePurpleFilterOn
            return SPUtils.getBoolean("DePurpleFilter", true)
        }
        set(enable) {
            mPlayerParamsBuilder!!.setDePurpleFilterOn(enable)
            SPUtils.putBoolean("DePurpleFilter", enable)
        }

    var isDynamicStitch: Boolean
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.isDynamicStitch
            return SPUtils.getBoolean("DynamicStitch", false)
        }
        set(enable) {
            mPlayerParamsBuilder!!.setDynamicStitch(enable)
            SPUtils.putBoolean("DynamicStitch", enable)
        }

    private var withSwitchingAnimation: Boolean
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.isWithSwitchingAnimation
            return SPUtils.getBoolean("WithSwitchingAnimation", true)
        }
        set(enable) {
            mPlayerParamsBuilder!!.setWithSwitchingAnimation(enable)
            SPUtils.putBoolean("WithSwitchingAnimation", enable)
        }

    var offsetType: OffsetType
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.offsetType
            val type: String = SPUtils.getString("OffsetType", "ORIGINAL")
            for (value in OffsetType.entries) {
                if (value.name == type) {
                    return value
                }
            }
            return OffsetType.ORIGINAL
        }
        set(offsetType) {
            mPlayerParamsBuilder!!.setOffsetType(offsetType)
            SPUtils.putString("OffsetType", offsetType.name)
        }

    val screenRatio: IntArray
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.screenRatio
            val value: String = SPUtils.getString("ScreenRatio", "9:16")
            return Arrays.stream(value.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()).mapToInt { s: String -> s.toInt() }.toArray()
        }

    fun setScreenRatio(ratioX: Int, ratioY: Int) {
        mPlayerParamsBuilder!!.setScreenRatio(ratioX, ratioY)
        SPUtils.putString("ScreenRatio", "$ratioX:$ratioY")
    }

    var stabType: Int
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.stabType
            return SPUtils.getInt("StabType", InstaStabType.STAB_TYPE_OFF)
        }
        set(type) {
            mPlayerParamsBuilder!!.setStabType(type)
            SPUtils.putInt("StabType", type)
        }

    var renderModelType: Int
        get() {
            if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder!!.renderModelType
            return SPUtils.getInt("RenderModelType", PlayerParamsBuilder.RENDER_MODE_AUTO)
        }
        set(type) {
            mPlayerParamsBuilder!!.setRenderModelType(type)
            SPUtils.putInt("RenderModelType", type)
        }

    private var exportId: Int = -1
    private var exportPath: String = ""

    var hdrStitchPath = ""
        private set
    var pureShotStitchPath = ""
        private set

    var showHdr: Boolean = false
        private set
    var showPureShot: Boolean = false
        private set

    private fun createExportImageParamsBuilder(player: InstaImagePlayerView, exportWidth: Int, exportHeight: Int): ExportImageParamsBuilder {
        val imageBuilder = ExportImageParamsBuilder()
        imageBuilder.setTargetPath(StorageUtils.exportImageDir + "/" + System.currentTimeMillis() + ".jpg")
        imageBuilder.setStabType(player.stabType)
        imageBuilder.setOffsetType(player.offsetType)
        imageBuilder.setImageFusion(player.isImageFusion)
        if (renderModelType == PlayerParamsBuilder.RENDER_MODE_PLANE_STITCH) {
            imageBuilder.setExportMode(ExportMode.PANORAMA)
            imageBuilder.setScreenRatio(2, 1)
        } else {
            imageBuilder.setExportMode(ExportMode.SPHERE)
            imageBuilder.setScreenRatio(player.screenRatio[0], player.screenRatio[1])
        }
        imageBuilder.setWidth(exportWidth)
        imageBuilder.setHeight(exportHeight)
        imageBuilder.setDePurpleFilterOn(isDePurpleFilterOn)
        imageBuilder.setDynamicStitch(player.isDynamicStitch)

        imageBuilder.setDistance(player.distance)
        imageBuilder.setYaw(player.yaw)
        imageBuilder.setFov(player.fov)
        imageBuilder.setPitch(player.pitch)
        return imageBuilder
    }

    private fun createExportVideoParamsBuilder(player: InstaVideoPlayerView, exportWidth: Int, exportHeight: Int, fps: Int, bitrate: Int, denoise: Boolean): ExportVideoParamsBuilder {
        val videoBuilder = ExportVideoParamsBuilder()
        videoBuilder.setTargetPath(StorageUtils.exportVideoDir + "/" + System.currentTimeMillis() + ".mp4")
        videoBuilder.setStabType(player.stabType)
        videoBuilder.setOffsetType(player.offsetType)
        videoBuilder.setImageFusion(player.isImageFusion)
        if (renderModelType == PlayerParamsBuilder.RENDER_MODE_PLANE_STITCH) {
            videoBuilder.setExportMode(ExportMode.PANORAMA)
            videoBuilder.setScreenRatio(2, 1)
        } else {
            videoBuilder.setExportMode(ExportMode.SPHERE)
            videoBuilder.setScreenRatio(player.screenRatio[0], player.screenRatio[1])
        }
        videoBuilder.setWidth(exportWidth)
        videoBuilder.setHeight(exportHeight)
        videoBuilder.setFps(fps)
        videoBuilder.setDenoise(denoise)
        videoBuilder.setBitrate(bitrate)
        videoBuilder.setDePurpleFilterOn(player.isDePurpleFilterOn)
        videoBuilder.setDynamicStitch(player.isDynamicStitch)
        videoBuilder.setFov(player.fov)
        videoBuilder.setDistance(player.distance)
        videoBuilder.setYaw(player.yaw)
        videoBuilder.setRoll(player.roll)
        videoBuilder.setPitch(player.pitch)
        return videoBuilder
    }

    fun createPlayerParamsBuilder(workWrapper: WorkWrapper, showHdr: Boolean = false, showPureShot: Boolean = false, ): PlayerParamsBuilder<*> {
        if (mPlayerParamsBuilder != null) return mPlayerParamsBuilder as PlayerParamsBuilder<*>
        val builder = if (workWrapper.isVideo) {
            VideoParamsBuilder()
        } else {
            ImageParamsBuilder()
            // 缓存路径使用默认路径
            // builder.setCacheCutSceneRootPath("");
        }

        // 缓存路径使用默认路径
//        builder.setCacheCutSceneRootPath("");
//        builder.setCacheWorkThumbnailRootPath("");
//        builder.setStabilizerCacheRootPath("");
        builder.isGestureEnabled = gestureEnabled
        builder.setImageFusion(isImageFusion)
        builder.setDePurpleFilterOn(isDePurpleFilterOn)
        builder.setDynamicStitch(isDynamicStitch)
        builder.setWithSwitchingAnimation(withSwitchingAnimation)
        builder.setOffsetType(offsetType)
        val screenRatio = screenRatio
        builder.setScreenRatio(screenRatio[0], screenRatio[1])
        builder.setStabType(stabType)
        builder.setRenderModelType(renderModelType)
        this.showHdr = showHdr
        if (showHdr && hdrStitchPath.isNotEmpty()) {
            builder.setUrlForPlay(hdrStitchPath)
        }
        this.showPureShot = showPureShot
        if (showPureShot && pureShotStitchPath.isNotEmpty()) {
            builder.setUrlForPlay(pureShotStitchPath)
        }
        mPlayerParamsBuilder = builder
        return mPlayerParamsBuilder!!
    }


    fun exportImage(workWrapper: WorkWrapper, imagePlayerView: InstaImagePlayerView, width: Int, height: Int) {
        val createExportImageParamsBuilder = createExportImageParamsBuilder(imagePlayerView, width, height)
        exportPath = createExportImageParamsBuilder.targetPath
        ExportUtils.exportImage(workWrapper, createExportImageParamsBuilder, this)
    }

    fun exportVideo(workWrapper: WorkWrapper, player: InstaVideoPlayerView, exportWidth: Int, exportHeight: Int, fps: Int, bitrate: Int, denoise: Boolean) {
        val createExportVideoParamsBuilder = createExportVideoParamsBuilder(player, exportWidth, exportHeight,
            fps,
            if (bitrate == -1) workWrapper.bitrate else bitrate,
            denoise
        )
        exportPath = createExportVideoParamsBuilder.targetPath
        ExportUtils.exportVideo(workWrapper, createExportVideoParamsBuilder, this)
    }

    fun cancelExport() {
        ExportUtils.stopExport(exportId)
    }

    override fun onStart(id: Int) {
        exportId = id
        emitEvent(WorkPlayEvent.ExportMediaEvent(WorkPlayEvent.ExportStatus.START))
    }

    override fun onSuccess() {
        emitEvent(WorkPlayEvent.ExportMediaEvent(WorkPlayEvent.ExportStatus.SUCCESS, exportPath = exportPath))
    }

    override fun onFail(code: Int, msg: String?) {
        emitEvent(WorkPlayEvent.ExportMediaEvent(WorkPlayEvent.ExportStatus.FAILED, error = "$code:$msg"))
    }

    override fun onCancel() {
        emitEvent(WorkPlayEvent.ExportMediaEvent(WorkPlayEvent.ExportStatus.CANCEL))
    }


    fun hdrStitch(workWrapper: WorkWrapper) {
        emitEvent(WorkPlayEvent.HDRStitchEvent(EventStatus.START))
        viewModelScope.launch(Dispatchers.IO) {
            val output = StorageUtils.hdrStitchDir + "/hdr_" + System.currentTimeMillis() + ".jpg"
            val result = StitchUtils.generateHDR(workWrapper, output)
            withContext(Dispatchers.Main) {
                if (result) {
                    hdrStitchPath = output
                    emitEvent(WorkPlayEvent.HDRStitchEvent(EventStatus.SUCCESS))
                } else {
                    emitEvent(WorkPlayEvent.HDRStitchEvent(EventStatus.FAILED))
                }
            }
        }
    }

    fun pureShotStitch(workWrapper: WorkWrapper) {
        emitEvent(WorkPlayEvent.PureShotStitchEvent(EventStatus.START))
        viewModelScope.launch(Dispatchers.IO) {
            val output = StorageUtils.pureShotStitchDir + "/pure_shot_" + System.currentTimeMillis() + ".jpg"
            val result = StitchUtils.generatePureShot(workWrapper, output, "insta360/pure_shot_algo")
            withContext(Dispatchers.Main) {
                if (result == 0) {
                    pureShotStitchPath = output
                    emitEvent(WorkPlayEvent.PureShotStitchEvent(EventStatus.SUCCESS))
                } else {
                    emitEvent(WorkPlayEvent.PureShotStitchEvent(EventStatus.FAILED))
                }
            }
        }
    }

    override fun onProgress(progress: Float) {
        emitEvent(WorkPlayEvent.ExportMediaEvent(WorkPlayEvent.ExportStatus.PROGRESS,progress = progress))
    }

    fun tryLoadExtraData(workWrapper: WorkWrapper) {
        emitEvent(WorkPlayEvent.TryLoadExtraDataEvent(EventStatus.START))
        viewModelScope.launch(Dispatchers.IO) {
            if (!workWrapper.isExtraDataLoaded) {
                workWrapper.loadExtraData()
            }
            withContext(Dispatchers.Main) {
                emitEvent(WorkPlayEvent.TryLoadExtraDataEvent(EventStatus.SUCCESS))
            }
        }
    }
}
