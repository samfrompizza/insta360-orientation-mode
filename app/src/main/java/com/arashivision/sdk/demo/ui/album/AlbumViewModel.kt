package com.arashivision.sdk.demo.ui.album

import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.ext.connectivityManager
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ui.album.AlbumEvent.AlbumDeleteCameraFileEvent
import com.arashivision.sdk.demo.ui.album.AlbumEvent.AlbumGetWorkWEvent
import com.arashivision.sdk.demo.util.NetworkManager
import com.arashivision.sdk.demo.util.StorageUtils
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkmedia.work.WorkUtils
import com.arashivision.sdkmedia.work.WorkWrapper
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.arashivision.sdkcamera.okgo.OkGo
import com.arashivision.sdkcamera.okgo.callback.FileCallback
import com.arashivision.sdkcamera.okgo.model.Progress
import com.arashivision.sdkcamera.okgo.model.Response
import com.arashivision.sdkcamera.okgo.request.base.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class AlbumViewModel : BaseViewModel(), ICameraChangedCallback {

    private val logger: Logger = XLog.tag(AlbumViewModel::class.java.simpleName).build()

    var mCameraWorks: MutableList<WorkWrapper> = ArrayList()
    var mLocalWorks: MutableList<WorkWrapper> = ArrayList()

    private var downloadTag: String = "AlbumViewModel"

    fun getAllWorks(refresh: Boolean) {
        if (!refresh) emitEvent(AlbumGetWorkWEvent(EventStatus.START))
        viewModelScope.launch(Dispatchers.IO) {
            mCameraWorks = getCameraWorks().toMutableList()
            mLocalWorks = getLocalWorks().toMutableList()
            withContext(Dispatchers.Main) {
                emitEvent(AlbumGetWorkWEvent(EventStatus.SUCCESS, mCameraWorks, mLocalWorks))
            }
        }
    }

    private fun getCameraWorks(): List<WorkWrapper> {
        val connectedType = instaCameraManager.cameraConnectedType
        if (connectedType == InstaCameraManager.CONNECT_TYPE_NONE || connectedType == InstaCameraManager.CONNECT_TYPE_BLE) {
            return emptyList()
        }
        return WorkUtils.getAllCameraWorks()
    }

    private fun getLocalWorks(): List<WorkWrapper> {
        return WorkUtils.getAllLocalWorks(StorageUtils.workDir + "/" + instaCameraManager.cameraType)
    }

    fun deleteFile(workWrapper: WorkWrapper) {
        emitEvent(AlbumDeleteCameraFileEvent(EventStatus.START))
        viewModelScope.launch(Dispatchers.IO) {
            if (workWrapper.isLocalFile) {
                workWrapper.urlsForDelete.forEach {
                    File(it).delete()
                }
                withContext(Dispatchers.Main) {
                    mLocalWorks.remove(workWrapper)
                    emitEvent(AlbumDeleteCameraFileEvent(EventStatus.SUCCESS))
                }
            } else {
                val result = deleteCameraFile(workWrapper.urlsForDelete.toList())
                withContext(Dispatchers.Main) {
                    if (result) {
                        mCameraWorks.remove(workWrapper)
                        emitEvent(AlbumDeleteCameraFileEvent(EventStatus.SUCCESS))
                    } else {
                        emitEvent(AlbumDeleteCameraFileEvent(EventStatus.FAILED))
                    }
                }
            }
        }
    }

    private suspend fun deleteCameraFile(files: List<String>): Boolean {
        return suspendCancellableCoroutine {
            instaCameraManager.deleteFileList(
                files,
                object : ICameraOperateCallback {
                    override fun onSuccessful() {
                        it.resume(true)
                    }

                    override fun onFailed() {
                        it.resume(false)
                    }

                    override fun onCameraConnectError() {
                        it.resume(false)
                    }
                })
        }
    }

    fun canDownload(workWrapper: WorkWrapper): Boolean {
        if (workWrapper.isLocalFile || workWrapper.urls.isEmpty()) return false

        val firstUrl = workWrapper.urls.firstOrNull() ?: return false

        val fileName = firstUrl.substringAfterLast('/', "")

        if (fileName.isBlank()) return false

        return mLocalWorks.asSequence()
            .flatMap { it.urls.asSequence() }
            .none { localUrl -> localUrl.contains(fileName) }
    }

    fun downloadWorkWrapper(workWrapper: WorkWrapper) {
        emitEvent(AlbumEvent.AlbumDownloadCameraFileEvent(EventStatus.START))
        val saveDir = File(StorageUtils.workDir + "/" + instaCameraManager.cameraType)
        if (!saveDir.exists()) saveDir.mkdirs()
        viewModelScope.launch(Dispatchers.IO) {
            workWrapper.urls.forEachIndexed { index, url ->
                val success = downloadFile(url, index, saveDir.absolutePath)
                if (!success) {
                    emitEvent(AlbumEvent.AlbumDownloadCameraFileEvent(EventStatus.FAILED))
                    return@launch
                }
            }
            withContext(Dispatchers.Main) {
                emitEvent(AlbumEvent.AlbumDownloadCameraFileEvent(EventStatus.SUCCESS))
            }
        }
    }

    private suspend fun downloadFile(url: String, index: Int, saveDir: String): Boolean {
        // http通信需要先绑定相机网络
        NetworkManager.cameraNet?.let { connectivityManager.bindProcessToNetwork(it) }
            ?: return false

        return suspendCancellableCoroutine {
            OkGo.get<File>(url).tag(downloadTag).execute(object :
                FileCallback(saveDir, url.substring(url.lastIndexOf("/") + 1, url.length)) {
                override fun onStart(request: Request<File, out Request<Any, Request<*, *>>>?) {
                    logger.d("start download $url")
                }

                override fun onSuccess(response: Response<File>?) {
                    it.resume(true)
                }

                override fun onError(response: Response<File>?) {
                    logger.d("download failed  code=${response?.code()}  body=${response?.body()}  exception=${response?.exception}")
                    it.resume(false)
                }

                override fun onFinish() {
                    super.onFinish()
                    logger.d("download finish")
                    // http通信结束，解除相机网络绑定
                    connectivityManager.bindProcessToNetwork(null)
                }

                override fun downloadProgress(progress: Progress?) {
                    progress?.let { p ->
                        val pro = (p.currentSize.toFloat() / p.totalSize.toFloat() * 100).toInt()
                        logger.d("${p.currentSize} / ${p.totalSize}   $p %")
                        emitEvent(
                            AlbumEvent.AlbumDownloadCameraFileEvent(
                                EventStatus.PROGRESS,
                                index = index,
                                progress = pro,
                                speed = getFormattedSpeed(p.speed.toFloat())
                            )
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getFormattedSpeed(speed: Float): String {
        return when {
            speed >= 1024 -> "${String.format("%.2f", speed / 1024 / 1024)} MB/s"
            speed > 0 -> "${String.format("%.2f", speed / 1024)} KB/s"
            else -> "0 KB/s"
        }
    }

    private fun cancelDownload() {
        OkGo.getInstance().cancelTag(downloadTag)
    }
}
