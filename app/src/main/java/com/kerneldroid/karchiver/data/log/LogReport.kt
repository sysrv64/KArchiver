package com.kerneldroid.karchiver.data.log

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import com.kerneldroid.karchiver.data.RustBridge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LogReport {

    private const val RUST_LOG_NAME = "karchiver-rust.log"

    fun rustLogFile(context: Context): File = File(context.cacheDir, RUST_LOG_NAME)

    suspend fun collect(context: Context): String = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        buildString {
            appendLine("KArchiver log report")
            appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("package: ${appContext.packageName}")
            appendLine("version: ${versionName(appContext)}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("rust: ${if (RustBridge.isLoaded()) "loaded" else "unavailable"}")
            appendLine()
            appendLine("===== system log (warnings and above, this app) =====")
            appendLine(logcat())
            appendLine()
            appendLine("===== app log =====")
            appendLine(KLog.dump().ifBlank { "(empty)" })
            appendLine()
            appendLine("===== rust log =====")
            appendLine(readFile(rustLogFile(appContext)).ifBlank { "(empty)" })
        }
    }

    suspend fun save(context: Context, content: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KArchiver"
            )
            if (!dir.exists() && !dir.mkdirs()) return@runCatching null
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "karchiver-logs-$stamp.txt")
            file.writeText(content)
            file
        }.getOrNull()
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun logcat(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}", "*:W"
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.trim()
    }.getOrDefault("(logcat unavailable)")

    private fun readFile(file: File): String = runCatching {
        if (file.isFile) file.readText().trim() else ""
    }.getOrDefault("")
}
