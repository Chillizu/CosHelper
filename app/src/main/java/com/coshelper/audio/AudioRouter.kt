package com.coshelper.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.coshelper.utils.AppLogger

class AudioRouter private constructor(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        @Volatile
        var preferredInputDeviceId: Int? = null
            private set

        @Volatile
        var preferredOutputDeviceId: Int? = null
            private set

        fun setInputDevice(deviceId: Int) {
            preferredInputDeviceId = deviceId
        }

        fun setOutputDevice(deviceId: Int) {
            preferredOutputDeviceId = deviceId
        }

        fun resetInputDevice() {
            preferredInputDeviceId = null
        }

        fun resetOutputDevice() {
            preferredOutputDeviceId = null
        }

        @JvmStatic
        private var instance: AudioRouter? = null

        fun getInstance(context: Context): AudioRouter {
            return instance ?: synchronized(this) {
                instance ?: AudioRouter(context).also { instance = it }
            }
        }
    }

    private val inputTypePriority = listOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BUILTIN_MIC
    )

    private val outputTypePriority = listOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    )

    fun getInputDevices(): List<AudioDeviceInfo> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in inputTypePriority }
            .sortedBy { inputTypePriority.indexOf(it.type) }
        val sb = StringBuilder("Input devices: ")
        if (devices.isEmpty()) {
            sb.append("none")
        } else {
            devices.joinTo(sb) { "[id=${it.id}, type=${AppLogger.deviceTypeName(it.type)}, name=${it.productName}]" }
        }
        AppLogger.d("AudioRouter", sb.toString())
        return devices
    }

    fun getOutputDevices(): List<AudioDeviceInfo> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in outputTypePriority }
            .sortedBy { outputTypePriority.indexOf(it.type) }
        val sb = StringBuilder("Output devices: ")
        if (devices.isEmpty()) {
            sb.append("none")
        } else {
            devices.joinTo(sb) { "[id=${it.id}, type=${AppLogger.deviceTypeName(it.type)}, name=${it.productName}]" }
        }
        AppLogger.d("AudioRouter", sb.toString())
        return devices
    }

    fun setCommunicationMode(isCommunication: Boolean) {
        AppLogger.d("AudioRouter", "setCommunicationMode($isCommunication)")
        if (isCommunication) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun getDefaultInputDevice(): AudioDeviceInfo? {
        val device = getInputDevices().firstOrNull()
        AppLogger.d("AudioRouter", "Default input device: ${device?.let { "[id=${it.id}, type=${AppLogger.deviceTypeName(it.type)}, name=${it.productName}]" } ?: "none"}")
        return device
    }

    fun getDefaultOutputDevice(): AudioDeviceInfo? {
        val device = getOutputDevices().firstOrNull()
        AppLogger.d("AudioRouter", "Default output device: ${device?.let { "[id=${it.id}, type=${AppLogger.deviceTypeName(it.type)}, name=${it.productName}]" } ?: "none"}")
        return device
    }
}
