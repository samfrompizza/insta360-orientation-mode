package com.arashivision.sdk.demo.util

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtils(private val context: Context) {
    /**
     * 将多个指定文件夹打包成 ZIP 文件
     *
     * @param sourceDirs  源文件夹列表
     * @param zipFilePath 目标 ZIP 文件路径
     * @param includeRoot 是否在 ZIP 中包含根文件夹名称
     * @return 成功返回 true，失败返回 false
     */
    fun zipSelectedFolders(
        sourceDirs: List<File>?,
        zipFilePath: String?,
        includeRoot: Boolean
    ): Boolean {
        if (sourceDirs.isNullOrEmpty()) {
            Log.e(TAG, "源文件夹列表为空")
            return false
        }

        var zos: ZipOutputStream? = null
        try {
            zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath)))
            for (sourceDir in sourceDirs) {
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    val rootPath = if (includeRoot) sourceDir.name else ""
                    addFolderToZip(sourceDir, zos, rootPath)
                } else {
                    Log.w(TAG, "源文件夹不存在或不是目录: " + sourceDir.absolutePath)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "压缩过程出错: " + e.message, e)
            return false
        } finally {
            if (zos != null) {
                try {
                    zos.close()
                } catch (e: IOException) {
                    Log.e(TAG, "关闭流失败: " + e.message, e)
                }
            }
        }
    }

    /**
     * 递归将文件夹添加到 ZIP
     */
    @Throws(IOException::class)
    private fun addFolderToZip(folder: File, zos: ZipOutputStream, parentPath: String) {
        val entries = folder.listFiles() ?: return

        for (entry in entries) {
            val zipEntryPath = if (parentPath.isEmpty())
                entry.name
            else
                parentPath + "/" + entry.name

            if (entry.isDirectory) {
                // 添加目录（需要以斜杠结尾）
                zos.putNextEntry(ZipEntry("$zipEntryPath/"))
                zos.closeEntry()
                // 递归处理子目录
                addFolderToZip(entry, zos, zipEntryPath)
            } else {
                // 添加文件
                zos.putNextEntry(ZipEntry(zipEntryPath))
                var bis: BufferedInputStream? = null
                try {
                    bis = BufferedInputStream(FileInputStream(entry))
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while ((bis.read(buffer).also { bytesRead = it }) != -1) {
                        zos.write(buffer, 0, bytesRead)
                    }
                } finally {
                    bis?.close()
                }
                zos.closeEntry()
            }
        }
    }

    companion object {
        private const val TAG = "ZipUtils"
    }
}
