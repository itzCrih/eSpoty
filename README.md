# eSpoty - Spotify Integration for Minecraft (Fabric 1.21.x - 1.21.11)

A high-performance, modern Spotify integration mod for Fabric 1.21 through 1.21.11. Features a sleek Gold and Yellow glassmorphic HUD, real-time synchronized karaoke lyrics, 3D celestial sky lyrics along the horizon, full playback controls, queue management, audio ducking, and automatic local PC Spotify desktop process detection.

---

## Features

- Direct Local PC Link (Zero Login Required):
  - Automatically connects to your running Spotify desktop app on Windows via Windows Media SMTC and a local loopback bridge.
  - Zero developer account setup, zero client IDs or secrets, and zero browser login required.
  - Optional Spotify Web API PKCE login for extended remote control features.

- Interactive In-Game Control Hub (Screen):
  - Now Playing: High-resolution album artwork, track title, artists, album, and favorites heart button.
  - Full Playback Controls: Play, Pause, Next Track, Previous Track, Shuffle toggle, and Repeat cycle (Off / All / One).
  - Dynamic State Handling: Control buttons automatically adapt and dim if playback actions are restricted by Spotify.
  - Interactive Seek Bar: Click or drag the progress scrubber to jump to any timestamp in the track.
  - Master Volume Slider: Real-time volume scrubber (0% - 100%) with instant responsiveness.

- Liked Songs (Favorites):
  - Favorite any track with a single click using the Heart button.
  - Liked tracks display a gold heart indicator both in the Hub and directly on the in-game HUD.

- Real-Time Synchronized Lyrics:
  - Live karaoke-style synchronized lyrics powered by the LRCLib database.
  - Accurate sub-frame alignment with smooth auto-scroll.
  - Active line highlighted in bright white with smooth progression.
  - HUD lyrics pill: subtle floating pill below the in-game HUD.

- Celestial Sky Lyrics (3D World Horizon):
  - Pure lyrics rendered directly in the Minecraft sky along the horizon.
  - Strict depth-tested occlusion: blocks, terrain, pillars, trees, and entities always occlude the text (never bleeds through structures).
  - Customizable scale slider in settings (50% to 200%).
  - Customizable line count slider in settings (3 to 15 lines).
  - Dual-depth drop shadow architecture that completely eliminates OpenGL Z-fighting flicker.

- Modern Gold and Yellow In-Game HUD:
  - Glassmorphic dark card with electric gold and yellow borders.
  - Smooth marquee scroller for long track titles and artists.
  - Dynamic client-interpolated progress bar running at 60+ FPS.
  - Configurable screen positions: Top Right, Top Left, Bottom Right, Bottom Left, or Disabled.
  - Compact mode and configurable Auto-Hide timer.

- Minecraft Audio Ducking:
  - Automatically dims in-game ambient music and sound effects while Spotify is playing music, restoring standard volumes when paused.

- Mod Menu Integration:
  - Direct configuration and Hub access from the Fabric Mod Menu list.

- Integrated Update Checker:
  - Non-blocking asynchronous GitHub release checker that notifies players when a new version is available.

---

## Controls and Keybindings

| Key | Action | Description |
|---|---|---|
| K | Open eSpoty Hub | Opens the full interactive Spotify control screen. |
| (Unbound) | Play / Pause | Toggles track playback. |
| (Unbound) | Next Track | Skips to the next song in your queue. |
| (Unbound) | Previous Track | Skips to the previous song. |
| (Unbound) | Toggle HUD | Toggles the in-game music overlay on or off. |

All keybindings can be customized in the Minecraft Options -> Controls -> Key Binds menu under the eSpoty category.

---

## Commands

- `/espoty` - Opens the eSpoty interactive player GUI.
- `/espoty play` - Resumes Spotify playback.
- `/espoty pause` - Pauses Spotify playback.
- `/espoty next` - Skips to the next track.
- `/espoty prev` - Skips to the previous track.
- `/espoty now` - Prints the currently playing track and artist to in-game chat.
- `/espoty status` - Displays PC app status and bridge connection state.
- `/espoty refresh` - Forces a full reload of the local bridge session.

---

## Getting Started

1. Place the compiled `espoty-1.1.4.jar` into your Minecraft `.minecraft/mods` folder.
2. Start your Spotify desktop application on your PC.
3. Launch Minecraft with Fabric Loader.
4. Press `K` (or type `/espoty`) to open the Hub. eSpoty connects automatically to your local Spotify app.

---

## Building from Source

Requirements:
- Java 21 LTS or higher
- Fabric Loader 0.16.0 or higher
- Minecraft 1.21.1 / 1.21.x / 1.21.11

```bash
# Clone the repository
git clone https://github.com/itzCrih/eSpoty.git
cd eSpoty

# Build the mod jar
./gradlew build
```

The compiled mod jar will be located in `build/libs/espoty-1.1.4.jar`.

---

## Author and License

- Author: itzCrih (https://itzcrih.it)
- Repository: https://github.com/itzCrih/eSpoty
- License: MIT License
