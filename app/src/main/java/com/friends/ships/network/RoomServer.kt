package com.friends.ships.network

import com.friends.ships.game.Cell
import com.friends.ships.game.GameEngine
import com.friends.ships.game.ShipPlacement
import com.friends.ships.game.ShotKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class RoomPhase { LOBBY, PLACEMENT, BATTLE, FINISHED }

data class PlayerSummary(
    val id: String,
    val name: String,
    val color: Int,
    val ready: Boolean,
    val connected: Boolean,
    val alive: Boolean
)

data class RoomSnapshot(
    val hostId: String = "",
    val players: List<PlayerSummary> = emptyList(),
    val settings: GameSettingsWire = GameSettingsWire(),
    val phase: String = "LOBBY",
    val currentTurnId: String? = null,
    val remainingMillis: Long = 0L,
    val winnerId: String? = null,
    val eventLog: List<String> = emptyList(),
    val lastFireResult: FireResultWire? = null,
    val isPaused: Boolean = false
)

/** Host-authoritative TCP room. Ship layouts stay on the host; peers receive only public shot results. */
class RoomServer(
    private val port: Int = 8765,
    private val random: Random = Random.Default
) : Closeable {
    private data class PlayerState(
        val id: String,
        val name: String,
        val color: Int,
        var ready: Boolean = false,
        var connected: Boolean = true,
        var alive: Boolean = true,
        var ships: List<ShipPlacement> = emptyList(),
        val shotsAgainst: MutableMap<String, MutableSet<Cell>> = mutableMapOf(),
        val receivedShots: MutableSet<Cell> = mutableSetOf(),
        var shots: Int = 0,
        var hits: Int = 0,
        var shipsSunk: Int = 0,
        var disconnectJob: Job? = null
    )

    private data class Peer(
        val socket: Socket,
        val writer: BufferedWriter,
        val playerId: String,
        val writeLock: Any = Any(),
        @Volatile var lastPong: Long = System.currentTimeMillis()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val players = linkedMapOf<String, PlayerState>()
    private val peers = linkedMapOf<String, Peer>()
    private val pendingDisconnects = linkedSetOf<String>()
    private var pausedRemainingMillis: Long = 0L
    private val eventLog = java.util.ArrayDeque<String>()
    private var serverSocket: ServerSocket? = null
    private var ticker: Job? = null
    private var phase = RoomPhase.LOBBY
    private var settings = GameSettingsWire()
    private var hostId = UUID.randomUUID().toString()
    private var currentTurnId: String? = null
    private var deadlineMillis: Long = 0L
    private var winnerId: String? = null
    private var lastFireResult: FireResultWire? = null
    private val _state = MutableStateFlow(RoomSnapshot())
    val state: StateFlow<RoomSnapshot> = _state.asStateFlow()
    val hostPlayerId: String get() = hostId

    @Throws(Exception::class)
    fun start(hostName: String, roomSettings: GameSettingsWire = GameSettingsWire()): Int {
        require(hostName.isNotBlank())
        settings = roomSettings.copy(maxPlayers = roomSettings.maxPlayers.coerceIn(2, 6))
        val socket = ServerSocket(port)
        socket.reuseAddress = true
        serverSocket = socket
        players[hostId] = PlayerState(hostId, hostName.trim(), 0xFF35D6C5.toInt())
        publish()
        scope.launch { acceptLoop(socket) }
        ticker = scope.launch { tickerLoop() }
        return socket.localPort
    }

    suspend fun startPlacement(): Boolean = mutex.withLock {
        if (phase != RoomPhase.LOBBY || players.size < 2) return@withLock false
        phase = RoomPhase.PLACEMENT
        players.values.forEach { it.ready = false; it.ships = emptyList() }
        broadcast(Packet(MessageType.START_PLACEMENT))
        broadcastLobby()
        publish()
        true
    }

    suspend fun updateSettings(newSettings: GameSettingsWire) = mutex.withLock {
        if (phase != RoomPhase.LOBBY) return@withLock
        settings = newSettings.copy(maxPlayers = newSettings.maxPlayers.coerceIn(2, 6))
        broadcast(Packet(MessageType.SETTINGS_UPDATE, WireJson.instance.encodeToJsonElement(settings).let { it as kotlinx.serialization.json.JsonObject }))
        broadcastLobby()
        publish()
    }

    suspend fun setHostPlacement(ships: List<ShipPlacement>): Boolean = mutex.withLock {
        val host = players[hostId] ?: return@withLock false
        if (phase != RoomPhase.PLACEMENT) return@withLock false
        if (!GameEngine.validateFleet(ships, settings.noTouching).valid) return@withLock false
        host.ships = ships.toList()
        host.ready = true
        broadcastLobby()
        maybeStartGame()
        publish()
        true
    }

    suspend fun hostFire(targetId: String, cell: Cell): Boolean = mutex.withLock {
        fireInternal(hostId, targetId, cell)
    }

    suspend fun hostRematch() = mutex.withLock {
        if (phase == RoomPhase.FINISHED) resetForRematch()
    }

    suspend fun removeHostRoom() {
        close()
    }

    private suspend fun acceptLoop(socket: ServerSocket) = withContext(Dispatchers.IO) {
        while (scope.isActive && !socket.isClosed) {
            try {
                val client = socket.accept()
                client.tcpNoDelay = true
                scope.launch { handleSocket(client) }
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                if (!socket.isClosed) delay(250)
            }
        }
    }

    private suspend fun handleSocket(socket: Socket) = withContext(Dispatchers.IO) {
        var peer: Peer? = null
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val firstLine = reader.readLine() ?: return@withContext
            val packet = runCatching { WireJson.instance.decodeFromString<Packet>(firstLine) }.getOrNull()
            if (packet?.type != MessageType.JOIN) {
                writer.write(WireJson.instance.encodeToString(Packet(MessageType.JOIN_REJECTED, WireJson.instance.encodeToJsonElement(JoinRejectedWire("JOIN_REQUIRED")).let { it as kotlinx.serialization.json.JsonObject })))
                writer.newLine(); writer.flush()
                return@withContext
            }
            val join = runCatching { WireJson.decode<JoinWire>(packet) }.getOrNull() ?: return@withContext
            peer = registerPeer(socket, writer, join)
            if (peer == null) return@withContext
            while (scope.isActive && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val incoming = runCatching { WireJson.instance.decodeFromString<Packet>(line) }.getOrNull() ?: continue
                handlePacket(peer, incoming)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A disconnected socket is handled below and can reconnect with the stable player id.
        } finally {
            runCatching { socket.close() }
            peer?.let { markDisconnected(it) }
        }
    }

    private suspend fun registerPeer(socket: Socket, writer: BufferedWriter, join: JoinWire): Peer? = mutex.withLock {
        val name = join.name.trim().take(20)
        if (name.isBlank()) {
            writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("INVALID_NAME"))))
            socket.close(); return@withLock null
        }
        if (phase != RoomPhase.LOBBY && phase != RoomPhase.PLACEMENT && join.reconnectId == null) {
            writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("GAME_STARTED"))))
            socket.close(); return@withLock null
        }
        val reconnecting = join.reconnectId?.let { players[it] }
        if (phase != RoomPhase.LOBBY && (reconnecting == null || reconnecting.connected)) {
            writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("GAME_STARTED"))))
            socket.close(); return@withLock null
        }
        if (reconnecting != null && reconnecting.connected) {
            writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("ALREADY_CONNECTED"))))
            socket.close(); return@withLock null
        }
        val player = if (reconnecting != null && !reconnecting.connected) {
            reconnecting.disconnectJob?.cancel()
            reconnecting.connected = true
            reconnecting
        } else {
            if (players.size >= settings.maxPlayers) {
                writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("ROOM_FULL"))))
                socket.close(); return@withLock null
            }
            if (players.values.any { it.name.equals(name, ignoreCase = true) }) {
                writePacket(writer, Packet(MessageType.JOIN_REJECTED, payload(JoinRejectedWire("NAME_TAKEN"))))
                socket.close(); return@withLock null
            }
            val id = UUID.randomUUID().toString()
            PlayerState(id, name, PLAYER_COLORS[players.size % PLAYER_COLORS.size]).also { players[id] = it }
        }
        val connection = Peer(socket, writer, player.id)
        peers[player.id]?.let { old -> runCatching { old.socket.close() } }
        peers[player.id] = connection
        connection.lastPong = System.currentTimeMillis()
        if (reconnecting != null) {
            pendingDisconnects.remove(player.id)
            resumeAfterReconnectIfPossible()
        }
        val lobby = lobbyWire()
        writePacket(writer, WireJson.packet(MessageType.JOIN_ACCEPTED, JoinAcceptedWire(player.id, lobby)))
        broadcastLobby()
        if (phase == RoomPhase.BATTLE) broadcastTurn()
        addEvent("${player.name} joined")
        publish()
        connection
    }

    private suspend fun handlePacket(peer: Peer, packet: Packet) {
        when (packet.type) {
            MessageType.PONG -> peer.lastPong = System.currentTimeMillis()
            MessageType.PING -> send(peer, Packet(MessageType.PONG))
            MessageType.PLACEMENT_READY -> {
                val placement = runCatching { WireJson.decode<PlacementWire>(packet) }.getOrNull() ?: return
                mutex.withLock {
                    val player = players[peer.playerId] ?: return@withLock
                    if (phase != RoomPhase.PLACEMENT || !player.connected) return@withLock
                    if (!GameEngine.validateFleet(placement.ships, settings.noTouching).valid) {
                        send(peer, Packet(MessageType.ERROR, payload(ErrorWire("INVALID_PLACEMENT"))))
                        return@withLock
                    }
                    player.ships = placement.ships.toList()
                    player.ready = true
                    broadcastLobby()
                    maybeStartGame()
                    publish()
                }
            }
            MessageType.FIRE -> {
                val fire = runCatching { WireJson.decode<FireWire>(packet) }.getOrNull() ?: return
                mutex.withLock { fireInternal(peer.playerId, fire.targetId, fire.cell) }
            }
            MessageType.REMATCH -> mutex.withLock {
                if (phase == RoomPhase.FINISHED && peer.playerId == hostId) resetForRematch()
            }
        }
    }

    private fun maybeStartGame() {
        if (phase != RoomPhase.PLACEMENT || players.size < 2) return
        if (players.values.any { !it.ready || !it.connected }) return
        phase = RoomPhase.BATTLE
        val alive = players.values.filter { it.alive }.map { it.id }
        currentTurnId = alive.randomOrNull(random)
        setDeadline()
        broadcast(Packet(MessageType.ALL_READY))
        broadcastTurn()
        addEvent("Battle started")
    }

    private fun fireInternal(attackerId: String, targetId: String, cell: Cell): Boolean {
        if (phase != RoomPhase.BATTLE || pendingDisconnects.isNotEmpty() || attackerId != currentTurnId) return false
        val attacker = players[attackerId] ?: return false
        val target = players[targetId] ?: return false
        if (!attacker.alive || !target.alive || attackerId == targetId || !target.connected) return false
        val tried = attacker.shotsAgainst.getOrPut(targetId) { mutableSetOf() }
        if (!cell.isOnBoard() || cell in tried) return false
        val resolution = GameEngine.resolveShot(target.ships, tried, cell, target.receivedShots)
        tried += cell
        target.receivedShots += cell
        attacker.shots++
        if (resolution.kind != ShotKind.MISS) attacker.hits++
        if (resolution.kind == ShotKind.SUNK) attacker.shipsSunk++
        val eliminated = GameEngine.isFleetSunk(target.ships, target.receivedShots)
        if (eliminated) {
            target.alive = false
            target.ready = false
            addEvent("${target.name} eliminated")
        }
        val aliveIds = players.values.filter { it.alive && it.connected }.map { it.id }.toSet()
        winnerId = GameEngine.winner(aliveIds)
        if (winnerId != null) {
            phase = RoomPhase.FINISHED
            currentTurnId = null
            deadlineMillis = 0L
        } else if (resolution.kind == ShotKind.MISS || !settings.extraTurnOnHit || eliminated) {
            currentTurnId = GameEngine.nextAlive(players.keys.toList(), attackerId, aliveIds)
            setDeadline()
        } else {
            currentTurnId = attackerId
            setDeadline()
        }
        val result = FireResultWire(
            attackerId = attackerId,
            targetId = targetId,
            cell = cell,
            kind = resolution.kind,
            sunkType = resolution.sunkShip?.type,
            sunkCells = resolution.sunkShip?.cells ?: emptyList(),
            targetEliminated = eliminated,
            nextTurnId = currentTurnId,
            winnerId = winnerId
        )
        lastFireResult = result
        broadcast(WireJson.packet(MessageType.FIRE_RESULT, result))
        if (eliminated) broadcast(WireJson.packet(MessageType.PLAYER_ELIMINATED, PlayerLeftWire(targetId, "SUNK")))
        if (winnerId != null) broadcast(WireJson.packet(MessageType.GAME_OVER, TurnWire(winnerId, 0, aliveIds.toList())))
        else broadcastTurn()
        addEvent("${attacker.name} fired at ${target.name} ${cell}: ${resolution.kind}")
        publish()
        return true
    }

    private fun setDeadline() {
        deadlineMillis = if (settings.timerSeconds <= 0) 0L else System.currentTimeMillis() + settings.timerSeconds * 1000L
    }

    private fun broadcastTurn() {
        val remaining = when {
            pendingDisconnects.isNotEmpty() -> pausedRemainingMillis
            deadlineMillis == 0L -> 0L
            else -> (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0)
        }
        val alive = players.values.filter { it.alive && it.connected }.map { it.id }
        val update = TurnWire(currentTurnId, remaining, alive, pendingDisconnects.isNotEmpty())
        broadcast(WireJson.packet(MessageType.TURN_UPDATE, update))
        peers.values.toList().forEach { peer ->
            if (pendingDisconnects.isEmpty() && peer.playerId == currentTurnId) {
                send(peer, WireJson.packet(MessageType.YOUR_TURN, update))
            }
        }
    }

    private fun resumeAfterReconnectIfPossible() {
        if (pendingDisconnects.isNotEmpty() || phase != RoomPhase.BATTLE) return
        if (settings.timerSeconds > 0) deadlineMillis = System.currentTimeMillis() + pausedRemainingMillis
        else deadlineMillis = 0L
        pausedRemainingMillis = 0L
        broadcastTurn()
    }

    private suspend fun tickerLoop() {
        while (scope.isActive) {
            delay(3_000)
            val now = System.currentTimeMillis()
            val toDrop = mutableListOf<String>()
            peers.values.toList().forEach { peer ->
                if (now - peer.lastPong > 10_000) toDrop += peer.playerId
                else send(peer, Packet(MessageType.PING))
            }
            toDrop.forEach { id -> peers[id]?.let { runCatching { it.socket.close() } } }
            mutex.withLock {
                if (phase == RoomPhase.BATTLE && pendingDisconnects.isEmpty() && deadlineMillis > 0) broadcastTurn()
                if (phase == RoomPhase.BATTLE && pendingDisconnects.isEmpty() && deadlineMillis > 0 && now >= deadlineMillis) {
                    val current = currentTurnId
                    if (current != null) {
                        val possibleTargets = players.values.filter { it.alive && it.connected && it.id != current }
                        val candidate = possibleTargets.mapNotNull { target ->
                            val already = players[current]?.shotsAgainst?.get(target.id).orEmpty()
                            val remaining = (0..9).flatMap { row -> (0..9).map { col -> Cell(row, col) } }.filter { it !in already }
                            if (remaining.isEmpty()) null else target to remaining
                        }.randomOrNull(random)
                        if (candidate != null) fireInternal(current, candidate.first.id, candidate.second.random(random))
                    }
                }
                publish()
            }
        }
    }

    private suspend fun markDisconnected(peer: Peer) = mutex.withLock {
        val playerId = peer.playerId
        if (peers[playerId] !== peer) return@withLock
        val player = players[playerId] ?: return@withLock
        if (playerId == hostId) return@withLock
        peers.remove(playerId)
        player.connected = false
        addEvent("${player.name} disconnected")
        player.disconnectJob?.cancel()
        if (phase == RoomPhase.BATTLE) {
            if (pendingDisconnects.isEmpty()) {
                pausedRemainingMillis = if (deadlineMillis > 0) (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0) else 0L
            }
            pendingDisconnects += playerId
            deadlineMillis = 0L
            broadcastTurn()
            player.disconnectJob = scope.launch {
                delay(30_000)
                mutex.withLock {
                    val current = players[playerId]
                    if (current != null && !current.connected) {
                        current.alive = false
                        current.ships = emptyList()
                        pendingDisconnects.remove(playerId)
                        addEvent("${current.name} removed after reconnect timeout")
                        val aliveIds = players.values.filter { it.alive && it.connected }.map { it.id }.toSet()
                        if (currentTurnId == playerId) currentTurnId = GameEngine.nextAlive(players.keys.toList(), playerId, aliveIds)
                        winnerId = GameEngine.winner(aliveIds)
                        if (winnerId != null) {
                            phase = RoomPhase.FINISHED
                            deadlineMillis = 0L
                            broadcast(WireJson.packet(MessageType.GAME_OVER, TurnWire(winnerId, 0, aliveIds.toList())))
                        } else {
                            resumeAfterReconnectIfPossible()
                        }
                        broadcast(WireJson.packet(MessageType.PLAYER_LEFT, PlayerLeftWire(playerId, "DISCONNECTED")))
                        broadcastTurn()
                        publish()
                    }
                }
            }
        } else {
            players.remove(playerId)
        }
        broadcastLobby()
        publish()
    }

    private fun resetForRematch() {
        phase = RoomPhase.LOBBY
        winnerId = null
        lastFireResult = null
        currentTurnId = null
        deadlineMillis = 0L
        pendingDisconnects.clear()
        pausedRemainingMillis = 0L
        players.values.forEach {
            it.ready = false
            it.alive = true
            it.ships = emptyList()
            it.shotsAgainst.clear()
            it.receivedShots.clear()
            it.shots = 0
            it.hits = 0
            it.shipsSunk = 0
        }
        eventLog.clear()
        broadcastLobby()
        broadcast(Packet(MessageType.REMATCH))
        publish()
    }

    private fun lobbyWire() = LobbyWire(
        players = players.values.map { PlayerWire(it.id, it.name, it.color, it.ready, it.connected, it.alive) },
        settings = settings,
        phase = phase.name,
        hostId = hostId
    )

    private fun broadcastLobby() = broadcast(WireJson.packet(MessageType.LOBBY_UPDATE, lobbyWire()))

    private fun broadcast(packet: Packet) {
        peers.values.toList().forEach { send(it, packet) }
    }

    private fun send(peer: Peer, packet: Packet) = writePacket(peer.writer, packet, peer.writeLock)

    private fun writePacket(writer: BufferedWriter, packet: Packet, lock: Any = writer) {
        runCatching {
            synchronized(lock) {
                writer.write(WireJson.instance.encodeToString(packet))
                writer.newLine()
                writer.flush()
            }
        }
    }

    private inline fun <reified T> payload(value: T): kotlinx.serialization.json.JsonObject =
        WireJson.instance.encodeToJsonElement(value).let { it as kotlinx.serialization.json.JsonObject }

    private fun addEvent(text: String) {
        eventLog.addFirst(text)
        while (eventLog.size > 30) eventLog.removeLast()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        _state.value = RoomSnapshot(
            hostId = hostId,
            players = players.values.map { PlayerSummary(it.id, it.name, it.color, it.ready, it.connected, it.alive) },
            settings = settings,
            phase = phase.name,
            currentTurnId = currentTurnId,
            remainingMillis = when {
                pendingDisconnects.isNotEmpty() -> pausedRemainingMillis
                deadlineMillis == 0L -> 0L
                else -> (deadlineMillis - now).coerceAtLeast(0)
            },
            winnerId = winnerId,
            eventLog = eventLog.toList(),
            lastFireResult = lastFireResult,
            isPaused = pendingDisconnects.isNotEmpty()
        )
    }

    override fun close() {
        ticker?.cancel()
        scope.coroutineContext[Job]?.cancel()
        runCatching { serverSocket?.close() }
        peers.values.forEach { runCatching { it.socket.close() } }
        peers.clear()
    }

    companion object {
        private val PLAYER_COLORS = listOf(
            0xFF35D6C5.toInt(), 0xFFFFB454.toInt(), 0xFF7C9EFF.toInt(),
            0xFFFF718B.toInt(), 0xFFB98AFF.toInt(), 0xFF85D66B.toInt()
        )
    }
}
