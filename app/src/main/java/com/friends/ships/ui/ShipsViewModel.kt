package com.friends.ships.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.friends.ships.R
import com.friends.ships.data.AppSettings
import com.friends.ships.data.SettingsRepository
import com.friends.ships.game.Cell
import com.friends.ships.game.GameEngine
import com.friends.ships.game.HuntTargetBot
import com.friends.ships.game.ShipPlacement
import com.friends.ships.game.ShipType
import com.friends.ships.game.ShotKind
import com.friends.ships.network.DiscoveredRoom
import com.friends.ships.network.FireResultWire
import com.friends.ships.network.FireWire
import com.friends.ships.network.GameSettingsWire
import com.friends.ships.network.LobbyWire
import com.friends.ships.network.MessageType
import com.friends.ships.network.NsdDiscovery
import com.friends.ships.network.PlayerSummary
import com.friends.ships.network.PlayerWire
import com.friends.ships.network.PlacementWire
import com.friends.ships.network.RoomClient
import com.friends.ships.network.RoomJoinException
import com.friends.ships.network.RoomServer
import com.friends.ships.network.RoomSnapshot
import com.friends.ships.network.TurnWire
import com.friends.ships.network.WireJson
import com.friends.ships.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { HOME, CREATE, JOIN, LOBBY, PLACEMENT, BATTLE, GAME_OVER, SETTINGS, HELP }

data class ShipsUiState(
    val screen: AppScreen = AppScreen.HOME,
    val settings: AppSettings = AppSettings(),
    val playerName: String = "Ali",
    val hostIp: String = "",
    val port: String = "8765",
    val maxPlayers: Int = 6,
    val timerSeconds: Int = 30,
    val extraTurnOnHit: Boolean = true,
    val noTouching: Boolean = false,
    val hostAddress: String = "",
    val isHost: Boolean = false,
    val players: List<PlayerSummary> = emptyList(),
    val myPlayerId: String = "",
    val currentTurnId: String? = null,
    val remainingMillis: Long = 0L,
    val roomPaused: Boolean = false,
    val selectedOpponentId: String? = null,
    val selectedCell: Cell? = null,
    val selectedShip: ShipType? = ShipType.CARRIER,
    val vertical: Boolean = false,
    val placedShips: List<ShipPlacement> = emptyList(),
    val shotMarks: Map<String, Map<Cell, ShotKind>> = emptyMap(),
    val incomingMarks: Map<Cell, ShotKind> = emptyMap(),
    val sunkOutlines: Map<String, Set<Cell>> = emptyMap(),
    val events: List<String> = emptyList(),
    val winnerId: String? = null,
    val error: String? = null,
    val isReady: Boolean = false,
    val placementRemainingSeconds: Int = 90,
    val practiceMode: Boolean = false,
    val shots: Int = 0,
    val hits: Int = 0,
    val shipsSunk: Int = 0,
    val discoveredRooms: List<DiscoveredRoom> = emptyList()
)

class ShipsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val discovery = NsdDiscovery(application)
    private var roomServer: RoomServer? = null
    private var roomClient: RoomClient? = null
    private var hostStateJob: kotlinx.coroutines.Job? = null
    private var clientMessagesJob: kotlinx.coroutines.Job? = null
    private var placementCountdownJob: kotlinx.coroutines.Job? = null
    private var lastProcessedFire: FireResultWire? = null
    private var isReconnecting = false
    private val _ui = MutableStateFlow(ShipsUiState(hostIp = NetworkUtils.localIpv4().orEmpty()))
    val ui: StateFlow<ShipsUiState> = _ui.asStateFlow()

    // Practice mode state is deliberately isolated from the network room state.
    private var practiceOwnFleet: List<ShipPlacement> = emptyList()
    private var practiceBotFleet: List<ShipPlacement> = emptyList()
    private val practiceOwnShots = mutableSetOf<Cell>()
    private val practiceBotIncoming = mutableSetOf<Cell>()
    private var bot = HuntTargetBot()
    private var practiceTurnLocal = true

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { value -> _ui.value = _ui.value.copy(settings = value) }
        }
        viewModelScope.launch {
            discovery.rooms.collectLatest { rooms -> _ui.value = _ui.value.copy(discoveredRooms = rooms) }
        }
    }

    fun setPlayerName(value: String) { _ui.value = _ui.value.copy(playerName = value.take(20)) }
    fun setHostIp(value: String) { _ui.value = _ui.value.copy(hostIp = value.trim()) }
    fun setPort(value: String) { _ui.value = _ui.value.copy(port = value.filter(Char::isDigit).take(5)) }
    fun setTimer(seconds: Int) { _ui.value = _ui.value.copy(timerSeconds = seconds) }
    fun setMaxPlayers(value: Int) { _ui.value = _ui.value.copy(maxPlayers = value.coerceIn(2, 6)) }
    fun setExtraTurn(value: Boolean) { _ui.value = _ui.value.copy(extraTurnOnHit = value) }
    fun setNoTouching(value: Boolean) { _ui.value = _ui.value.copy(noTouching = value) }

    fun open(screen: AppScreen) { _ui.value = _ui.value.copy(screen = screen, error = null) }

    fun setSound(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSound(enabled) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { settingsRepository.setVibration(enabled) }
    fun setTheme(theme: String) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    fun createRoom() {
        val name = _ui.value.playerName.trim()
        val port = _ui.value.port.toIntOrNull() ?: 8765
        if (name.isBlank() || port !in 1024..65535) {
            _ui.value = _ui.value.copy(error = "INVALID_INPUT")
            return
        }
        viewModelScope.launch {
            try {
                closeRoomOnly()
                val settings = GameSettingsWire(
                    timerSeconds = _ui.value.timerSeconds,
                    extraTurnOnHit = _ui.value.extraTurnOnHit,
                    noTouching = _ui.value.noTouching,
                    maxPlayers = _ui.value.maxPlayers
                )
                val server = RoomServer(port)
                withContext(Dispatchers.IO) { server.start(name, settings) }
                roomServer = server
                val hostId = server.hostPlayerId
                discovery.advertise("Ships-${name.take(12)}", port)
                hostStateJob = viewModelScope.launch { server.state.collectLatest(::syncHostState) }
                _ui.value = _ui.value.copy(
                    screen = AppScreen.LOBBY,
                    isHost = true,
                    myPlayerId = hostId,
                    hostAddress = "${NetworkUtils.localIpv4() ?: "?"}:$port",
                    error = null,
                    players = emptyList()
                )
            } catch (error: Exception) {
                _ui.value = _ui.value.copy(error = if (error.message?.contains("Address already in use", true) == true) "PORT_IN_USE" else "HOST_START_FAILED")
            }
        }
    }

    fun beginDiscovery() { discovery.startDiscovery() }

    fun joinRoom(host: String = _ui.value.hostIp, portValue: String = _ui.value.port) {
        val name = _ui.value.playerName.trim()
        val port = portValue.toIntOrNull() ?: 8765
        if (name.isBlank() || host.isBlank() || port !in 1..65535) {
            _ui.value = _ui.value.copy(error = "INVALID_INPUT")
            return
        }
        viewModelScope.launch {
            try {
                closeRoomOnly()
                val client = RoomClient()
                roomClient = client
                clientMessagesJob = viewModelScope.launch {
                    client.messages.collectLatest { packet -> handleClientPacket(packet) }
                }
                val accepted = withContext(Dispatchers.IO) { client.connect(host, port, name) }
                _ui.value = _ui.value.copy(hostIp = host, port = port.toString())
                _ui.value = _ui.value.copy(
                    screen = AppScreen.LOBBY,
                    isHost = false,
                    myPlayerId = accepted.playerId,
                    hostAddress = "$host:$port",
                    error = null
                )
                applyLobby(accepted.lobby)
                discovery.stopDiscovery()
            } catch (error: Exception) {
                val reason = (error as? RoomJoinException)?.reason
                _ui.value = _ui.value.copy(error = when (reason) {
                    "ROOM_FULL" -> "ROOM_FULL"
                    "NAME_TAKEN" -> "NAME_TAKEN"
                    "GAME_STARTED" -> "GAME_STARTED"
                    else -> "CONNECT_FAILED"
                })
                roomClient?.close(); roomClient = null
            }
        }
    }

    fun joinDiscovered(room: DiscoveredRoom) {
        _ui.value = _ui.value.copy(hostIp = room.host, port = room.port.toString())
        joinRoom(room.host, room.port.toString())
    }

    fun startPlacement() {
        viewModelScope.launch {
            val started = roomServer?.startPlacement() == true
            if (!started) _ui.value = _ui.value.copy(error = "NOT_ENOUGH_PLAYERS")
            else {
                _ui.value = _ui.value.copy(screen = AppScreen.PLACEMENT, placedShips = emptyList(), selectedShip = ShipType.CARRIER, isReady = false, placementRemainingSeconds = 90)
                startPlacementCountdown()
            }
        }
    }

    fun rotate() { _ui.value = _ui.value.copy(vertical = !_ui.value.vertical) }
    fun selectShip(type: ShipType) {
        if (_ui.value.placedShips.none { it.type == type }) _ui.value = _ui.value.copy(selectedShip = type)
    }

    fun placeShip(cell: Cell) {
        val state = _ui.value
        val type = state.selectedShip ?: return
        val candidate = ShipPlacement(type, cell, state.vertical)
        val validation = GameEngine.canPlace(state.placedShips, candidate, state.noTouching)
        if (!validation.valid) {
            _ui.value = state.copy(error = "INVALID_PLACEMENT")
            return
        }
        val updated = state.placedShips + candidate
        val next = GameEngine.fleet.firstOrNull { t -> updated.none { it.type == t } }
        _ui.value = state.copy(placedShips = updated, selectedShip = next, error = null)
    }

    fun randomizeFleet() {
        val state = _ui.value
        val fleet = runCatching { GameEngine.randomFleet(state.noTouching) }.getOrElse {
            _ui.value = state.copy(error = "RANDOMIZE_FAILED"); return
        }
        _ui.value = state.copy(placedShips = fleet, selectedShip = null, error = null)
    }

    fun clearFleet() {
        _ui.value = _ui.value.copy(placedShips = emptyList(), selectedShip = ShipType.CARRIER, isReady = false, error = null)
    }

    fun readyFleet() {
        val state = _ui.value
        if (!GameEngine.validateFleet(state.placedShips, state.noTouching).valid) return
        placementCountdownJob?.cancel()
        placementCountdownJob = null
        _ui.value = state.copy(isReady = true, error = null)
        if (state.isHost) {
            viewModelScope.launch { roomServer?.setHostPlacement(state.placedShips) }
        } else {
            roomClient?.send(MessageType.PLACEMENT_READY, PlacementWire(state.placedShips))
        }
    }

    private fun startPlacementCountdown() {
        placementCountdownJob?.cancel()
        placementCountdownJob = viewModelScope.launch {
            for (remaining in 90 downTo 1) {
                if (_ui.value.screen != AppScreen.PLACEMENT || _ui.value.isReady) return@launch
                _ui.value = _ui.value.copy(placementRemainingSeconds = remaining)
                delay(1_000)
            }
            val current = _ui.value
            if (current.screen == AppScreen.PLACEMENT && !current.isReady) {
                val completed = runCatching { GameEngine.completeFleet(current.placedShips, current.noTouching) }
                    .getOrElse { GameEngine.randomFleet(current.noTouching) }
                _ui.value = current.copy(placedShips = completed, selectedShip = null, placementRemainingSeconds = 0)
                readyFleet()
            }
        }
    }

    fun selectOpponent(id: String) { _ui.value = _ui.value.copy(selectedOpponentId = id, selectedCell = null) }
    fun selectCell(cell: Cell) { _ui.value = _ui.value.copy(selectedCell = cell) }

    fun fireSelected() {
        val state = _ui.value
        val target = state.selectedOpponentId ?: return
        val cell = state.selectedCell ?: return
        if (state.practiceMode) {
            practiceFire(cell)
        } else if (state.isHost) {
            viewModelScope.launch { roomServer?.hostFire(target, cell) }
        } else {
            roomClient?.send(MessageType.FIRE, FireWire(target, cell))
        }
        _ui.value = _ui.value.copy(selectedCell = null)
    }

    fun startPractice() {
        practiceOwnFleet = GameEngine.randomFleet()
        practiceBotFleet = GameEngine.randomFleet()
        practiceOwnShots.clear()
        practiceBotIncoming.clear()
        bot = HuntTargetBot()
        practiceTurnLocal = true
        _ui.value = _ui.value.copy(
            screen = AppScreen.BATTLE,
            practiceMode = true,
            isHost = false,
            myPlayerId = "local",
            players = listOf(
                PlayerSummary("local", _ui.value.playerName.ifBlank { "Player" }, 0xFF35D6C5.toInt(), true, true, true),
                PlayerSummary("bot", getApplication<Application>().getString(R.string.bot), 0xFFFFB454.toInt(), true, true, true)
            ),
            placedShips = practiceOwnFleet,
            selectedOpponentId = "bot",
            currentTurnId = "local",
            winnerId = null,
            shotMarks = emptyMap(), incomingMarks = emptyMap(), sunkOutlines = emptyMap(),
            events = emptyList(), shots = 0, hits = 0, shipsSunk = 0, error = null
        )
    }

    private fun practiceFire(cell: Cell) {
        if (!practiceTurnLocal || cell in practiceOwnShots) return
        val result = GameEngine.resolveShot(practiceBotFleet, practiceOwnShots, cell)
        practiceOwnShots += cell
        val marks = _ui.value.shotMarks.toMutableMap()
        val opponentMarks = marks["bot"].orEmpty().toMutableMap()
        opponentMarks[cell] = result.kind
        marks["bot"] = opponentMarks
        val outlines = _ui.value.sunkOutlines.toMutableMap()
        result.sunkShip?.let { outlines["bot"] = outlines["bot"].orEmpty() + it.cells }
        val updatedEvents = listOf(formatFireEvent("local", "bot", cell, result.kind)) + _ui.value.events
        _ui.value = _ui.value.copy(
            shotMarks = marks,
            sunkOutlines = outlines,
            events = updatedEvents.take(20),
            shots = _ui.value.shots + 1,
            hits = _ui.value.hits + if (result.kind == ShotKind.MISS) 0 else 1,
            shipsSunk = _ui.value.shipsSunk + if (result.kind == ShotKind.SUNK) 1 else 0
        )
        playFeedback(result.kind)
        if (GameEngine.isFleetSunk(practiceBotFleet, practiceOwnShots)) {
            _ui.value = _ui.value.copy(screen = AppScreen.GAME_OVER, winnerId = "local", currentTurnId = null)
            return
        }
        if (result.kind == ShotKind.MISS || !_ui.value.extraTurnOnHit) {
            practiceTurnLocal = false
            _ui.value = _ui.value.copy(currentTurnId = "bot")
            viewModelScope.launch { delay(700); botTurn() }
        }
    }

    private fun botTurn() {
        if (_ui.value.screen != AppScreen.BATTLE || !_ui.value.practiceMode) return
        val target = bot.nextShot()
        val resolution = GameEngine.resolveShot(practiceOwnFleet, practiceBotIncoming, target)
        practiceBotIncoming += target
        bot.recordShot(target, resolution.kind)
        val incoming = _ui.value.incomingMarks.toMutableMap().apply { put(target, resolution.kind) }
        val eventList = listOf(formatFireEvent("bot", "local", target, resolution.kind)) + _ui.value.events
        _ui.value = _ui.value.copy(incomingMarks = incoming, events = eventList.take(20))
        playFeedback(resolution.kind)
        if (GameEngine.isFleetSunk(practiceOwnFleet, practiceBotIncoming)) {
            _ui.value = _ui.value.copy(screen = AppScreen.GAME_OVER, winnerId = "bot", currentTurnId = null)
        } else if (resolution.kind == ShotKind.MISS || !_ui.value.extraTurnOnHit) {
            practiceTurnLocal = true
            _ui.value = _ui.value.copy(currentTurnId = "local")
        } else {
            viewModelScope.launch { delay(550); botTurn() }
        }
    }

    fun rematch() {
        if (_ui.value.practiceMode) startPractice()
        else if (_ui.value.isHost) viewModelScope.launch { roomServer?.hostRematch() }
        else roomClient?.send(PacketCompat.rematch())
    }

    fun mainMenu() {
        viewModelScope.launch {
            closeRoomOnly()
            discovery.close()
            _ui.value = ShipsUiState(settings = _ui.value.settings, hostIp = NetworkUtils.localIpv4().orEmpty())
        }
    }

    private suspend fun closeRoomOnly() {
        hostStateJob?.cancel(); hostStateJob = null
        clientMessagesJob?.cancel(); clientMessagesJob = null
        placementCountdownJob?.cancel(); placementCountdownJob = null
        roomClient?.close(); roomClient = null
        roomServer?.close(); roomServer = null
        discovery.stopAdvertising()
    }

    private fun syncHostState(snapshot: RoomSnapshot) {
        if (snapshot.phase == "LOBBY" && _ui.value.screen == AppScreen.GAME_OVER) {
            lastProcessedFire = null
            _ui.value = _ui.value.copy(shotMarks = emptyMap(), incomingMarks = emptyMap(), sunkOutlines = emptyMap(), events = emptyList(), shots = 0, hits = 0, shipsSunk = 0, winnerId = null, isReady = false)
        }
        if (snapshot.lastFireResult == null && snapshot.phase == "LOBBY") lastProcessedFire = null
        val me = _ui.value.myPlayerId.ifBlank { snapshot.hostId }
        val last = snapshot.lastFireResult
        val marks = _ui.value.shotMarks.toMutableMap()
        val incoming = _ui.value.incomingMarks.toMutableMap()
        val outlines = _ui.value.sunkOutlines.toMutableMap()
        var shots = _ui.value.shots
        var hits = _ui.value.hits
        var sunkCount = _ui.value.shipsSunk
        var events = _ui.value.events
        if (last != null && last != lastProcessedFire) {
            lastProcessedFire = last
            val board = marks[last.targetId].orEmpty().toMutableMap()
            board[last.cell] = last.kind
            marks[last.targetId] = board
            if (last.targetId == me) incoming[last.cell] = last.kind
            if (last.sunkCells.isNotEmpty()) outlines[last.targetId] = outlines[last.targetId].orEmpty() + last.sunkCells
            if (last.attackerId == me) {
                shots++
                if (last.kind != ShotKind.MISS) hits++
                if (last.kind == ShotKind.SUNK) sunkCount++
            }
            events = listOf(formatFireEvent(last.attackerId, last.targetId, last.cell, last.kind, snapshot.players)) + events
        }
        val players = snapshot.players
        _ui.value = _ui.value.copy(
            isHost = true,
            myPlayerId = me,
            players = players,
            currentTurnId = snapshot.currentTurnId,
            remainingMillis = snapshot.remainingMillis,
            roomPaused = snapshot.isPaused,
            shotMarks = marks,
            incomingMarks = incoming,
            sunkOutlines = outlines,
            events = events.take(20),
            shots = shots,
            hits = hits,
            shipsSunk = sunkCount,
            winnerId = snapshot.winnerId,
            screen = when (snapshot.phase) {
                "PLACEMENT" -> if (_ui.value.screen == AppScreen.PLACEMENT) AppScreen.PLACEMENT else AppScreen.PLACEMENT
                "BATTLE" -> AppScreen.BATTLE
                "FINISHED" -> AppScreen.GAME_OVER
                "LOBBY" -> if (_ui.value.screen == AppScreen.GAME_OVER) AppScreen.LOBBY else _ui.value.screen
                else -> _ui.value.screen
            },
            timerSeconds = snapshot.settings.timerSeconds,
            extraTurnOnHit = snapshot.settings.extraTurnOnHit,
            noTouching = snapshot.settings.noTouching,
            maxPlayers = snapshot.settings.maxPlayers,
            selectedOpponentId = _ui.value.selectedOpponentId?.takeIf { id -> players.any { it.id == id && it.alive } }
                ?: players.firstOrNull { it.id != me && it.alive }?.id
        )
    }

    private fun handleClientPacket(packet: com.friends.ships.network.Packet) {
        when (packet.type) {
            MessageType.JOIN_ACCEPTED -> {
                val accepted = WireJson.decode<com.friends.ships.network.JoinAcceptedWire>(packet)
                _ui.value = _ui.value.copy(myPlayerId = accepted.playerId)
                applyLobby(accepted.lobby)
            }
            MessageType.LOBBY_UPDATE -> applyLobby(WireJson.decode<LobbyWire>(packet))
            MessageType.SETTINGS_UPDATE -> {
                val settings = WireJson.decode<GameSettingsWire>(packet)
                _ui.value = _ui.value.copy(timerSeconds = settings.timerSeconds, extraTurnOnHit = settings.extraTurnOnHit, noTouching = settings.noTouching, maxPlayers = settings.maxPlayers)
            }
            MessageType.START_PLACEMENT -> {
                _ui.value = _ui.value.copy(screen = AppScreen.PLACEMENT, placedShips = emptyList(), selectedShip = ShipType.CARRIER, isReady = false, placementRemainingSeconds = 90)
                startPlacementCountdown()
            }
            MessageType.ALL_READY -> {
                placementCountdownJob?.cancel(); placementCountdownJob = null
                _ui.value = _ui.value.copy(screen = AppScreen.BATTLE, currentTurnId = null, roomPaused = false)
            }
            MessageType.TURN_UPDATE, MessageType.YOUR_TURN -> {
                val turn = WireJson.decode<TurnWire>(packet)
                _ui.value = _ui.value.copy(currentTurnId = turn.playerId, remainingMillis = turn.remainingMillis, roomPaused = turn.isPaused)
            }
            MessageType.FIRE_RESULT -> processFireResult(WireJson.decode<FireResultWire>(packet))
            MessageType.PLAYER_ELIMINATED, MessageType.PLAYER_LEFT -> {
                val payload = runCatching { WireJson.decode<com.friends.ships.network.PlayerLeftWire>(packet) }.getOrNull()
                if (payload != null) _ui.value = _ui.value.copy(players = _ui.value.players.map { if (it.id == payload.playerId) it.copy(alive = false, connected = payload.reason != "DISCONNECTED") else it })
            }
            MessageType.GAME_OVER -> {
                val turn = WireJson.decode<TurnWire>(packet)
                _ui.value = _ui.value.copy(screen = AppScreen.GAME_OVER, winnerId = turn.playerId, currentTurnId = null)
            }
            MessageType.REMATCH -> _ui.value = _ui.value.copy(screen = AppScreen.LOBBY, players = _ui.value.players.map { it.copy(ready = false, alive = true) }, placedShips = emptyList(), shotMarks = emptyMap(), incomingMarks = emptyMap(), events = emptyList(), winnerId = null, isReady = false)
            MessageType.ERROR -> attemptReconnect()
        }
    }

    private fun applyLobby(lobby: LobbyWire) {
        val me = _ui.value.myPlayerId
        _ui.value = _ui.value.copy(
            players = lobby.players.map { it.toSummary() },
            timerSeconds = lobby.settings.timerSeconds,
            extraTurnOnHit = lobby.settings.extraTurnOnHit,
            noTouching = lobby.settings.noTouching,
            maxPlayers = lobby.settings.maxPlayers,
            selectedOpponentId = _ui.value.selectedOpponentId?.takeIf { id -> lobby.players.any { it.id == id && it.alive } }
                ?: lobby.players.firstOrNull { it.id != me && it.alive }?.id
        )
    }

    private fun PlayerWire.toSummary() = PlayerSummary(id, name, color, ready, connected, alive)

    private fun processFireResult(result: FireResultWire) {
        val state = _ui.value
        val marks = state.shotMarks.toMutableMap()
        marks[result.targetId] = marks[result.targetId].orEmpty().toMutableMap().apply { put(result.cell, result.kind) }
        val incoming = state.incomingMarks.toMutableMap()
        if (result.targetId == state.myPlayerId) incoming[result.cell] = result.kind
        val outlines = state.sunkOutlines.toMutableMap()
        if (result.sunkCells.isNotEmpty()) outlines[result.targetId] = outlines[result.targetId].orEmpty() + result.sunkCells
        val players = if (result.targetEliminated) state.players.map { if (it.id == result.targetId) it.copy(alive = false) else it } else state.players
        val ownShot = result.attackerId == state.myPlayerId
        _ui.value = state.copy(
            shotMarks = marks,
            incomingMarks = incoming,
            sunkOutlines = outlines,
            players = players,
            currentTurnId = result.nextTurnId,
            winnerId = result.winnerId,
            shots = state.shots + if (ownShot) 1 else 0,
            hits = state.hits + if (ownShot && result.kind != ShotKind.MISS) 1 else 0,
            shipsSunk = state.shipsSunk + if (ownShot && result.kind == ShotKind.SUNK) 1 else 0,
            screen = if (result.winnerId != null) AppScreen.GAME_OVER else AppScreen.BATTLE,
            events = listOf(formatFireEvent(result.attackerId, result.targetId, result.cell, result.kind, state.players)) + state.events
        )
        if (result.targetId == state.myPlayerId) playFeedback(result.kind)
        else if (result.attackerId == state.myPlayerId) playFeedback(result.kind)
    }


    private fun attemptReconnect() {
        if (isReconnecting) return
        if (_ui.value.isHost || _ui.value.myPlayerId.isBlank()) {
            if (!_ui.value.isHost) _ui.value = _ui.value.copy(error = "HOST_DISCONNECTED", screen = AppScreen.HOME)
            return
        }
        isReconnecting = true
        viewModelScope.launch {
            val host = _ui.value.hostIp.ifBlank { _ui.value.hostAddress.substringBeforeLast(":", "") }
            val port = _ui.value.port.toIntOrNull() ?: _ui.value.hostAddress.substringAfterLast(":", "8765").toIntOrNull() ?: 8765
            val reconnectId = _ui.value.myPlayerId
            var recovered = false
            for (attempt in 0 until 5) {
                if (recovered) break
                delay(1_500)
                try {
                    clientMessagesJob?.cancel()
                    roomClient?.close()
                    val replacement = RoomClient()
                    roomClient = replacement
                    clientMessagesJob = viewModelScope.launch { replacement.messages.collectLatest(::handleClientPacket) }
                    val accepted = replacement.connect(host, port, _ui.value.playerName, reconnectId)
                    applyLobby(accepted.lobby)
                    _ui.value = _ui.value.copy(error = null)
                    recovered = true
                } catch (_: Exception) {
                    roomClient?.close()
                    roomClient = null
                }
            }
            isReconnecting = false
            if (!recovered) {
                clientMessagesJob?.cancel()
                roomClient?.close(); roomClient = null
                _ui.value = _ui.value.copy(screen = AppScreen.HOME, error = "HOST_DISCONNECTED", isHost = false)
            }
        }
    }

    private fun formatFireEvent(
        attackerId: String,
        targetId: String,
        cell: Cell,
        kind: ShotKind,
        players: List<PlayerSummary> = _ui.value.players
    ): String {
        val context = getApplication<Application>()
        val you = context.getString(R.string.event_you)
        val botName = context.getString(R.string.event_bot)
        val attacker = when (attackerId) {
            "local" -> you
            "bot" -> botName
            else -> players.firstOrNull { it.id == attackerId }?.name ?: context.getString(R.string.player_name)
        }
        val target = when (targetId) {
            "local" -> you
            "bot" -> botName
            else -> players.firstOrNull { it.id == targetId }?.name ?: context.getString(R.string.player_name)
        }
        val result = context.getString(when (kind) {
            ShotKind.MISS -> R.string.miss
            ShotKind.HIT -> R.string.hit
            ShotKind.SUNK -> R.string.sunk
        })
        return context.getString(R.string.event_shot, attacker, target, cell.toString(), result)
    }

    private fun playFeedback(kind: ShotKind) {
        val settings = _ui.value.settings
        val context = getApplication<Application>()
        if (settings.soundEnabled) runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            tone.startTone(if (kind == ShotKind.MISS) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK, 130)
            viewModelScope.launch { delay(180); tone.release() }
        }
        if (settings.vibrationEnabled) runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                else vibrator.vibrate(80)
            }
        }
    }

    override fun onCleared() {
        discovery.close()
        roomClient?.close()
        roomServer?.close()
        super.onCleared()
    }
}

private object PacketCompat {
    fun rematch() = com.friends.ships.network.Packet(MessageType.REMATCH)
}
