package com.alex.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Отдаёт скачанный APK системному установщику через FileProvider. */
class ApkInstaller(private val context: Context) {

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }
}
