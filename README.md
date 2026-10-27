# eSpoty - Spotify Integration for Minecraft (Fabric Multi-Version)

A high-performance, modern Spotify integration mod for Fabric. Features a liquid glassmorphic theme engine, interactive in-game HUD drag-and-drop editor with corner scaling, real-time synchronized karaoke lyrics, 3D celestial sky lyrics with dynamic gaze tracking, full playback controls, queue management, chat-accessible HUD controls, audio ducking, and automatic local PC Spotify desktop process detection.

---

## Features

- Direct Local PC Link (Zero Login Required):
  - Automatically connects to your running Spotify desktop app on Windows via Windows Media SMTC and a local loopback bridge.
  - Zero developer account setup, zero client IDs or secrets, and zero browser login required.
  - Controls Spotify application audio session directly without modifying Windows master output volume.
  - Optional Spotify Web API PKCE login for extended remote control features.

- Interactive In-Game HUD Drag & Drop Editor:
  - Live HUD positioning: drag the music card anywhere on your screen.
  - Corner resize handles: scale your HUD smoothly from 50% to 150%.
  - Magnetic guidelines: automatic snapping to screen edges and center axes.
  - Live preview with real-time waveform and song metadata simulation.

- Glassmorphic Theme Engine:
  - Six curated presets: Borealis (Neon Mint & Deep Space), Midnight (Obsidian & Royal Violet), OLED Black (Pitch Black & Emerald), Daylight (Frosted Slate & Azure), Sunset (Velvet Dusk & Coral Rose), and Cyber Gold (Titanium & Radiant Gold).
  - Custom accent overrides (Mint Cyan, Spotify Green, Royal Violet, Electric Blue, Sunset Orange, Coral Rose, Cyber Gold, Ice White).
  - Adjustable glass opacity slider and optional theme synchronization across HUD, mini player, and sky lyrics.

- Song-Synchronized Equalizer Visualizer:
  - Dynamic frequency waveform that pulses and grooves in real-time with song rhythm, BPM tempo, kick/snare attacks, and lyric vocal harmonics.
  - Pre-computed harmonic frequency curves for high render efficiency and zero FPS drop.

- Interactive In-Game Control Hub (Screen):
  - Now Playing: High-resolution album artwork, track title, artists, album, and favorites button.
  - Full Playback Controls: Play, Pause, Next Track, Previous Track, Shuffle toggle, and Repeat cycle (Off / All / One).
  - Queue Management: View upcoming tracks, reorder items, skip to any track in queue, or remove entries.
  - Dynamic State Handling: Control buttons automatically adapt and dim if playback actions are restricted by Spotify.
  - Interactive Seek Bar: Click or drag the progress scrubber to jump to any timestamp in the track.
  - Application Volume Slider: Real-time volume scrubber (0% - 100%) targeting Spotify exclusively.
  - Dedicated Themes Tab: Live preview cards and color swatch discs for immediate visual selection.

- Liked Songs (Favorites):
  - Favorite any track with a single click using the Heart button.
  - Liked tracks display an indicator both in the Hub and directly on the in-game HUD and main menu player.

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
  - Configurable screen positions (Top Left, Top Right, Bottom Left, Bottom Right, Off, or Custom via HUD Editor), opacity, and scale.

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

1. Place the compiled `espoty-3.0.0.jar` (or version-specific jar) into your Minecraft `.minecraft/mods` folder.
2. Start your Spotify desktop application on your PC.
3. Launch Minecraft with Fabric Loader.
4. Press `K` (or type `/espoty`) to open the Hub. eSpoty connects automatically to your local Spotify app.

---

## Building from Source

Requirements:
- Java 21 LTS or higher
- Fabric Loader 0.16.0 or higher

```bash
# Clone the repository
git clone https://github.com/itzCrih/eSpoty.git
cd eSpoty

# Build the mod jar
./gradlew build
```

The compiled mod jar will be located in `build/libs/espoty-3.0.0.jar`.

---

## Author and License

- Author: itzCrih (https://itzcrih.it)
- Repository: https://github.com/itzCrih/eSpoty
- License: MIT License
