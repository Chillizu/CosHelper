package com.coshelper.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.coshelper.audio.AudioPlayer
import com.coshelper.audio.AudioRecorder
import com.coshelper.audio.OpusCodec
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NearbyChatManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectionsClient: ConnectionsClient = com.google.android.gms.nearby.Nearby.getConnectionsClient(appContext)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun ensureScope() {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    private val codec = OpusCodec.getInstance()
    private val recorder = AudioRecorder(appContext)
    private val player = AudioPlayer(appContext)

    private val _state = MutableStateFlow<ChatState>(ChatState.Idle)
    val state: StateFlow<ChatState> = _state

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    val events: kotlinx.coroutines.flow.SharedFlow<ChatEvent> = _events

    private val pendingEndpoints = ConcurrentHashMap<String, ConnectionInfo>()
    private val connectedEndpoints = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var isPttActive = false

    private val serviceId = "com.coshelper.audio.v1"

    private val strategy = Strategy.P2P_POINT_TO_POINT

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _events.tryEmit(ChatEvent.EndpointFound(endpointId, info.endpointName))
            if (connectedEndpoints.isEmpty() && _state.value !is ChatState.Connecting) {
                requestConnection(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _events.tryEmit(ChatEvent.EndpointLost(endpointId))
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingEndpoints[endpointId] = info
            _state.value = ChatState.Connecting(endpointId, info.endpointName)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (resolution.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                _state.value = ChatState.Connected(endpointId)
                _events.tryEmit(ChatEvent.Connected(endpointId))
                stopDiscovery() // keep advertising
            } else {
                _state.value = ChatState.Idle
                _events.tryEmit(ChatEvent.ConnectionFailed(endpointId, resolution.status.statusMessage ?: "unknown"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            _events.tryEmit(ChatEvent.Disconnected(endpointId))
            _state.value = ChatState.Idle
            startDiscovery() // auto reconnect
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val pcm = codec.decode(bytes)
                pcm?.let { player.playPcm(it) }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun start() {
        if (!hasPermissions()) {
            _events.tryEmit(ChatEvent.Error("Missing required permissions"))
            return
        }
        ensureScope()
        _state.value = ChatState.Searching
        startAdvertising()
        startDiscovery()

        // Search timeout: if still searching after 30s, go idle
        scope.launch {
            delay(30_000)
            if (_state.value == ChatState.Searching) {
                stopDiscovery()
                _state.value = ChatState.Idle
            }
        }
    }

    fun stop() {
        stopDiscovery()
        stopAdvertising()
        connectedEndpoints.forEach { connectionsClient.disconnectFromEndpoint(it) }
        connectedEndpoints.clear()
        pendingEndpoints.clear()
        stopPtt()
        _state.value = ChatState.Idle
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            "CosHelper",
            serviceId,
            connectionLifecycleCallback,
            options
        )
            .addOnSuccessListener { _events.tryEmit(ChatEvent.AdvertisingStarted) }
            .addOnFailureListener { _events.tryEmit(ChatEvent.Error("Advertising failed: ${it.message}")) }
    }

    private fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            options
        )
            .addOnSuccessListener { _events.tryEmit(ChatEvent.DiscoveryStarted) }
            .addOnFailureListener { _events.tryEmit(ChatEvent.Error("Discovery failed: ${it.message}")) }
    }

    private fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    private fun requestConnection(endpointId: String) {
        _state.value = ChatState.Connecting(endpointId, endpointId)
        connectionsClient.requestConnection("CosHelper", endpointId, connectionLifecycleCallback)
    }

    fun startPtt() {
        if (!hasPermissions()) return
        ensureScope()
        isPttActive = true
        _state.value = when (val s = _state.value) {
            is ChatState.Connected -> ChatState.Sending(s.endpointId)
            else -> ChatState.Sending(null)
        }
        recorder.setPcmCallback { pcm ->
            if (isPttActive) {
                val frame = codec.encode(pcm)
                frame?.let { sendToAll(it) }
            }
        }
        recorder.start()
    }

    fun stopPtt() {
        isPttActive = false
        recorder.stop()
        if (_state.value is ChatState.Sending) {
            val endpointId = (_state.value as? ChatState.Sending)?.endpointId
            if (endpointId != null) {
                _state.value = ChatState.Connected(endpointId)
            } else {
                _state.value = ChatState.Idle
            }
        }
    }

    private fun sendToAll(data: ByteArray) {
        if (connectedEndpoints.isEmpty()) return
        val payload = Payload.fromBytes(data)
        connectedEndpoints.forEach { connectionsClient.sendPayload(it, payload) }
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
        recorder.cleanup()
        player.cleanup()
    }
}

sealed class ChatState {
    object Idle : ChatState()
    object Searching : ChatState()
    data class Connecting(val endpointId: String, val name: String) : ChatState()
    data class Connected(val endpointId: String) : ChatState()
    data class Sending(val endpointId: String?) : ChatState()
}

sealed class ChatEvent {
    object AdvertisingStarted : ChatEvent()
    object DiscoveryStarted : ChatEvent()
    data class EndpointFound(val endpointId: String, val name: String) : ChatEvent()
    data class EndpointLost(val endpointId: String) : ChatEvent()
    data class Connected(val endpointId: String) : ChatEvent()
    data class Disconnected(val endpointId: String) : ChatEvent()
    data class ConnectionFailed(val endpointId: String, val reason: String) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}
