package com.arashivision.sdk.demo.util

import android.app.Application
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator

object XLogUtils {

    fun init(app: Application) {
        val configuration = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag("InstaSDK")
            .build()

        val androidPrinter: Printer = AndroidPrinter(true)
        val consolePrinter: Printer = ConsolePrinter()
        val filePrinter: Printer =
            FilePrinter.Builder(app.cacheDir.toString() + "/xlog")
                .fileNameGenerator(DateFileNameGenerator())
                .backupStrategy(NeverBackupStrategy())
                .cleanStrategy(FileLastModifiedCleanStrategy((15 * 24 * 60 * 60 * 1000).toLong()))
                .build()
        XLog.init(
            configuration,
            androidPrinter,
            consolePrinter,
            filePrinter
        )
    }
}