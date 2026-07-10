package com.coshelper.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.coshelper.audio.AudioPlayer
import com.coshelper.audio.AudioRecorder
import com.coshelper.audio.OpusCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class HotspotChatManager(context: Context) {

    private val appContext = context.applicationContext
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fallbackJob: Job? = null

    private fun ensureScope() {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    private val codec = OpusCodec.getInstance()
    private val recorder = AudioRecorder(appContext)
    private val player = AudioPlayer(appContext)

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state

    private val _status = MutableStateFlow("待机")
    val status: StateFlow<String> = _status

    private val _events = MutableSharedFlow<HotspotEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HotspotEvent> = _events

    private var serverSocket: ServerSocket? = null
    private val clientSockets = ConcurrentHashMap<String, Socket>()
    private val isPttActive = AtomicBoolean(false)
    private var isServer = false

    private var inputDeviceId: Int? = null

    fun setInputDevice(deviceId: Int?) {
        inputDeviceId = deviceId
    }

    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private val serviceType = "_coshelper._tcp"
    private val serviceName = "CosHelper"
    private val port = 19999
    private var fallbackHost = "192.168.43.1"

    fun setFallbackHost(host: String) {
        fallbackHost = host
    }

    fun startServer() {
        if (!hasPermissions()) {
            _status.value = "缺少权限"
            _events.tryEmit(HotspotEvent.Error("Missing permissions"))
            return
        }
        stop()
        ensureScope()
        isServer = true
        _state.value = HotspotState.Server
        _status.value = "启动服务器…"
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                registerService()
                _events.tryEmit(HotspotEvent.ServerStarted(port))
                _status.value = "服务器已启动"
                while (!serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    val id = socket.inetAddress.hostAddress ?: "unknown"
                    clientSockets[id] = socket
                    _events.tryEmit(HotspotEvent.ClientConnected(id))
                    _status.value = "客户端已连接: $id"
                    launch { handleSocket(socket, id) }
                }
            } catch (e: IOException) {
                _events.tryEmit(HotspotEvent.Error("Server failed: ${e.message}"))
                _status.value = "服务器启动失败: ${e.message}"
                _state.value = HotspotState.Idle
            }
        }
    }

    fun startClient() {
        if (!hasPermissions()) {
            _status.value = "缺少权限"
            _events.tryEmit(HotspotEvent.Error("Missing permissions"))
            return
        }
        stop()
        ensureScope()
        isServer = false
        _state.value = HotspotState.ClientSearching
        _status.value = "正在搜索…"
        startNsdDiscovery()
    }

    private fun connectClient(host: String, port: Int) {
        if (_state.value !is HotspotState.ClientSearching) return
        _status.value = "正在连接 $host:$port…"
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5000)
                val id = socket.inetAddress.hostAddress ?: host
                clientSockets[id] = socket
                _state.value = HotspotState.ClientConnected(id)
                _events.tryEmit(HotspotEvent.ConnectedToServer(id))
                _status.value = "已连接"
                handleSocket(socket, id)
            } catch (e: IOException) {
                _events.tryEmit(HotspotEvent.Error("Connect failed: ${e.message}"))
                _status.value = "连接失败: ${e.message}"
                _state.value = HotspotState.Idle
            }
        }
    }

    private fun handleSocket(socket: Socket, id: String) {
        try {
            val input = socket.getInputStream()
            val header = ByteArray(10)
            while (!socket.isClosed && socket.isConnected) {
                // read 10-byte header
                var read = 0
                while (read < 10) {
                    val r = input.read(header, read, 10 - read)
                    if (r < 0) throw IOException("EOF")
                    read += r
                }
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val frameType = buf.short.toInt() and 0xFFFF
                // seq and timestamp are part of the protocol but not needed for playback
                buf.short // sequence
                buf.int // timestamp
                val dataLen = buf.short.toInt() and 0xFFFF
                if (frameType != 2 || dataLen > 4096) continue
                val payload = ByteArray(dataLen)
                var pr = 0
                while (pr < dataLen) {
                    val r = input.read(payload, pr, dataLen - pr)
                    if (r < 0) throw IOException("EOF")
                    pr += r
                }
                val fullFrame = header + payload
                val pcm = codec.decode(fullFrame)
                pcm?.let { player.playPcm(it) }
            }
        } catch (_: IOException) {
            // expected on close
        } finally {
            clientSockets.remove(id)
            try { socket.close() } catch (_: Throwable) {}
            _events.tryEmit(HotspotEvent.Disconnected(id))
            _status.value = "连接断开"
            if (_state.value is HotspotState.ClientConnected) {
                _state.value = HotspotState.Idle
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo, listener: NsdManager.ResolveListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.resolveService(serviceInfo, appContext.mainExecutor, listener)
        } else {
            nsdManager.resolveService(serviceInfo, listener)
        }
    }

    @Suppress("DEPRECATION")
    private fun getHostAddress(serviceInfo: NsdServiceInfo?): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo?.hostAddresses?.firstOrNull()?.hostAddress
        } else {
            serviceInfo?.host?.hostAddress
        }
    }

    private fun registerService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@HotspotChatManager.serviceName
            serviceType = this@HotspotChatManager.serviceType
            port = this@HotspotChatManager.port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                _events.tryEmit(HotspotEvent.NsdRegistered(info?.serviceName ?: serviceName))
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        }
        nsdRegistrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                _events.tryEmit(HotspotEvent.NsdDiscoveryStarted)
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo?.serviceName == serviceName) {
                    val listener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            serviceInfo?.let { info ->
                                getHostAddress(info)?.let { host ->
                                    connectClient(host, info.port)
                                }
                            }
                        }
                    }
                    resolveService(serviceInfo, listener)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        nsdDiscoveryListener = listener
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)

        // Fallback after 5 seconds
        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            delay(5.seconds)
            if (_state.value == HotspotState.ClientSearching) {
                connectClient(fallbackHost, port)
            }
        }
    }

    fun stop() {
        fallbackJob?.cancel()
        fallbackJob = null
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        clientSockets.values.forEach { try { it.close() } catch (_: Throwable) {} }
        clientSockets.clear()
        nsdRegistrationListener?.let { nsdManager.unregisterService(it) }
        nsdRegistrationListener = null
        nsdDiscoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        nsdDiscoveryListener = null
        stopPtt()
        _state.value = HotspotState.Idle
        _status.value = "待机"
    }

    fun startPtt() {
        if (!hasPermissions()) return
        ensureScope()
        isPttActive.set(true)
        recorder.setPcmCallback { pcm ->
            if (isPttActive.get()) {
                val frame = codec.encode(pcm)
                frame?.let { sendToAll(it) }
            }
        }
        recorder.start(inputDeviceId)
    }

    fun stopPtt() {
        isPttActive.set(false)
        recorder.stop()
    }

    private fun sendToAll(data: ByteArray) {
        clientSockets.values.forEach { socket ->
            try {
                socket.getOutputStream().write(data)
            } catch (e: IOException) {
                _events.tryEmit(HotspotEvent.Error("Send failed: ${e.message}"))
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val baseOk = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!baseOk) return false

        val wifiOk = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        if (!wifiOk) return false

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
        recorder.cleanup()
        player.cleanup()
    }
}

sealed class HotspotState {
    object Idle : HotspotState()
    object Server : HotspotState()
    object ClientSearching : HotspotState()
    data class ClientConnected(val host: String) : HotspotState()
}

sealed class HotspotEvent {
    data class ServerStarted(val port: Int) : HotspotEvent()
    data class ClientConnected(val id: String) : HotspotEvent()
    data class ConnectedToServer(val id: String) : HotspotEvent()
    data class Disconnected(val id: String) : HotspotEvent()
    data class NsdRegistered(val name: String) : HotspotEvent()
    object NsdDiscoveryStarted : HotspotEvent()
    data class Error(val message: String) : HotspotEvent()
}
