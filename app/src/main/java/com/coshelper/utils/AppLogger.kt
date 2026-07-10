package com.coshelper.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application file logger. Writes log entries to the app's private files directory
 * so they can be exported without adb. Each log call also forwards to [Log].
 *
 * The log file is rotated when it exceeds [MAX_LOG_SIZE_BYTES]; only the current
 * and one previous file are kept.
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "app.log"
    private const val LOG_FILE_OLD = "app.log.old"
    private const val MAX_LOG_SIZE_BYTES = 512 * 1024L // 512 KB

    private var logDir: File? = null

    @Synchronized
    fun init(context: Context) {
        if (logDir != null) return
        logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        i(TAG, "AppLogger initialized at ${logDir!!.absolutePath}")
    }

    fun getLogFile(context: Context): File {
        init(context)
        return File(logDir, LOG_FILE)
    }

    @JvmStatic
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        append("V", tag, message)
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append("D", tag, message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message)
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        append("W", tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        append("E", tag, message, throwable)
    }

    @Synchronized
    private fun append(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val dir = logDir ?: return
        try {
            val file = File(dir, LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                val old = File(dir, LOG_FILE_OLD)
                old.delete()
                file.renameTo(old)
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = StringBuilder().apply {
                append(timestamp).append(" ").append(level).append("/").append(tag).append(": ").append(message)
                throwable?.let {
                    append("\n")
                    append(Log.getStackTraceString(it))
                }
                append("\n")
            }
            file.appendText(line.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to append log entry", t)
        }
    }

    fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        else -> "TYPE_$type"
    }
}
