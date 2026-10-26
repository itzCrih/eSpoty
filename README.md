# eSpoty - Spotify Integration for Minecraft (Fabric 1.21.x - 1.21.11)

A high-performance, modern Spotify integration mod for Fabric 1.21 through 1.21.11. Features a liquid glassmorphic theme engine, real-time synchronized karaoke lyrics, 3D celestial sky lyrics with dynamic gaze tracking, full playback controls, queue management, chat-accessible HUD controls, audio ducking, and automatic local PC Spotify desktop process detection.

---

## Features

- Direct Local PC Link (Zero Login Required):
  - Automatically connects to your running Spotify desktop app on Windows via Windows Media SMTC and a local loopback bridge.
  - Zero developer account setup, zero client IDs or secrets, and zero browser login required.
  - Optional Spotify Web API PKCE login for extended remote control features.

- Glassmorphic Theme Engine:
  - Aurora aesthetic with six curated presets: Aurora (Neon Mint & Deep Space), Midnight (Obsidian & Royal Violet), OLED Black (Pitch Black & Emerald), Daylight (Frosted Slate & Azure), Sunset (Velvet Dusk & Coral Rose), and Cyber Gold (Titanium & Radiant Gold).
  - Custom accent overrides (Mint Cyan, Spotify Green, Royal Violet, Electric Blue, Sunset Orange, Coral Rose, Cyber Gold, Ice White).
  - Adjustable glass opacity slider and optional theme synchronization across HUD, mini player, and sky lyrics.

- Song-Synchronized Equalizer Visualizer:
  - Dynamic frequency waveform that pulses and grooves in real-time with song rhythm, BPM tempo, kick/snare attacks, and lyric vocal harmonics.
  - Pre-computed harmonic frequency curves for high render efficiency and zero FPS drop.

- Interactive In-Game Control Hub (Screen):
  - Now Playing: High-resolution album artwork, track title, artists, album, and favorites heart button.
  - Full Playback Controls: Play, Pause, Next Track, Previous Track, Shuffle toggle, and Repeat cycle (Off / All / One).
  - Dynamic State Handling: Control buttons automatically adapt and dim if playback actions are restricted by Spotify.
  - Interactive Seek Bar: Click or drag the progress scrubber to jump to any timestamp in the track.
  - Master Volume Slider: Real-time volume scrubber (0% - 100%) with instant responsiveness.
  - Dedicated Themes Tab: Live preview cards and color swatch discs for immediate visual selection.

- Liked Songs (Favorites):
  - Favorite any track with a single click using the Heart button.
  - Liked tracks display a heart indicator both in the Hub and directly on the in-game HUD and main menu player.

- Real-Time Synchronized Lyrics:
  - Live karaoke-style synchronized lyrics powered by the LRCLib database.
  - Accurate sub-frame alignment with smooth auto-scroll.
  - Active line highlighted with smooth float-up transitions.
  - Multiple font selections: Modern Sans, Spotify Bold, Minecraft Pixel, Vanilla Smooth, and Monospace.

- Celestial Sky Lyrics (3D World Horizon):
  - Dynamic gaze tracking: smoothly follows the player's horizontal viewing direction across the sky dome.
  - Fixed North & South mode available for monumental static placement.
  - Pure white luminescence with emissive fullbright light for clear visibility day or night.
  - Strict depth-tested occlusion: blocks, terrain, pillars, trees, and entities always occlude the text.
  - Fully compatible with Iris and Sodium shader pipelines via dedicated isolated batch buffers.
  - Dual-depth drop shadows with polygon offset completely eliminating Z-fighting flicker.

- Modern In-Game HUD & Chat Playback Controls:
  - Glassmorphic card displaying current track, artist, album art, progress bar, and synced mini waveform.
  - Interactive chat controls dock: smoothly slides into view when opening Minecraft chat, allowing one-click track skips, pause, and liking without leaving the game.
  - Configurable screen positions (Top Left, Top Right, Bottom Left, Bottom Right, or Off), opacity, and scale.

- Main Menu Mini Player:
  - Elegant mini player widget on the Minecraft title screen with album art, metadata, controls, and waveform visualizer.

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

1. Place the compiled `espoty-2.0.0.jar` into your Minecraft `.minecraft/mods` folder.
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

The compiled mod jar will be located in `build/libs/espoty-2.0.0.jar`.

---

## Author and License

- Author: itzCrih (https://itzcrih.it)
- Repository: https://github.com/itzCrih/eSpoty
- License: MIT License
