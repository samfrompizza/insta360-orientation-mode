package com.arashivision.sdk.demo.ui.album.adapter

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseListAdapter
import com.arashivision.sdk.demo.databinding.ItemAlbumBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.glide.GlideApp
import com.arashivision.sdk.demo.glide.GlideRequest
import com.arashivision.sdkmedia.work.WorkWrapper
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import java.util.function.Consumer


class AlbumAdapter : BaseListAdapter<ItemAlbumBinding, WorkWrapper>() {

    override fun bind(binding: ItemAlbumBinding, data: WorkWrapper, position: Int) {
        if (data.isVideo) {
            binding.tvVideoDuration.visibility = View.VISIBLE
            binding.tvVideoDuration.text = data.durationInMs.durationFormat()
        } else {
            binding.tvVideoDuration.visibility = View.GONE
        }
        binding.ivCaptureMode.setImageResource(
            when {
                data.isHDRVideo -> R.drawable.ic_capture_mode_hdr_record
                data.isHDRPhoto -> R.drawable.ic_capture_mode_hdr_capture
                data.isBulletTime -> R.drawable.ic_capture_mode_bullettime
                data.isBurst -> R.drawable.ic_capture_mode_burst
                data.isIntervalShooting -> R.drawable.ic_capture_mode_interval
                data.isSlowMotion -> R.drawable.ic_capture_mode_slowmo
                data.isLooperVideo -> R.drawable.ic_capture_mode_loop_video
                data.isSuperNight -> R.drawable.ic_capture_mode_super_night
                data.isNormalPhoto -> R.drawable.ic_capture_mode_capture
                data.isNormalVideo -> R.drawable.ic_capture_mode_record
                data.isSuperVideo -> R.drawable.ic_capture_mode_super_record
                data.isSelfieVideo -> R.drawable.ic_capture_mode_selfie
                data.isTimeShift -> R.drawable.ic_capture_mode_timeshift
                data.isPureVideo -> R.drawable.ic_capture_mode_pure_video
                data.isStarLapse -> R.drawable.ic_capture_mode_starlapse
                else -> R.drawable.ic_capture_mode_record
            }
        )

        loadBitmap(binding.root.context, data) {
            if (data.isPanoramaFile) {
                // 全景图片 背景虚化处理
                GlideApp.with(binding.ivThumb)
                    .load(data)
                    .transform(BlurTransformation(25, 4))
                    .into(binding.ivThumb)

                binding.ivPanoThumb.visibility = View.VISIBLE
                GlideApp.with(binding.ivPanoThumb)
                    .load(data)
                    .circleCrop()
                    .into(binding.ivPanoThumb)
            } else {
                binding.ivThumb.setImageBitmap(it)
                binding.ivPanoThumb.visibility = View.GONE
            }
        }
    }


    private fun loadBitmap(context: Context, data: WorkWrapper, consumer: Consumer<Bitmap>) {
        GlideApp.with(context)
            .asBitmap()
            .load(data)
            .placeholder(R.drawable.ic_image_default)
            .error(R.drawable.ic_image_default)
            .priority(Priority.HIGH)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    consumer.accept(resource)
                }
            })
    }
}
