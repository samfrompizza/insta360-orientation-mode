package com.arashivision.sdk.demo.ui.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.databinding.ActivityWorkPlayBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ui.play.dialog.ExportDialog
import com.arashivision.sdk.demo.util.AnimationUtils
import com.arashivision.sdk.demo.view.picker.EffectiveMode
import com.arashivision.sdk.demo.view.picker.PickData
import com.arashivision.sdkmedia.params.PlayerParamsBuilder
import com.arashivision.sdkmedia.player.image.ImageParamsBuilder
import com.arashivision.sdkmedia.player.listener.PlayerGestureListener
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.arashivision.sdkmedia.player.listener.VideoStatusListener
import com.arashivision.sdkmedia.player.offset.OffsetType
import com.arashivision.sdkmedia.player.video.VideoParamsBuilder
import com.arashivision.sdkmedia.work.WorkWrapper
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

class WorkPlayActivity : BaseActivity<ActivityWorkPlayBinding, WorkPlayViewModel>() {

    private val logger: Logger = XLog.tag(WorkPlayActivity::class.java.getSimpleName()).build()

    companion object {
        private var mWorkWrapper: WorkWrapper? = null

        fun launch(context: Context, workWrapper: WorkWrapper) {
            mWorkWrapper = workWrapper
            context.startActivity(Intent(context, WorkPlayActivity::class.java))
        }
    }

    private var exportDialog: ExportDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (mWorkWrapper == null) {
            finish()
            return
        }
        // 必须先加载文件尾数据，才可以进行播放，导出等操作
        mWorkWrapper?.let { viewModel.tryLoadExtraData(it) }
    }
    override fun initView() {
        super.initView()
        binding.pickPlayerSetting.setTitleText(getString(R.string.player_setting_dialog_title))

        binding.pickExportSetting.setTitleText(getString(R.string.export_params_setting_dialog_title))
        binding.pickExportSetting.setApplyText(getString(R.string.export_dialog_btn_text))
        binding.pickExportSetting.setEffectiveMode(EffectiveMode.APPLY)
    }


    override fun onEvent(event: BaseEvent) {
        super.onEvent(event)
        when (event) {
            is WorkPlayEvent.TryLoadExtraDataEvent -> {
                when (event.status) {
                    EventStatus.START -> showLoading()
                    EventStatus.SUCCESS -> {
                        if (mWorkWrapper?.isVideo == true) {
                            playVideo()
                        } else {
                            playImage()
                        }
                    }
                    else -> {}
                }
            }

            is WorkPlayEvent.ExportMediaEvent -> {
                when (event.exportStatus) {
                    WorkPlayEvent.ExportStatus.START -> {
                        showExportDialog()
                    }

                    WorkPlayEvent.ExportStatus.PROGRESS -> exportDialog?.setProgress((event.progress * 100).toInt())
                    WorkPlayEvent.ExportStatus.SUCCESS -> {
                        exportDialog?.setSuccess(event.exportPath)
                    }

                    WorkPlayEvent.ExportStatus.FAILED ->  exportDialog?.setError(event.error)

                    WorkPlayEvent.ExportStatus.CANCEL -> {}
                }
            }

            is WorkPlayEvent.HDRStitchEvent -> {
                when (event.status) {
                    EventStatus.START -> showLoading(R.string.player_hdr_stitching)
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        toast(R.string.player_hdr_stitch_success)
                        binding.tvHdrStitch.text = getString(R.string.player_show_original_image)
                        playImage(true)
                    }

                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.player_hdr_stitch_failed)
                    }

                    else -> {}
                }
            }

            is WorkPlayEvent.PureShotStitchEvent -> {
                when (event.status) {
                    EventStatus.START -> showLoading(R.string.player_pure_shot_stitching)
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        toast(R.string.player_pure_shot_stitch_success)
                        binding.tvPureShotStitch.text = getString(R.string.player_show_original_image)
                        playImage(true)
                    }

                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.player_pure_shot_stitch_failed)
                    }

                    else -> {}
                }
            }
        }
    }

    private fun showExportDialog() {
        exportDialog = ExportDialog()
        exportDialog?.setOnCancelExportListener {
            viewModel.cancelExport()
        }
        exportDialog?.show(supportFragmentManager, "export_dialog")
    }

    private fun initImagePlayerView() {
        binding.imagePlayerView.visibility = View.VISIBLE
        binding.videoPlayerView.visibility = View.GONE
        binding.groupProgress.visibility = View.GONE
        binding.imagePlayerView.setLifecycle(this.lifecycle)
        binding.imagePlayerView.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingStatusChanged(isLoading: Boolean) {
                logger.d("Image onLoadingStatusChanged   isLoading=$isLoading")
            }

            override fun onFirstFrameRender() {
                logger.d("Image onFirstFrameRender")
                hideLoading()
            }

            override fun onLoadingFinish() {
                logger.d("Image onLoadingFinish")
                binding.tvHdrStitch.visibility = if(mWorkWrapper!!.isHDRPhoto) View.VISIBLE else View.GONE
                binding.tvPureShotStitch.visibility = if(mWorkWrapper!!.supportPureShot()) View.VISIBLE else View.GONE
            }

            override fun onFail(errorCode: Int, errorMsg: String) {
                logger.d("Image onFail   errorCode=$errorCode   errorMsg=$errorMsg")
                hideLoading()
                toast(R.string.player_image_load_failed)
            }
        })
    }

    private fun playImage(showHdr: Boolean = false, showPureShot: Boolean = false) {
        initImagePlayerView()
        val builder: ImageParamsBuilder = viewModel.createPlayerParamsBuilder(mWorkWrapper!!, showHdr, showPureShot) as ImageParamsBuilder
        binding.imagePlayerView.prepare(mWorkWrapper, builder)
        binding.imagePlayerView.play()
        showLoading()
    }

    private fun initVideoPlayerView() {
        binding.imagePlayerView.visibility = View.GONE
        binding.videoPlayerView.visibility = View.VISIBLE
        binding.groupProgress.visibility = View.VISIBLE
        binding.videoPlayerView.setLifecycle(this.lifecycle)
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding.videoPlayerView.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.videoPlayerView.seekTo(seekBar.progress.toLong())
            }
        })
        binding.videoPlayerView.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingStatusChanged(isLoading: Boolean) {
                logger.d("Video onLoadingStatusChanged   isLoading=$isLoading")
            }

            override fun onFirstFrameRender() {
                logger.d("Video onFirstFrameRender")
                hideLoading()
            }

            override fun onLoadingFinish() {
                logger.d("Video onLoadingFinish")
            }

            override fun onFail(errorCode: Int, errorMsg: String) {
                logger.d("Video onFail   errorCode=$errorCode   errorMsg=$errorMsg")
                hideLoading()
                toast(R.string.player_video_load_failed)
            }
        })
        binding.videoPlayerView.setVideoStatusListener(object : VideoStatusListener {
            override fun onProgressChanged(position: Long, length: Long) {
                logger.d("Video onProgressChanged   position=$position   length=$length")
                binding.seekBar.setMax(length.toInt())
                binding.seekBar.progress = position.toInt()
                binding.tvCurrent.text = position.durationFormat()
                binding.tvTotal.text = length.durationFormat()
            }

            override fun onPlayStateChanged(isPlaying: Boolean) {
                logger.d("Video onPlayStateChanged   isPlaying=$isPlaying")
                if (binding.videoPlayerView.isPlaying) {
                    binding.ivPlay.setImageResource(R.drawable.ic_play_button_play)
                } else {
                    binding.ivPlay.setImageResource(R.drawable.ic_play_button_pause)
                }
                AnimationUtils.animateShowAndHide(binding.ivPlay)
            }

            override fun onSeekComplete() {
                logger.d("Video onSeekComplete")
                binding.videoPlayerView.resume()
            }

            override fun onCompletion() {
                logger.d("Video onCompletion")
            }
        })
        binding.videoPlayerView.setGestureListener(object : PlayerGestureListener {

            override fun onTap(e: MotionEvent): Boolean {
                if (binding.videoPlayerView.isPrepared) {
                    if (binding.videoPlayerView.isPlaying) {
                        binding.videoPlayerView.pause()
                    } else if (!binding.videoPlayerView.isLoading && !binding.videoPlayerView.isSeeking) {
                        binding.videoPlayerView.resume()
                    }
                }
                return false
            }

        })
    }

    private fun playVideo() {
        initVideoPlayerView()
        val builder: VideoParamsBuilder = viewModel.createPlayerParamsBuilder(mWorkWrapper!!) as VideoParamsBuilder
        binding.videoPlayerView.prepare(mWorkWrapper, builder)
        binding.videoPlayerView.play()
        showLoading()
    }

    override fun initListener() {
        super.initListener()
        binding.ivSetting.setOnClickListener { showPickerPlayerSetting() }

        binding.ivExport.setOnClickListener { showPickerExportParamsSetting() }

        binding.tvPreviewNormal.setOnClickListener {
            binding.videoPlayerView.switchNormalMode()
        }

        binding.tvPreviewFisheye.setOnClickListener {
            binding.videoPlayerView.switchFisheyeMode()
        }

        binding.tvPreviewPerspective.setOnClickListener {
            binding.videoPlayerView.switchPerspectiveMode()
        }

        binding.pickPlayerSetting.setOnItemClickListener { position, data ->
            when (position) {
                0 -> {
                    viewModel.isDePurpleFilterOn = data as Boolean
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.isDePurpleFilterOn = data
                    } else {
                        // InstaImagePlayerView暂时不支持去紫边功能
                    }
                }

                1 -> {
                    viewModel.isImageFusion =  data as Boolean
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.isImageFusion = data
                    } else {
                        binding.imagePlayerView.isImageFusion = data
                    }
                }

                2 -> {
                    viewModel.isDynamicStitch = data as Boolean
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.isDynamicStitch = data
                    } else {
                        binding.imagePlayerView.isDynamicStitch = data
                    }
                }

                3 -> {
                    val screenRatio = data as IntArray
                    viewModel.setScreenRatio(screenRatio[0], screenRatio[1])
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.setScreenRatio(screenRatio[0], screenRatio[1])
                    } else {
                        binding.imagePlayerView.setScreenRatio(screenRatio[0], screenRatio[1])
                    }
                }

                4 -> {
                    val stabType = data as Int
                    viewModel.stabType = stabType
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.stabType = stabType
                    } else {
                        binding.imagePlayerView.stabType = stabType
                    }
                }

                5 -> {
                    val renderModeType = data as Int
                    viewModel.renderModelType = renderModeType
                    if (renderModeType == PlayerParamsBuilder.RENDER_MODE_PLANE_STITCH) {
                        // 平铺模式使用2:1展示
                        viewModel.setScreenRatio(2, 1)
                        binding.tvPreviewNormal.isEnabled = false
                        binding.tvPreviewFisheye.isEnabled = false
                        binding.tvPreviewPerspective.isEnabled = false
                    } else {
                        viewModel.setScreenRatio(9, 16)
                        binding.tvPreviewNormal.isEnabled = true
                        binding.tvPreviewFisheye.isEnabled = true
                        binding.tvPreviewPerspective.isEnabled = true
                    }
                    if (mWorkWrapper?.isVideo == true) {
                        playVideo()
                    } else {
                        playImage()
                    }
                }

                6 -> {
                    val offsetType: OffsetType = data as OffsetType
                    viewModel.offsetType = offsetType
                    if (mWorkWrapper?.isVideo == true) {
                        binding.videoPlayerView.offsetType = offsetType
                    } else {
                        binding.imagePlayerView.offsetType = offsetType
                    }
                }
            }
            binding.pickPlayerSetting.setData(playerSettingData())
        }

        binding.pickExportSetting.setOnApplyClickListener { data ->
            mWorkWrapper?.let {
                if (it.isVideo) {
                    // 导出视频对手机性能要求较高,实际项目中导出时建议关闭播放器，否则容易出现oom
                    // 注意：关闭播放器指的是调用videoPlayerView.destroy()方法，恢复播放则需要重新创建一个新的InstaVideoPlayerView对象
                    viewModel.exportVideo(
                        it,
                        binding.videoPlayerView,
                        (data[0] as Size).width,
                        (data[0] as Size).height,
                        data[1] as Int,
                        data[3] as Int,
                        data[2] as Boolean
                    )
                } else {
                    viewModel.exportImage(
                        it,
                        binding.imagePlayerView,
                        (data[0] as Size).width,
                        (data[0] as Size).height
                    )
                }
            }
        }

        binding.tvHdrStitch.setOnClickListener {
            mWorkWrapper?.let { work ->
                if (viewModel.hdrStitchPath.isEmpty()) {
                    viewModel.hdrStitch(work)
                } else {
                    playImage(!viewModel.showHdr)
                    binding.tvHdrStitch.text = getString(if (viewModel.showHdr) R.string.player_show_original_image else R.string.player_hdr_stitch_play)
                }
            }
        }

        binding.tvPureShotStitch.setOnClickListener {
            mWorkWrapper?.let { work ->
                if (viewModel.pureShotStitchPath.isEmpty()) {
                    viewModel.pureShotStitch(work)
                } else {
                    playImage(!viewModel.showPureShot)
                    binding.tvPureShotStitch.text = getString(if (viewModel.showPureShot) R.string.player_show_original_image else R.string.player_pure_shot_stitch_play)
                }
            }
        }
    }

    private fun showPickerPlayerSetting() {
        binding.pickPlayerSetting.setData(playerSettingData())
        binding.pickPlayerSetting.show()
    }

    private fun showPickerExportParamsSetting() {
        binding.pickExportSetting.setData(exportParamsSettingData())
        binding.pickExportSetting.show()
    }

    private fun playerSettingData(): List<PickData> {
        val dataList: MutableList<PickData> = ArrayList()

        // 去紫边
        val dePurpleFilterTitle = getString(R.string.player_setting_de_purple_filter)
        val isDePurpleFilterOn = viewModel.isDePurpleFilterOn
        dataList.add(
            PickData(
                mWorkWrapper?.isVideo == true,
                dePurpleFilterTitle,
                if (isDePurpleFilterOn) 0 else 1,
                switchData.map { getString(it.first) to it.second }
            )
        )

        // 消色差
        val imageFusionTitle = getString(R.string.player_setting_image_fusion)
        val isImageFusion = viewModel.isImageFusion
        dataList.add(PickData(true, imageFusionTitle, if (isImageFusion) 0 else 1, switchData.map { getString(it.first) to it.second }))


        // 动态拼接
        val dynamicStitchTitle = getString(R.string.player_setting_dynamic_stitch)
        val isDynamicStitch = viewModel.isDynamicStitch
        dataList.add(PickData(true, dynamicStitchTitle, if (isDynamicStitch) 0 else 1, switchData.map { getString(it.first) to it.second }))


        // 屏幕比例
        val screenRatioTitle = getString(R.string.player_setting_screen_ratio)
        val screenRatioPosition = screenRatioOptions.indexOfFirst {
            val ratio = (1000 * (it.second[0].toFloat() / it.second[1])).toInt()
            val ratio2: Int =
                (1000 * (viewModel.screenRatio[0].toFloat() / viewModel.screenRatio[1])).toInt()
            ratio == ratio2
        }
        val screenRatioEnable = viewModel.renderModelType != PlayerParamsBuilder.RENDER_MODE_PLANE_STITCH
        dataList.add(PickData(screenRatioEnable, screenRatioTitle, screenRatioPosition, screenRatioOptions))


        // 防抖类型
        val stabTypeTitle: String = getString(R.string.player_setting_stab_type)
        val stabTypePosition = stabTypeOptions.indexOfFirst { it.second == viewModel.stabType }
        dataList.add(PickData(true, stabTypeTitle, stabTypePosition, stabTypeOptions.map { getString(it.first) to it.second }))


        // 渲染模式
        val renderModeTitle: String = getString(R.string.player_setting_render_mode)
        val renderModePosition = renderModeOptions.indexOfFirst { it.second == viewModel.renderModelType }
        dataList.add(PickData(true, renderModeTitle, renderModePosition, renderModeOptions.map { getString(it.first) to it.second }))


        // 保护镜类型
        val offsetTypeTitle: String = getString(R.string.player_setting_offset_type)
        val offsetTypePosition = offsetTypeOptions.indexOfFirst { it.second == viewModel.offsetType }
        dataList.add(PickData(true, offsetTypeTitle, offsetTypePosition, offsetTypeOptions.map { getString(it.first) to it.second }))

        return dataList
    }


    private fun exportParamsSettingData(): List<PickData> {
        val dataList: MutableList<PickData> = ArrayList()

        // 导出分辨率
        val screenRatio = if (mWorkWrapper!!.isVideo) binding.videoPlayerView.screenRatio else binding.imagePlayerView.screenRatio
        val resolutionTitle = getString(R.string.player_setting_export_resolution)
        val resolutionList = sizeOptionsList.filter {
            it.third[0] == screenRatio[0] && it.third[1] == screenRatio[1] && it.second.width <= mWorkWrapper!!.width && it.second.height <= mWorkWrapper!!.height
        }.map { it.first to it.second }
        val resolutionSelect = resolutionList.map { it.second }.indexOfFirst { it.width == mWorkWrapper!!.width && it.height == mWorkWrapper!!.height }
        dataList.add(PickData(true, resolutionTitle, maxOf(resolutionSelect, 0), resolutionList))

        if (mWorkWrapper!!.isVideo) {
            // 帧率
            val fpsTitle = getString(R.string.player_setting_export_fps)
            val fpsList = fpsOptionsList.filter { it.second <= mWorkWrapper!!.fps }
            val fpsSelect = fpsList.map { it.second }.indexOf(mWorkWrapper!!.fps.toInt())
            dataList.add(PickData(true, fpsTitle, maxOf(fpsSelect, 0), fpsList))

            // 降噪
            val denoiseTitle = getString(R.string.player_setting_export_denoise)
            dataList.add(PickData(true, denoiseTitle, 1, switchData.map { getString(it.first) to it.second }))

            // 码率
            val bitrateTitle = getString(R.string.player_setting_export_bitrate)
            val bitrateList = getBitrateOptionsList(this).filter { it.second <= mWorkWrapper!!.bitrate }
            val bitrateSelect = bitrateList.map { it.second }.indexOf(mWorkWrapper!!.bitrate)
            dataList.add(PickData(true, bitrateTitle, maxOf(bitrateSelect, 0), bitrateList))
        }


        return dataList
    }


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.pickExportSetting.isVisible) {
            binding.pickExportSetting.hide()
            return
        }
        if (binding.pickPlayerSetting.isVisible) {
            binding.pickPlayerSetting.hide()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        mWorkWrapper = null
        binding.videoPlayerView.destroy()
        binding.imagePlayerView.destroy()
        super.onDestroy()
    }
}
