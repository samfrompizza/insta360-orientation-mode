package com.arashivision.sdk.demo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object ShareUtils {
    /**
     * 分享文件到任意支持的应用
     * @param context 上下文
     * @param file 要分享的文件
     */
    fun shareFile(context: Context, file: File) {
        // 获取文件的 MIME 类型
        var mimeType = getMimeType(file.name)
        if (mimeType == null) {
            mimeType = "application/octet-stream"
        }

        // 创建分享意图
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setType(mimeType)
        // Android 7.0+ 使用 FileProvider

        // 根据 Android 版本获取文件 URI
        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        // 授予临时访问权限
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // 添加文件和额外信息
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "分享文件: " + file.name)
        shareIntent.putExtra(Intent.EXTRA_TEXT, "来自 Android 应用的分享")

        // 创建选择器对话框
        context.startActivity(Intent.createChooser(shareIntent, "分享文件到"))
    }

    /**
     * 获取文件的 MIME 类型
     */
    private fun getMimeType(fileName: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
        if (extension != null) {
            return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        }
        return null
    }
}
