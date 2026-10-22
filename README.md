# ⚡ eSpoty — Full Spotify Integration for Minecraft (Fabric 1.21.x – 1.21.11)

![eSpoty Mod](src/main/resources/assets/espoty/icon.png)

A high-performance, modern Spotify integration mod for **Fabric 1.21 through 1.21.11**. Featuring an ultra-sleek **Gold & Yellow** glassmorphic HUD, real-time synchronized karaoke lyrics, full playback controls, queue management, and automatic local PC Spotify process detection.

---

## ✨ Features

- 🟢 **Zero Login Required / Direct PC Link**: Automatically connects directly to your running Spotify app on Windows via Windows SMTC and local TCP socket. Zero developer app setup, zero client IDs or secrets, and zero browser login required!
- 🎧 **Interactive In-Game Hub (Screen)**:
  - **Now Playing**: High-resolution album artwork (with iTunes 600x600 HD art fetching), track title, artists, album, and liked songs heart button (💛/🤍).
  - **Full Playback Controls**: Play/Pause, Next (⏭), Previous (⏮), Shuffle toggle (🔀), and Repeat cycle (🔁 Off / All / One).
  - **Dynamic State Disabling**: Buttons automatically disable and dim if Spotify marks them as unavailable (e.g. shuffle or repeat restrictions).
  - **Interactive Seek Bar**: Click or drag the progress scrubber to jump to any point in the track.
  - **Volume Slider**: Interactive real-time audio master volume slider (0% - 100%) with quick mute/unmute toggle.
- 💛 **Liked Songs (Favorites)**:
  - Favorite any track with one click using the Heart button.
  - Liked tracks show a radiant Gold heart (`❤`) both in the Hub and directly on the in-game HUD!
- 📜 **Real-Time Synchronized Lyrics**:
  - Live karaoke-style synchronized lyrics powered by LRCLib database.
  - Accurate sub-frame alignment with zero delay or freezing.
  - Active line highlighted in glowing Gold (`#FFD700`).
  - Synced HUD lyrics: floating pill below the progress bar in-game!
- 📋 **Live Queue Viewer**:
  - Displays the current song and all upcoming tracks in your Spotify playback queue with song titles, artists, and durations.
- 🎨 **Modern Gold & Yellow HUD Overlay**:
  - Glassmorphic dark card (`#121217`) with vibrant Gold (`#FFD700`) and Yellow (`#FFF176`) borders.
  - Smooth Marquee auto-scroller for long track titles.
  - Dynamic client-interpolated progress bar running at 60+ FPS.
  - Configurable positions: `Top Right`, `Top Left`, `Bottom Right`, `Bottom Left`, or `Disabled`.
  - Compact mode and Auto-Hide support.
- ⚡ **High Performance & Security**:
  - **Zero Frame Drops**: Non-blocking asynchronous HTTP/2 client (`java.net.http.HttpClient`) and background scheduling. Never blocks the Minecraft render thread!
  - **Client-Side Progress Prediction**: Calculates interpolated track progress locally between polling intervals, saving network bandwidth and eliminating API rate-limiting while providing 60 FPS smoothness.
  - **RFC 7636 PKCE OAuth 2.0 Flow**: Industry-standard Proof Key for Code Exchange. No client secret required, tokens securely saved locally in `.minecraft/config/espoty/tokens.json`.
  - **Texture Caching**: Album covers are downloaded asynchronously and converted to `DynamicTexture` objects in RAM, preventing memory leaks and repetitive network requests.

---

## 🎮 Controls & Keybindings

| Key | Action | Description |
|---|---|---|
| **`O`** | Open eSpoty Hub | Opens the full interactive music control screen. |
| *(Configurable)* | Play / Pause | Toggles track playback. |
| *(Configurable)* | Next Track | Skips to the next song in your queue. |
| *(Configurable)* | Previous Track | Skips to the previous song. |
| *(Configurable)* | Toggle HUD | Turns the on-screen music overlay on/off. |

*All keybindings can be customized in the Minecraft `Options -> Controls -> Key Binds` menu under the **eSpoty** category.*

---

## 💬 Commands

- `/espoty` — Opens the eSpoty interactive GUI.
- `/espoty play` — Resumes Spotify playback.
- `/espoty pause` — Pauses Spotify playback.
- `/espoty next` — Skips to the next track.
- `/espoty prev` — Skips to the previous track.
- `/espoty now` — Prints the currently playing track and artist to in-game chat.
- `/espoty login` — Starts the browser authorization flow.
- `/espoty status` — Displays PC app status and account connection state.

---

## 🚀 How to Connect Spotify

1. Launch Minecraft with the **eSpoty** mod installed in your `mods` folder.
2. Press **`O`** or type `/espoty` to open the hub.
3. Click **"Connect to Spotify"** in the Settings tab or on the welcome screen.
4. Your default web browser will open with the Spotify authorization page.
5. Click **Agree** / **Authorize**.
6. The built-in local server receives the authorization token, and eSpoty instantly connects! You can now return to Minecraft and enjoy your music!

---

## 🛠️ Building from Source

Requirements:
- Java 21+ LTS
- Fabric Loader 0.16.9+
- Minecraft 1.21.1

```bash
# Clone the repository
git clone https://github.com/your-username/eSpoty.git
cd eSpoty

# Build the mod jar
./gradlew build
```

The compiled mod jar will be in `build/libs/espoty-1.0.0.jar`.
