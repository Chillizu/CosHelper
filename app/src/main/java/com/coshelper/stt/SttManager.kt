package com.coshelper.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.coshelper.audio.AudioRecorder
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SttManager(context: Context) {
    private val appContext = context.applicationContext
    private val recorder = AudioRecorder(appContext)
    private val whisperJNI = WhisperJNI()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _status = MutableStateFlow("就绪")
    val status: StateFlow<String> = _status.asStateFlow()

    private var recordingJob: Job? = null
    private val sampleBuffer = ArrayList<Float>()

    private var modelLoaded = false


    private var inputDeviceId: Int? = null
    private var recognitionBeepEnabled = false

    fun setInputDevice(deviceId: Int?) {
        inputDeviceId = deviceId
    }

    fun setRecognitionBeep(enabled: Boolean) {
        recognitionBeepEnabled = enabled
    }
    private val modelFileName = "ggml-base-q5_1.bin"
    private val assetPath = "models/$modelFileName"

    fun loadModel(path: String): Boolean {
        _status.value = "模型加载中…"
        modelLoaded = try {
            whisperJNI.loadModel(path)
        } catch (e: Exception) {
            _status.value = "模型加载失败: ${e.message ?: "未知错误"}"
            false
        }
        _isModelLoaded.value = modelLoaded
        if (modelLoaded) {
            _status.value = "就绪"
        }
        return modelLoaded
    }

    fun loadModelFromAssetsOrDefault(): Boolean {
        _status.value = "模型加载中…"
        try {
            appContext.assets.open(assetPath).close()
            val outFile = File(appContext.filesDir, "models/$modelFileName")
            outFile.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val ok = loadModel(outFile.absolutePath)
            _isModelLoaded.value = ok
            return ok
        } catch (e: Exception) {
            _status.value = "模型加载失败: ${e.message ?: "请检查 assets/$assetPath"}"
            _isModelLoaded.value = false
            val file = File(appContext.filesDir, "models/$modelFileName")
            if (file.exists()) {
                val ok = loadModel(file.absolutePath)
                _isModelLoaded.value = ok
                return ok
            }
        }
        return false
    }

    fun start(): Boolean {
        if (!modelLoaded) {
            loadModelFromAssetsOrDefault()
        }
        if (!modelLoaded) {
            return false
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = "缺少录音权限"
            return false
        }
        stop()
        sampleBuffer.clear()
        _isRecording.value = true
        _status.value = "正在听…"
        playBeep()

        recorder.setPcmCallback { pcm ->
            synchronized(sampleBuffer) {
                pcm.forEach { sampleBuffer.add(it / 32768.0f) }
            }
        }
        recorder.start(inputDeviceId)

        recordingJob = scope.launch {
            while (isActive) {
                delay(300)
                val current = synchronized(sampleBuffer) {
                    if (sampleBuffer.size > 4800) {
                        val end = sampleBuffer.size
                        val start = (end - 4800 * 6).coerceAtLeast(0) // max 6 windows ~ 1.8s
                        sampleBuffer.subList(start, end).toFloatArray()
                    } else {
                        null
                    }
                }
                current?.let { samples ->
                    val result = whisperJNI.transcribe(samples, samples.size, "zh")
                    _text.value = result
                }
            }
        }
        return true
    }

    private fun playBeep() {
        if (!recognitionBeepEnabled) return
        scope.launch {
            val sampleRate = 16000
            val durationMs = 100
            val numSamples = sampleRate * durationMs / 1000
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val sample = (kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / sampleRate) * 8000).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize.coerceAtLeast(numSamples * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        recorder.stop()
        _isRecording.value = false
        _status.value = "已停止"
        playBeep()
    }

    fun cleanup() {
        stop()
        whisperJNI.freeModel()
        modelLoaded = false
        _isModelLoaded.value = false
        scope.cancel()
        recorder.cleanup()
    }
}
