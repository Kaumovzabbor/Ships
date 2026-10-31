package com.friends.ships.network

import com.friends.ships.game.Cell
import com.friends.ships.game.ShipPlacement
import com.friends.ships.game.ShipType
import com.friends.ships.game.ShotKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

object MessageType {
    const val JOIN = "JOIN"
    const val JOIN_ACCEPTED = "JOIN_ACCEPTED"
    const val JOIN_REJECTED = "JOIN_REJECTED"
    const val LOBBY_UPDATE = "LOBBY_UPDATE"
    const val SETTINGS_UPDATE = "SETTINGS_UPDATE"
    const val START_PLACEMENT = "START_PLACEMENT"
    const val PLACEMENT_READY = "PLACEMENT_READY"
    const val ALL_READY = "ALL_READY"
    const val YOUR_TURN = "YOUR_TURN"
    const val FIRE = "FIRE"
    const val FIRE_RESULT = "FIRE_RESULT"
    const val PLAYER_ELIMINATED = "PLAYER_ELIMINATED"
    const val GAME_OVER = "GAME_OVER"
    const val REMATCH = "REMATCH"
    const val PLAYER_LEFT = "PLAYER_LEFT"
    const val PING = "PING"
    const val PONG = "PONG"
    const val ERROR = "ERROR"
    const val TURN_UPDATE = "TURN_UPDATE"
}

@Serializable
data class Packet(val type: String, val payload: JsonObject = buildJsonObject { })

object WireJson {
    @OptIn(ExperimentalSerializationApi::class)
    val instance = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    inline fun <reified T> packet(type: String, payload: T): Packet =
        Packet(type, instance.encodeToJsonElement(payload).jsonObject)

    inline fun <reified T> decode(packet: Packet): T = instance.decodeFromJsonElement(packet.payload)
}

@Serializable
data class GameSettingsWire(
    val timerSeconds: Int = 30,
    val extraTurnOnHit: Boolean = true,
    val noTouching: Boolean = false,
    val maxPlayers: Int = 6,
    val placementSeconds: Int = 90
)

@Serializable
data class JoinWire(val name: String, val reconnectId: String? = null)

@Serializable
data class PlayerWire(
    val id: String,
    val name: String,
    val color: Int,
    val ready: Boolean,
    val connected: Boolean = true,
    val alive: Boolean = true
)

@Serializable
data class LobbyWire(
    val players: List<PlayerWire>,
    val settings: GameSettingsWire,
    val phase: String,
    val hostId: String
)

@Serializable
data class JoinAcceptedWire(val playerId: String, val lobby: LobbyWire)

@Serializable
data class JoinRejectedWire(val reason: String)

@Serializable
data class PlacementWire(val ships: List<ShipPlacement>)

@Serializable
data class FireWire(val targetId: String, val cell: Cell)

@Serializable
data class FireResultWire(
    val attackerId: String,
    val targetId: String,
    val cell: Cell,
    val kind: ShotKind,
    val sunkType: ShipType? = null,
    val sunkCells: List<Cell> = emptyList(),
    val targetEliminated: Boolean = false,
    val nextTurnId: String? = null,
    val winnerId: String? = null
)

@Serializable
data class TurnWire(val playerId: String?, val remainingMillis: Long, val aliveIds: List<String>, val isPaused: Boolean = false)

@Serializable
data class ErrorWire(val message: String)

@Serializable
data class PlayerLeftWire(val playerId: String, val reason: String)
