package com.friends.ships.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friends.ships.R
import com.friends.ships.game.Cell
import com.friends.ships.game.GameEngine
import com.friends.ships.game.ShipPlacement
import com.friends.ships.game.ShipType
import com.friends.ships.game.ShotKind
import com.friends.ships.ui.theme.Amber
import com.friends.ships.ui.theme.Coral
import com.friends.ships.ui.theme.HitColor
import com.friends.ships.ui.theme.MissColor
import com.friends.ships.ui.theme.Navy
import com.friends.ships.ui.theme.SeaCell
import com.friends.ships.ui.theme.SeaCellAlt
import com.friends.ships.ui.theme.ShipColor
import com.friends.ships.ui.theme.SunkColor
import com.friends.ships.ui.theme.Teal
import kotlin.math.roundToInt

@Composable
fun ShipsApp(ui: ShipsUiState, viewModel: ShipsViewModel) {
    LaunchedEffect(ui.screen) {
        if (ui.screen == AppScreen.JOIN) viewModel.beginDiscovery()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (ui.screen != AppScreen.HOME) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.open(AppScreen.HOME) }) { Text(stringResource(R.string.back)) }
                    Spacer(Modifier.weight(1f))
                    Text("SHIPS", color = Teal, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                }
            }
            ErrorMessage(ui.error)
            when (ui.screen) {
                AppScreen.HOME -> HomeScreen(viewModel)
                AppScreen.CREATE -> CreateRoomScreen(ui, viewModel)
                AppScreen.JOIN -> JoinRoomScreen(ui, viewModel)
                AppScreen.LOBBY -> LobbyScreen(ui, viewModel)
                AppScreen.PLACEMENT -> PlacementScreen(ui, viewModel)
                AppScreen.BATTLE -> BattleScreen(ui, viewModel)
                AppScreen.GAME_OVER -> GameOverScreen(ui, viewModel)
                AppScreen.SETTINGS -> SettingsScreen(ui, viewModel)
                AppScreen.HELP -> HelpScreen(viewModel)
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: ShipsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 42.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Box(
            modifier = Modifier.size(116.dp).clip(RoundedCornerShape(32.dp)).background(Navy),
            contentAlignment = Alignment.Center
        ) {
            Text("⚓", fontSize = 56.sp)
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(18.dp))
        PrimaryMenuButton(stringResource(R.string.create_room)) { viewModel.open(AppScreen.CREATE) }
        PrimaryMenuButton(stringResource(R.string.join_room)) { viewModel.open(AppScreen.JOIN) }
        PrimaryMenuButton(stringResource(R.string.practice)) { viewModel.startPractice() }
        OutlinedButton(onClick = { viewModel.open(AppScreen.HELP) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.how_to_play))
        }
        OutlinedButton(onClick = { viewModel.open(AppScreen.SETTINGS) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.settings))
        }
        Text(stringResource(R.string.local_multiplayer), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun PrimaryMenuButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Navy), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun CreateRoomScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ScreenTitle(stringResource(R.string.create_room))
        OutlinedTextField(value = ui.playerName, onValueChange = viewModel::setPlayerName, label = { Text(stringResource(R.string.player_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.port, onValueChange = viewModel::setPort, label = { Text(stringResource(R.string.port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(R.string.timer), fontWeight = FontWeight.SemiBold)
        TimerPicker(ui.timerSeconds, viewModel::setTimer)
        SettingSwitch(stringResource(R.string.extra_turn), ui.extraTurnOnHit, viewModel::setExtraTurn)
        SettingSwitch(stringResource(R.string.no_touching), ui.noTouching, viewModel::setNoTouching)
        Text("${stringResource(R.string.max_players)}: ${ui.maxPlayers}", fontWeight = FontWeight.SemiBold)
        Slider(value = ui.maxPlayers.toFloat(), onValueChange = { viewModel.setMaxPlayers(it.roundToInt()) }, valueRange = 2f..6f, steps = 3)
        Button(onClick = viewModel::createRoom, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.create_room)) }
    }
}

@Composable
private fun JoinRoomScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(stringResource(R.string.join_room))
        OutlinedTextField(value = ui.playerName, onValueChange = viewModel::setPlayerName, label = { Text(stringResource(R.string.player_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(if (ui.discoveredRooms.isEmpty()) R.string.no_rooms else R.string.available_rooms), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall)
        if (ui.discoveredRooms.isEmpty()) {
            Text(stringResource(R.string.no_wifi), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        } else {
            ui.discoveredRooms.forEach { room ->
                Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.joinDiscovered(room) }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(room.name, fontWeight = FontWeight.Bold)
                            Text("${room.host}:${room.port}", color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("›", fontSize = 28.sp, color = Teal)
                    }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.enter_ip), fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = ui.hostIp, onValueChange = viewModel::setHostIp, label = { Text(stringResource(R.string.host_ip)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.port, onValueChange = viewModel::setPort, label = { Text(stringResource(R.string.port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { viewModel.joinRoom() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.connect)) }
    }
}

@Composable
private fun LobbyScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(stringResource(R.string.players))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.host_ip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(ui.hostAddress.ifBlank { "—" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.room_settings), fontWeight = FontWeight.SemiBold)
                Text("${timerLabel(ui.timerSeconds)}  •  ${if (ui.extraTurnOnHit) stringResource(R.string.extra_turn_short) else stringResource(R.string.standard_turn)}  •  ${if (ui.noTouching) stringResource(R.string.no_touching) else stringResource(R.string.touching_allowed)}  •  ${ui.maxPlayers}")
            }
        }
        Text("${stringResource(R.string.players)} (${ui.players.size}/${ui.maxPlayers})", fontWeight = FontWeight.SemiBold)
        ui.players.forEach { player -> PlayerRow(player, ui.myPlayerId) }
        if (ui.isHost) {
            Button(onClick = viewModel::startPlacement, enabled = ui.players.size >= 2, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.start)) }
            if (ui.players.size < 2) Text(stringResource(R.string.not_enough_players), color = MaterialTheme.colorScheme.secondary)
        } else {
            Text(stringResource(R.string.lobby_wait), color = MaterialTheme.colorScheme.secondary)
        }
        OutlinedButton(onClick = viewModel::mainMenu, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.main_menu)) }
    }
}

@Composable
private fun PlayerRow(player: com.friends.ships.network.PlayerSummary, myId: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(player.color)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name + if (player.id == myId) " ${stringResource(R.string.you)}" else "", fontWeight = FontWeight.SemiBold)
                Text(if (!player.connected) stringResource(R.string.host_disconnected) else if (player.ready) stringResource(R.string.ready) else stringResource(R.string.not_ready), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            if (!player.alive) Text("×", color = Coral, fontSize = 22.sp)
        }
    }
}

@Composable
private fun PlacementScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle(stringResource(R.string.place_fleet))
        Text(stringResource(R.string.select_ship), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.placement_countdown, ui.placementRemainingSeconds), color = Amber, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            GameEngine.fleet.forEach { type ->
                val placed = ui.placedShips.any { it.type == type }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = ui.selectedShip == type,
                        onClick = { viewModel.selectShip(type) },
                        enabled = !placed,
                        label = { Text(shipName(type)) }
                    )
                    if (placed) Text("✓", color = Teal, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::rotate, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rotate)) }
            OutlinedButton(onClick = viewModel::randomizeFleet, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.randomize)) }
            OutlinedButton(onClick = viewModel::clearFleet, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear)) }
        }
        BoardGrid(
            ships = ui.placedShips,
            marks = emptyMap(),
            sunkOutline = emptySet(),
            clickable = ui.selectedShip != null && !ui.isReady,
            onCellTap = viewModel::placeShip,
            compact = false
        )
        if (ui.isReady) Text(stringResource(R.string.ready_status), color = Teal, fontWeight = FontWeight.Bold)
        Button(onClick = viewModel::readyFleet, enabled = !ui.isReady && GameEngine.validateFleet(ui.placedShips, ui.noTouching).valid, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(stringResource(R.string.ready))
        }
    }
}

@Composable
private fun BattleScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    val opponent = ui.players.firstOrNull { it.id == ui.selectedOpponentId }
    val myTurn = ui.currentTurnId == ui.myPlayerId || (ui.practiceMode && ui.currentTurnId == "local")
    val selectedMarks = ui.selectedOpponentId?.let { ui.shotMarks[it] }.orEmpty()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (ui.roomPaused) stringResource(R.string.player_reconnecting) else if (myTurn) stringResource(R.string.your_turn) else stringResource(R.string.waiting_turn), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(opponent?.name ?: stringResource(R.string.choose_opponent), color = MaterialTheme.colorScheme.secondary)
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (ui.timerSeconds == 0 || ui.remainingMillis == 0L) 1f else (ui.remainingMillis / (ui.timerSeconds * 1000f)).coerceIn(0f, 1f) }, color = Teal, trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(54.dp), strokeWidth = 5.dp)
                Text(if (ui.remainingMillis > 0) "${(ui.remainingMillis / 1000).coerceAtLeast(0)}" else "∞", fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            ui.players.filter { it.id != ui.myPlayerId && it.alive }.forEach { player ->
                FilterChip(selected = player.id == ui.selectedOpponentId, onClick = { viewModel.selectOpponent(player.id) }, label = { Text(player.name, maxLines = 1) })
            }
        }
        Text(stringResource(R.string.opponent_board), fontWeight = FontWeight.SemiBold)
        BoardGrid(
            ships = emptyList(), marks = selectedMarks,
            sunkOutline = ui.selectedOpponentId?.let { ui.sunkOutlines[it] }.orEmpty(),
            clickable = myTurn && opponent?.alive == true,
            onCellTap = viewModel::selectCell,
            selectedCell = ui.selectedCell,
            compact = false
        )
        if (ui.selectedCell != null) Text("${stringResource(R.string.select_cell)}: ${ui.selectedCell}", color = Teal)
        Button(onClick = viewModel::fireSelected, enabled = myTurn && ui.selectedCell != null && opponent?.alive == true && ui.selectedCell?.let { selectedMarks[it] } == null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.fire))
        }
        HorizontalDivider()
        Text(stringResource(R.string.own_fleet), fontWeight = FontWeight.SemiBold)
        BoardGrid(ships = ui.placedShips, marks = ui.incomingMarks, sunkOutline = ui.sunkOutlines[ui.myPlayerId].orEmpty(), clickable = false, onCellTap = {}, compact = true)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.event_log), fontWeight = FontWeight.SemiBold)
                if (ui.events.isEmpty()) Text("—", color = MaterialTheme.colorScheme.secondary)
                ui.events.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }
}

@Composable
private fun BoardGrid(
    ships: List<ShipPlacement>,
    marks: Map<Cell, ShotKind>,
    sunkOutline: Set<Cell>,
    clickable: Boolean,
    onCellTap: (Cell) -> Unit,
    selectedCell: Cell? = null,
    compact: Boolean
) {
    val shipCells = ships.flatMap { it.cells }.toSet()
    val labelSize = if (compact) 8.sp else 10.sp
    val innerPadding = if (compact) 0.5.dp else 1.dp
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Navy).padding(if (compact) 4.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) 17.dp else 21.dp))
            for (col in 0..9) Text(('A'.code + col).toChar().toString(), color = Color.White, fontSize = labelSize, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        for (row in 0..9) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((row + 1).toString(), color = Color.White, fontSize = labelSize, textAlign = TextAlign.Center, modifier = Modifier.width(if (compact) 17.dp else 21.dp))
                for (col in 0..9) {
                    val cell = Cell(row, col)
                    val mark = marks[cell]
                    val isSunk = cell in sunkOutline
                    val isShip = cell in shipCells
                    val color = when {
                        isSunk -> SunkColor
                        mark == ShotKind.HIT -> HitColor
                        mark == ShotKind.SUNK -> SunkColor
                        mark == ShotKind.MISS -> SeaCellAlt
                        isShip -> ShipColor
                        (row + col) % 2 == 0 -> SeaCell
                        else -> SeaCellAlt
                    }
                    val animatedCellColor by animateColorAsState(if (selectedCell == cell) Amber else color, label = "cell-color")
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(innerPadding)
                            .clip(RoundedCornerShape(if (compact) 2.dp else 4.dp))
                            .background(animatedCellColor)
                            .border(if (selectedCell == cell) 2.dp else 0.dp, if (selectedCell == cell) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable(enabled = clickable && mark == null) { onCellTap(cell) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            mark == ShotKind.MISS -> Text("•", color = MissColor, fontSize = if (compact) 13.sp else 17.sp)
                            mark == ShotKind.HIT || mark == ShotKind.SUNK || isSunk -> Text("×", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (compact) 10.sp else 13.sp)
                            isShip -> Box(Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(2.dp)).background(Teal.copy(alpha = 0.55f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameOverScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    val winnerName = ui.players.firstOrNull { it.id == ui.winnerId }?.name ?: if (ui.winnerId == "local") ui.playerName else if (ui.winnerId == "bot") stringResource(R.string.bot) else "—"
    val accuracy = if (ui.shots == 0) 0 else (ui.hits * 100 / ui.shots)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Text("🏆", fontSize = 68.sp)
        Text(stringResource(R.string.winner), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(winnerName, style = MaterialTheme.typography.displaySmall, color = Teal, fontWeight = FontWeight.Black)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                StatLine(stringResource(R.string.shots), ui.shots.toString())
                StatLine(stringResource(R.string.hits), ui.hits.toString())
                StatLine(stringResource(R.string.accuracy), "$accuracy%")
                StatLine(stringResource(R.string.ships_sunk), ui.shipsSunk.toString())
            }
        }
        if (ui.isHost || ui.practiceMode) {
            Button(onClick = viewModel::rematch, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.rematch)) }
        }
        OutlinedButton(onClick = viewModel::mainMenu, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.main_menu)) }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(ui: ShipsUiState, viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(stringResource(R.string.settings))
        SettingSwitch(stringResource(R.string.sound), ui.settings.soundEnabled, viewModel::setSound)
        SettingSwitch(stringResource(R.string.vibration), ui.settings.vibrationEnabled, viewModel::setVibration)
        Text(stringResource(R.string.theme), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(ui.settings.theme == "system", { viewModel.setTheme("system") }, label = { Text(stringResource(R.string.theme_system)) })
            FilterChip(ui.settings.theme == "light", { viewModel.setTheme("light") }, label = { Text(stringResource(R.string.theme_light)) })
            FilterChip(ui.settings.theme == "dark", { viewModel.setTheme("dark") }, label = { Text(stringResource(R.string.theme_dark)) })
        }
        Text(stringResource(R.string.language), fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(ui.settings.language == "uz", { viewModel.setLanguage("uz") }, label = { Text(stringResource(R.string.language_uz)) })
            FilterChip(ui.settings.language == "ru", { viewModel.setLanguage("ru") }, label = { Text(stringResource(R.string.language_ru)) })
            FilterChip(ui.settings.language == "en", { viewModel.setLanguage("en") }, label = { Text(stringResource(R.string.language_en)) })
        }
    }
}

@Composable
private fun HelpScreen(viewModel: ShipsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(stringResource(R.string.how_to_play))
        Text(stringResource(R.string.rules_text), style = MaterialTheme.typography.bodyLarge)
        Card {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fleet_carrier))
                Text(stringResource(R.string.fleet_battleship))
                Text(stringResource(R.string.fleet_cruiser))
                Text(stringResource(R.string.fleet_submarine))
                Text(stringResource(R.string.fleet_destroyer))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(MissColor, CircleShape)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.miss)) }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(HitColor, CircleShape)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.hit)) }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(SunkColor, CircleShape)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sunk)) }
        OutlinedButton(onClick = { viewModel.open(AppScreen.HOME) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.main_menu)) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimerPicker(value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf(0, 15), listOf(30, 60)).forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowValues.forEach { seconds ->
                    FilterChip(selected = value == seconds, onClick = { onChange(seconds) }, label = {
                        Text(when (seconds) {
                            0 -> stringResource(R.string.timer_off)
                            15 -> stringResource(R.string.seconds_15)
                            30 -> stringResource(R.string.seconds_30)
                            else -> stringResource(R.string.seconds_60)
                        })
                    })
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun ErrorMessage(error: String?) {
    if (error.isNullOrBlank()) return
    val message = when (error) {
        "PORT_IN_USE" -> stringResource(R.string.port_in_use)
        "ROOM_FULL" -> stringResource(R.string.room_full)
        "NAME_TAKEN" -> stringResource(R.string.name_taken)
        "GAME_STARTED" -> stringResource(R.string.game_started)
        "NOT_ENOUGH_PLAYERS" -> stringResource(R.string.not_enough_players)
        "CONNECT_FAILED", "HOST_START_FAILED" -> stringResource(R.string.no_wifi)
        "HOST_DISCONNECTED" -> stringResource(R.string.host_disconnected)
        else -> stringResource(R.string.unknown_error)
    }
    Text(message, color = Coral, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun shipName(type: ShipType): String = when (type) {
    ShipType.CARRIER -> stringResource(R.string.fleet_carrier)
    ShipType.BATTLESHIP -> stringResource(R.string.fleet_battleship)
    ShipType.CRUISER -> stringResource(R.string.fleet_cruiser)
    ShipType.SUBMARINE -> stringResource(R.string.fleet_submarine)
    ShipType.DESTROYER -> stringResource(R.string.fleet_destroyer)
}

@Composable
private fun timerLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.timer_off)
    15 -> stringResource(R.string.seconds_15)
    30 -> stringResource(R.string.seconds_30)
    else -> stringResource(R.string.seconds_60)
}
