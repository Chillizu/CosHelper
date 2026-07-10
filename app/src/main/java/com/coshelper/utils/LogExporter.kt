package com.coshelper.utils

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.coshelper.audio.AudioRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures app logs and system audio state, writes them into a single file, and
 * returns a shareable [Uri] via [FileProvider].
 */
object LogExporter {
    private const val TAG = "LogExporter"

    suspend fun export(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val appLogs = AppLogger.getLogFile(context)
            val outFile = File(context.filesDir, "logs/miokig_export_${timestamp()}.txt")
            outFile.writeText(buildReport(context, appLogs), Charsets.UTF_8)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to export logs", t)
            null
        }
    }

    fun createShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildReport(context: Context, appLogs: File): String {
        val sb = StringBuilder()
        sb.appendLine("=== MioKig Log Export ===")
        sb.appendLine("Time: ${isoTimestamp()}")
        sb.appendLine()

        sb.appendLine("=== Device Info ===")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Android API: ${Build.VERSION.SDK_INT}")
        sb.appendLine("App version: ${getAppVersion(context)}")
        sb.appendLine()

        sb.appendLine("=== App Process Logs ===")
        sb.append(captureLogcat("--pid", Process.myPid().toString()))
        sb.appendLine()

        sb.appendLine("=== System Audio Logs (best effort) ===")
        sb.append(captureAudioLogcat())
        sb.appendLine()

        sb.appendLine("=== Audio Device State ===")
        val router = AudioRouter.getInstance(context)
        sb.appendLine("Input devices:")
        router.getInputDevices().forEach { sb.appendLine(formatDevice(it)) }
        sb.appendLine("Output devices:")
        router.getOutputDevices().forEach { sb.appendLine(formatDevice(it)) }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sb.appendLine("AudioManager mode: ${audioManager.mode}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sb.appendLine("Communication device: ${audioManager.communicationDevice?.let { formatDevice(it) } ?: "none"}")
        }
        sb.appendLine()

        sb.appendLine("=== App File Logs ===")
        if (appLogs.exists()) {
            sb.append(appLogs.readText(Charsets.UTF_8))
        } else {
            sb.appendLine("(no app log file yet)")
        }
        return sb.toString()
    }

    private fun captureLogcat(vararg args: String): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", *args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.ifBlank { "(no logcat output)\n" }
        } catch (t: Throwable) {
            "(logcat failed: ${t.message})\n"
        }
    }

    private fun captureAudioLogcat(): String {
        // Best-effort system audio log capture. May be blocked by SELinux on
        // some devices, in which case we fall back to the process-only section.
        return try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "threadtime",
                "-s", "AudioFlinger:V", "AudioPolicyManager:V", "AudioService:V", "AudioRouter:V",
                "AudioRecorder:V", "AudioPlayer:V", "AudioDevicePicker:V", "*S"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.ifBlank { "(no system audio logcat output)\n" }
        } catch (t: Throwable) {
            "(system audio logcat failed: ${t.message})\n"
        }
    }

    private fun formatDevice(device: AudioDeviceInfo): String {
        return "  id=${device.id} type=${AppLogger.deviceTypeName(device.type)} name=${device.productName}"
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (t: Throwable) {
            "unknown"
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun isoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
