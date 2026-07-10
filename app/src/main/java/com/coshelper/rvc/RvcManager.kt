package com.coshelper.rvc

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.coshelper.audio.AudioPlayer
import com.coshelper.audio.AudioRecorder
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

class RvcManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: RvcManager? = null

        fun getInstance(context: Context): RvcManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RvcManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val processor = RvcProcessor()
    private val recorder = AudioRecorder(appContext)
    private val player = AudioPlayer(appContext)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(RvcState.Idle)
    val state: StateFlow<RvcState> = _state.asStateFlow()

    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info.asStateFlow()

    private var job: Job? = null

    private var inputDeviceId: Int? = null
    private var outputDeviceId: Int? = null

    private fun ensureScope() {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    fun setInputDevice(deviceId: Int?) {
        inputDeviceId = deviceId
    }

    fun setOutputDevice(deviceId: Int?) {
        outputDeviceId = deviceId
    }

    fun loadModel(path: String): Boolean {
        return try {
            processor.loadModel(path)
            _state.value = RvcState.Loaded
            _info.value = "模型已加载"
            true
        } catch (e: Exception) {
            _state.value = RvcState.Error
            _info.value = "加载失败: ${e.message}"
            false
        }
    }

    fun start() {
        if (_state.value == RvcState.Running) return
        if (_state.value != RvcState.Loaded) {
            _info.value = "请先加载模型"
            return
        }
        ensureScope()
        player.setCommunicationMode(false) // media channel
        player.setPreferredOutputDevice(outputDeviceId)
        _state.value = RvcState.Running
        job = scope.launch {
            recorder.setPcmCallback { pcm ->
                val out = processor.process(pcm)
                out?.let { player.playPcm(it) }
            }
            recorder.start(inputDeviceId)
            while (isActive) {
                delay(50)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder.stop()
        _state.value = if (_state.value == RvcState.Running) RvcState.Loaded else _state.value
    }

    fun unload() {
        stop()
        processor.unload()
        _state.value = RvcState.Idle
        _info.value = ""
    }

    fun cleanup() {
        unload()
        scope.cancel()
        recorder.cleanup()
        player.cleanup()
    }
}

enum class RvcState {
    Idle, Loaded, Running, Error
}
