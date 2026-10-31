package com.friends.ships.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class RoomJoinException(val reason: String) : Exception(reason)

class RoomClient : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null
    private val writeLock = Any()
    private val handshake = CompletableDeferred<JoinAcceptedWire>()
    private val _messages = MutableSharedFlow<Packet>(extraBufferCapacity = 64)
    val messages: SharedFlow<Packet> = _messages.asSharedFlow()
    @Volatile var playerId: String? = null
        private set
    @Volatile var isConnected: Boolean = false
        private set

    suspend fun connect(host: String, port: Int, name: String, reconnectId: String? = null): JoinAcceptedWire {
        withContext(Dispatchers.IO) {
            val clientSocket = Socket()
            clientSocket.tcpNoDelay = true
            clientSocket.connect(InetSocketAddress(host.trim(), port), 8_000)
            socket = clientSocket
            writer = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
            send(WireJson.packet(MessageType.JOIN, JoinWire(name, reconnectId)))
            isConnected = true
            readerJob = scope.launch {
                try {
                    while (isActive && !clientSocket.isClosed) {
                        val line = reader.readLine() ?: break
                        val packet = runCatching { WireJson.instance.decodeFromString<Packet>(line) }.getOrNull() ?: continue
                        when (packet.type) {
                            MessageType.PING -> send(Packet(MessageType.PONG))
                            MessageType.JOIN_ACCEPTED -> {
                                val accepted = WireJson.decode<JoinAcceptedWire>(packet)
                                playerId = accepted.playerId
                                handshake.complete(accepted)
                            }
                            MessageType.JOIN_REJECTED -> {
                                val rejected = WireJson.decode<JoinRejectedWire>(packet)
                                handshake.completeExceptionally(RoomJoinException(rejected.reason))
                            }
                        }
                        _messages.emit(packet)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // EOF / Wi-Fi changes are surfaced as host disconnect below.
                } finally {
                    isConnected = false
                    if (!handshake.isCompleted) handshake.completeExceptionally(RoomJoinException("CONNECTION_FAILED"))
                    _messages.emit(Packet(MessageType.ERROR))
                }
            }
        }
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(3_000)
                send(Packet(MessageType.PING))
            }
        }
        return withTimeout(10_000) { handshake.await() }
    }

    fun send(packet: Packet) {
        val output = writer ?: return
        runCatching {
            synchronized(writeLock) {
                output.write(WireJson.instance.encodeToString(packet))
                output.newLine()
                output.flush()
            }
        }.onFailure { isConnected = false }
    }

    inline fun <reified T> send(type: String, payload: T) = send(WireJson.packet(type, payload))

    override fun close() {
        isConnected = false
        heartbeatJob?.cancel()
        readerJob?.cancel()
        runCatching { socket?.close() }
        scope.cancel()
    }
}
