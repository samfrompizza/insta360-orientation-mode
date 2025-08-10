package com.arashivision.sdk.demo.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.arashivision.sdkmedia.export.ExportImageParamsBuilder
import com.arashivision.sdkmedia.export.ExportUtils
import com.arashivision.sdkmedia.export.IExportCallback
import com.arashivision.sdkmedia.work.WorkWrapper
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.Consumer

class WorkDataFetcher(private val mContext: Context, private val mWorkWrapper: WorkWrapper) :
    DataFetcher<InputStream> {
    private var mExportId = -1
    private var mInputStream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream?>) {
        mWorkWrapper.loadThumbnail()?.let {
            mInputStream = convertBitmapToInputStream(it, CompressFormat.PNG, 100)
            callback.onDataReady(mInputStream)
        } ?: run {
            exportThumbnail { path: String? ->
                try {
                    path?.let {
                        mInputStream = FileInputStream(path)
                        callback.onDataReady(mInputStream)
                    } ?: run {
                        callback.onLoadFailed(RuntimeException("export failed"))
                    }
                } catch (ex: Exception) {
                    callback.onLoadFailed(ex)
                }
            }
        }
    }


    private fun exportThumbnail(consumer: Consumer<String?>) {
        val targetPath =
            mContext.cacheDir.toString() + "/glide_thumbnail/" + mWorkWrapper.identicalKey
        val exportCallback: IExportCallback = object : IExportCallback {
            override fun onStart(id: Int) {
                mExportId = id
            }

            override fun onSuccess() {
                consumer.accept(targetPath)
            }

            override fun onFail(errorCode: Int, errorMsg: String) {
                consumer.accept(null)
                mExportId = -1
            }

            override fun onCancel() {
                mExportId = -1
                consumer.accept(null)
            }
        }
        if (mWorkWrapper.isVideo) {
            val builder = ExportImageParamsBuilder().setExportMode(ExportUtils.ExportMode.SPHERE)
                .setTargetPath(targetPath).setWidth(256).setHeight(256)
            ExportUtils.exportVideoToImage(mWorkWrapper, builder, exportCallback)
        } else {
            val builder = ExportImageParamsBuilder().setExportMode(ExportUtils.ExportMode.SPHERE)
                .setTargetPath(targetPath).setWidth(256).setHeight(256)
            ExportUtils.exportImage(mWorkWrapper, builder, exportCallback)
        }
    }


    override fun cleanup() {
        try {
            if (mInputStream != null) {
                mInputStream!!.close()
            }
        } catch (exception: IOException) {
            exception.printStackTrace()
        }
    }

    override fun cancel() {
        if (mExportId >= 0) {
            ExportUtils.stopExport(mExportId)
            mExportId = -1
        }
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        fun convertBitmapToInputStream(
            bitmap: Bitmap,
            format: CompressFormat,
            quality: Int
        ): InputStream {
            val bos = ByteArrayOutputStream()
            bitmap.compress(format, quality, bos)
            val bitmapData = bos.toByteArray()
            return ByteArrayInputStream(bitmapData)
        }
    }
}
