package com.coshelper.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import com.coshelper.BuildConfig
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var audioRecord: AudioRecord? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private var recordingJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var onPcmCallback: ((ShortArray) -> Unit)? = null

    private fun ensureScope() {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    fun setPcmCallback(callback: (ShortArray) -> Unit) {
        onPcmCallback = callback
    }

    fun start(): Boolean = start(null)
    fun start(inputDeviceId: Int?): Boolean {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        stop()
        ensureScope()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            return false
        }

        // 20 ms frame = 320 samples, doubled for safety
        val bufferSize = minBufferSize.coerceAtLeast(FRAME_SIZE_IN_SAMPLES * 2 * 2)

        val record = buildAudioRecord(bufferSize) ?: return false

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        // Per-feature input device override takes precedence over global default
        if (inputDeviceId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val device = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                .find { it.id == inputDeviceId }
            device?.let { record.setPreferredDevice(it) }
        }

        audioRecord = record
        record.startRecording()
        _isRecording.value = true

        recordingJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val frameBuffer = ShortArray(FRAME_SIZE_IN_SAMPLES)
            var frameFilled = 0
            var phase = 0
            try {
                while (isActive) {
                    if (frameFilled < FRAME_SIZE_IN_SAMPLES) {
                        val remaining = FRAME_SIZE_IN_SAMPLES - frameFilled
                        val read = if (BuildConfig.DEBUG) {
                            record.read(frameBuffer, frameFilled, remaining, AudioRecord.READ_NON_BLOCKING)
                        } else {
                            record.read(frameBuffer, frameFilled, remaining)
                        }
                        if (read > 0) {
                            frameFilled += read
                        } else if (frameFilled == 0 && BuildConfig.DEBUG) {
                            // Emulators often return no audio data; inject a 1 kHz test tone for debug builds only.
                            for (i in 0 until FRAME_SIZE_IN_SAMPLES) {
                                val sample = (kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * (phase + i) / SAMPLE_RATE) * 8000).toInt()
                                frameBuffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                            phase += FRAME_SIZE_IN_SAMPLES
                            frameFilled = FRAME_SIZE_IN_SAMPLES
                        } else if (frameFilled > 0) {
                            // Partial real frame; pad with silence to keep the encoder happy.
                            for (i in frameFilled until FRAME_SIZE_IN_SAMPLES) {
                                frameBuffer[i] = 0
                            }
                            frameFilled = FRAME_SIZE_IN_SAMPLES
                        }
                    }
                    if (frameFilled == FRAME_SIZE_IN_SAMPLES) {
                        onPcmCallback?.invoke(frameBuffer.copyOf())
                        frameFilled = 0
                    }
                }
            } catch (e: IllegalStateException) {
                // AudioRecord state error after stop/release; ignore
            } finally {
                try { record.stop() } catch (_: IllegalStateException) {}
                record.release()
                _isRecording.value = false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun buildAudioRecord(bufferSize: Int): AudioRecord? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val builder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)

            val record = builder.build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRouter.preferredInputDeviceId?.let { deviceId ->
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val device = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                        .find { it.id == deviceId }
                    device?.let { record.setPreferredDevice(it) }
                }
            }
            record
        } else {
            @Suppress("DEPRECATION")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }
    }

    fun stop() {
        recordingJob?.let { it.cancel() }
        recordingJob = null
        audioRecord?.apply {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        audioRecord = null
        _isRecording.value = false
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE_IN_SAMPLES = 320 // 20 ms at 16 kHz
    }
}
