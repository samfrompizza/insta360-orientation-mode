package com.arashivision.sdk.demo.ui.setting

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.ext.dateFormat
import com.arashivision.sdk.demo.util.StorageUtils
import com.arashivision.sdk.demo.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingViewModel : BaseViewModel() {

    fun exportTodayLog(context: Context) {
        emitEvent(SettingEvent.ExportLogEvent(EventStatus.START))
        val todayLogPath: String =
            StorageUtils.logCacheDir + System.currentTimeMillis().dateFormat()
        val zipFile =
            File(StorageUtils.logZipDir, "log_" + System.currentTimeMillis().dateFormat() + ".zip")
        viewModelScope.launch(Dispatchers.IO) {
            if (zipFile.exists()) {
                zipFile.delete()
            }
            val parent = zipFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val zipUtils = ZipUtils(context)
            val success =
                zipUtils.zipSelectedFolders(listOf(File(todayLogPath)), zipFile.absolutePath, true)
            withContext(Dispatchers.Main) {
                if (success) {
                    emitEvent(SettingEvent.ExportLogEvent(EventStatus.SUCCESS, zipFile))
                } else {
                    emitEvent(SettingEvent.ExportLogEvent(EventStatus.FAILED))
                }
            }
        }
    }
}
