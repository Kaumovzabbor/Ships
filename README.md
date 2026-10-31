# Ships — Android local-network Battleship

Ships is a Kotlin/Jetpack Compose Android implementation of the classic 10×10 Battleship game. The Uzbek (Latin) resources are the default; Russian and English translations are included. The project targets Android 14 (API 34), supports API 24+, and uses a phone as the authoritative TCP room host. No cloud service, account, ads, telemetry, or Internet gameplay backend is used.

## Build

Requirements: JDK 17 and Android SDK Platform 35 + Build Tools installed. Android Studio Ladybug or newer is recommended.

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The release variant is unsigned; configure signing in `app/build.gradle.kts` before distributing a release APK. The included `gradlew`/`gradlew.bat` are lightweight bootstrap scripts that download and run Gradle 8.9 from the official distribution URL. They are not the canonical Gradle Wrapper JAR distribution; the first build requires `curl` or `wget` (or PowerShell on Windows), `unzip` on Linux/macOS, and access to the Gradle/Maven repositories.

## GitHub Actions APK

`.github/workflows/build-apk.yml` runs on every push and on `workflow_dispatch`. It installs Temurin JDK 17, builds `assembleDebug`, and uploads `Ships-debug-apk` for 14 days. Push this folder to a GitHub repository, open **Actions → Build Ships APK**, then download the artifact from the completed run.

## Architecture

- `game/`: Android-independent Battleship rules, 10×10 coordinate model, ship fleet validation, randomized placement, shot resolution, elimination/turn helpers, and the Hunt/Target practice bot.
- `network/`: newline-delimited JSON protocol, coroutine TCP client, authoritative TCP host, heartbeat, 30-second server-side reconnect grace, and Android NSD/mDNS discovery (`_ships._tcp.`).
- `data/`: DataStore-backed sound, vibration, theme, and language preferences.
- `ui/`: single-activity Compose UI and a StateFlow-backed `AndroidViewModel`.
- `util/`: LAN IPv4 address detection.

The server stores every fleet privately. A joining client sends only its own placement to the host. Clients receive lobby metadata and public shot results; a ship's complete cell outline is transmitted only when that ship is sunk. The server validates turn ownership, target status, board bounds, and repeated shots. The first turn is randomized; subsequent turns follow join order while skipping eliminated players.

## Message protocol

Every TCP line is one UTF-8 JSON object with a `type` field and optional `payload`. The protocol is intentionally version-light; unknown JSON fields are ignored.

| Type | Direction | Purpose |
|---|---|---|
| `JOIN` | client → host | Player name and optional reconnect player ID |
| `JOIN_ACCEPTED` / `JOIN_REJECTED` | host → client | Assigned player ID and public lobby, or rejection reason |
| `LOBBY_UPDATE` | host → all | Public player list and room settings |
| `SETTINGS_UPDATE` | host → all | Updated game settings |
| `START_PLACEMENT` | host → all | Enter fleet placement |
| `PLACEMENT_READY` | client → host | Submit that client's five-ship fleet |
| `ALL_READY` | host → all | All fleets are ready; battle begins |
| `YOUR_TURN` / `TURN_UPDATE` | host → client/all | Current player and remaining turn time |
| `FIRE` | client → host | Target player and one board cell |
| `FIRE_RESULT` | host → all | MISS/HIT/SUNK, optional sunk-ship outline, next turn |
| `PLAYER_ELIMINATED` / `PLAYER_LEFT` | host → all | Elimination or disconnect status |
| `GAME_OVER` | host → all | Winner player ID |
| `REMATCH` | host → all | Reset the same room for a new fleet setup |
| `PING` / `PONG` | both directions | Heartbeat traffic |

## Play on a local network

1. To publish the source, create an empty GitHub repository named `Ships`, then from this folder run `git init -b main`, `git add .`, `git commit -m "Initial Ships Android app"`, `git remote add origin https://github.com/USERNAME/Ships.git`, and `git push -u origin main` (replace `USERNAME` with your GitHub login).
2. In GitHub, open **Actions → Build Ships APK**. The workflow runs on the push; you can also use **Run workflow**.
3. Download the `Ships-debug-apk` artifact from the completed workflow run, unzip it, and install the APK on two or more Android phones. Allow the browser/file manager to **Install unknown apps** when Android asks.
4. Connect every phone to the same Wi-Fi network or one phone's hotspot. Internet access is not required.
5. On one phone, choose **Create room** and leave the port at `8765` unless it is occupied. The lobby displays the host LAN IP and port.
6. On the other phone(s), choose **Join room** and tap the discovered host. If NSD discovery is blocked by the router, enter the host IP and port manually.
7. The host starts placement after at least two players have joined. Place every ship, then tap Ready. Select a living opponent, tap a cell, and confirm with Fire.

## Current implementation notes

This is an implementation baseline with a pure-Kotlin rules engine and JUnit tests; full Android compilation and device-level multiplayer testing still need to be run in an Android SDK environment. The game rules and authoritative multiplayer shot path are implemented, but several requested UX/operations details remain follow-up work:

- Placement uses tap-to-place plus rotation, Randomize, Clear, Ready, and a 90-second countdown that completes a partial fleet while preserving valid ships already placed. Drag-and-drop previews are not included.
- The battle board has immediate result markers and sunk outlines; bespoke splash/ripple/sinking motion effects are not yet included.
- The server retains a disconnected player for 30 seconds and accepts a reconnect JOIN carrying the same player ID. A live client attempts reconnect automatically while its process is still alive. Recreating a full local session after Android process death is not supported.
- The room runs in the app process with ViewModel-scoped coroutines and sockets, not a persistent foreground service. Android may suspend or kill a background process; reconnect grace handles transient drops while the process remains available.
- Multiplayer game-over stats are currently per-device shots/hits rather than a full cross-player statistics table. Practice mode includes the local player's shots/hits/accuracy and ships sunk.
- Network messages are plain TCP JSON on the trusted LAN (no TLS or cryptographic identity). The host is authoritative for rules, but this is not designed for hostile networks.

The unit tests cover legal/illegal placement, random fleet generation, MISS/HIT/SUNK resolution, duplicate shot rejection, turn skipping, winner detection, and bot target selection.
