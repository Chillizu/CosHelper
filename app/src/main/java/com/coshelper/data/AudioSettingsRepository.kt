package com.coshelper.data

import android.content.Context
import android.content.SharedPreferences

class AudioSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("audio_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_INPUT_DEFAULT = "audio_input_default"
        const val KEY_INPUT_CHAT = "audio_input_chat"
        const val KEY_INPUT_STT = "audio_input_stt"
        const val KEY_INPUT_RVC = "audio_input_rvc"
        const val KEY_OUTPUT_DEFAULT = "audio_output_default"
        const val KEY_OUTPUT_RVC = "audio_output_rvc"
        const val KEY_STT_RECOGNITION_BEEP = "stt_recognition_beep"
    }

    fun getInputDevice(feature: String?): Int? {
        val key = feature?.let { "audio_input_$it" } ?: KEY_INPUT_DEFAULT
        return getIntOrNull(key)
    }

    fun setInputDevice(feature: String?, deviceId: Int?) {
        val key = feature?.let { "audio_input_$it" } ?: KEY_INPUT_DEFAULT
        if (deviceId != null) {
            prefs.edit().putInt(key, deviceId).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun getOutputDevice(feature: String?): Int? {
        val key = feature?.let { "audio_output_$it" } ?: KEY_OUTPUT_DEFAULT
        return getIntOrNull(key)
    }

    fun setOutputDevice(feature: String?, deviceId: Int?) {
        val key = feature?.let { "audio_output_$it" } ?: KEY_OUTPUT_DEFAULT
        if (deviceId != null) {
            prefs.edit().putInt(key, deviceId).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun resetInputDevice(feature: String?) {
        val key = feature?.let { "audio_input_$it" } ?: KEY_INPUT_DEFAULT
        prefs.edit().remove(key).apply()
    }

    fun resetOutputDevice(feature: String?) {
        val key = feature?.let { "audio_output_$it" } ?: KEY_OUTPUT_DEFAULT
        prefs.edit().remove(key).apply()
    }

    fun getSttRecognitionBeep(): Boolean =
        prefs.getBoolean(KEY_STT_RECOGNITION_BEEP, true)

    fun setSttRecognitionBeep(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STT_RECOGNITION_BEEP, enabled).apply()
    }

    private fun getIntOrNull(key: String): Int? {
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, -1).takeIf { it != -1 }
    }
}
