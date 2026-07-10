package com.coshelper.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

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
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in inputTypePriority }
            .sortedBy { inputTypePriority.indexOf(it.type) }
    }

    fun getOutputDevices(): List<AudioDeviceInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in outputTypePriority }
            .sortedBy { outputTypePriority.indexOf(it.type) }
    }

    fun setCommunicationMode(isCommunication: Boolean) {
        if (isCommunication) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun getDefaultInputDevice(): AudioDeviceInfo? = getInputDevices().firstOrNull()
    fun getDefaultOutputDevice(): AudioDeviceInfo? = getOutputDevices().firstOrNull()
}
