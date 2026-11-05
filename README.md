# SolStream Studio (`v1.0.1`)

**SolStream Studio** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (~3 MB), zero-ad Android live-streaming broadcast studio engineered for **120 FPS competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **1440p60 / 1080p60** hardware-encoded video and **split 32-bit float studio commentary** to YouTube Live over encrypted **RTMPS**.

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120 FPS smoothness and voice clarity, questioning every edge case, and testing builds live on real hardware.
>
> **Architecture & Open-Source Credit:** Almost the entire application—the zero-copy OpenGL ES frame pacer, HUD aspect scaler, split 32-bit float vocal DSP, drift-locked stereo game mixer, look-ahead true-peak limiter, hardware capability scanner, AES-256 Keystore vault, and sleeping Accessibility Ghost Pill—is custom-engineered original code. For the final encrypted RTMPS network socket transport layer (`RtmpClient`), this project gratefully uses **Pedro Sánchez's** open-source [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) library under the Apache 2.0 License.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I will literally copy-paste your message straight back to Gemini and ChatGPT!
> * **Submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`lintDebug`, `testDebugUnitTest`, and `assembleRelease`) passes green, and open a PR.

---

## 1. Download & Install (`~3 MB` Signed Release APK)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** section on the right side of this GitHub repository.
2. Tap **`SolStream-Studio-v1.0.1.apk`** under **Assets** to download directly to your Android phone (no login or `.zip` extraction needed).
3. *(One-Time Note if upgrading from the earliest debug-signed `v1.0.0` test build: uninstall `v1.0.0` once before installing `v1.0.1`. All future official releases use permanent cryptographic signing and install straight over the top without losing settings!)*

---

## 2. What SolStream Studio Solves for Mobile Gamers

Most mobile streaming apps hurt competitive gameplay by bundling `100+ MB` of camera/social bloat, capping quality to older H.264, breaking Bluetooth stereo footsteps when squad voice chat opens, or running audio limiters over mixed audio that make gunshots sound underwater.

* **Plays at 120 FPS, Streams Cleanly at 60/30 FPS:** Your phone display stays locked at 120 Hz while the dedicated hardware media silicon (`HEVC / H.264 / HW AV1`) encodes paced 60 or 30 FPS video.
* **Isolated Voice-Only 32-Bit Float Studio Bus:** Processes *only* your microphone (high-pass rumble cut, noise attenuation, vocal EQ, de-esser, compressor) before mixing with untouched stereo game audio via a drift-locked circular buffer and a 64-sample look-ahead true-peak limiter (`-1.1 dBTP`).
* **Lobby-Safe Queue-Aware Adaptive Bitrate (ABR 2.0):** Caps outgoing RTMP packet queues to prevent router bufferbloat and steps down bitrate *only* on real upload queue congestion—never penalizing low-motion game lobbies or BRB screens.
* **6+ Hour Marathon Stability:** Monotonic 64-bit video/audio timestamps, automatic `AudioRecord` (`ERROR_DEAD_OBJECT`) self-recovery, auto-renewing 12-hour broadcast wake-locks, and sequential failover to YouTube's backup RTMPS server (`b.rtmps.youtube.com`).
* **0-Hz Sleeping In-Game Ghost Pill:** Stays at 38% opacity with zero UI redraw overhead during gunfights; double-tap to mute stream mic (while your squad still hears you!), mute game audio, trigger `1.5 Mbps` **BRB Privacy Blackout**, or adjust bitrate live.

---

## 3. Quick Plain-English Setup Guide

### Step A: One-Time Android 13–17 & Earphone Setup (2 Minutes)
1. **Unlock Restricted Accessibility (Sideloaded APK Security Rule):**
   * In Card 1, tap **`4. Enable Accessibility`** → *Installed apps* → tap greyed-out **SolStream Studio** once so Android registers the security prompt.
   * Return to SolStream Studio and tap **`3. Unlock Restricted`** (teleports directly to SolStream's App Info page) → tap **`⋮`** (top-right) → **Allow restricted settings**.
   * Tap **`4. Enable Accessibility`** again and switch **SolStream Studio** **ON**.
2. **Bluetooth Neckband Full Stereo + Low-Latency Game Mode Tip:**
   * Using any Bluetooth headset's built-in mic forces Bluetooth into muffled mono call mode (`HFP`). To keep crisp **Stereo footsteps + low-latency Game Mode** in your earphones while using your phone's 48 kHz studio mic: open **Bluetooth Settings → Earphones Gear ⚙️ → toggle `Calls` OFF** (keep `Audio` **ON**).
   * On Samsung One UI: *Settings → Sounds and vibration → Separate app sound* → lock *Call of Duty* to your Bluetooth device.
3. **Single-App Notification Privacy:** When Android asks *"Start recording or casting?"*, pick **"A single app" → Call of Duty** so personal notifications, calls, and OTPs never appear on stream.

### Step B: Pre-Stream Studio Cards
* **Card 1 — Pre-Flight & Direct Settings Teleporters:** One-tap buttons for Mic/Notification permissions, Unrestricted Battery, App Info (`Allow restricted settings`), and Accessibility.
* **Card 2 — Encrypted YouTube RTMPS Vault (`AES-256-GCM`):** Pre-filled with `rtmps://a.rtmps.youtube.com:443/live2` (works with or without `:443` and auto-configures `b.rtmps.youtube.com` backup failover). Includes **Paste Key**, live **Show/Hide Key**, **Clear Key**, **Save Vault**, and **Test Internet RTMPS Ping & Upload** (verifies DNS, TLS 1.2/1.3 SNI handshake, idle RTT/jitter, sustained Mbps upload, and router bufferbloat).
* **Card 3 — Video, Bitrate & HUD Framing:** Select Hardware `HEVC / H264 / AV1`, `1440p / 1080p`, `60 / 30 FPS`, `0° / 180°` USB-C cable flip or `9:16` Shorts, `12–24 Mbps` target (`CBR` recommended), `FIT / SMART / FILL` HUD framing, and optional single-pass GPU crisp sharpen.
* **Card 4 — 32-Bit Float Studio Audio & 5s Mic Tester:** Select Mic Input & sharing source (`AUTO`), `128–320 kbps` AAC-LC, `RAW / CLEAN / STUDIO` voice presets, `75–100 Hz` HPF, light noise cut, Gate/Compressor/EQ/De-Esser, Mic/Game mix levels, VAD Auto-Ducking, lip-sync offset, and **5s Grip Recording & Playback**.
* **Card 5 — Launch, Session Report & Updater:** Includes Silent-Stream volume check, secret-redacted **Session Notes & Report** (`App Process PSS`, heap breakdown, real °C, Fresh vs. Encoded FPS, Avg QP, queue frames), passive **Glip Benchmark**, and **Check GitHub Update**.

---

## 4. Source Architecture & Strict Engineering Invariants

```text
app/src/main/
├── AndroidManifest.xml                  # Minimal permissions (zero Camera, Storage, Contacts, or SAW)
├── res/xml/accessibility_service_config.xml
└── java/com/solgaming/solstreamstudio/
    ├── MainActivity.kt                  # Pure-Kotlin Pre-Stream Studio, TLS+Upload Probe, Updater
    ├── SolStreamService.kt              # Accessibility + ForegroundService, ABR 2.0, Backup Failover, Ghost Pill
    ├── SolVideoPipeline.kt              # SurfaceTexture + Dynamic Resize -> OpenGL -> HW MediaCodec + QP stats
    ├── SolDualAudioEngine.kt            # Drift-locked Stereo Ring + Mono Float Mic -> Look-Ahead True-Peak Limiter
    ├── MicDspProcessor.kt               # Zero-allocation 32-bit float vocal DSP (64-bit biquad state)
    ├── CodecCapabilityProbe.kt          # Hardware MediaCodec, PerformancePoint & Surface dry-run scanner
    ├── KeystoreVault.kt                 # Verified AndroidKeyStore AES-256-GCM vault & URL normalizer
    └── DiagnosticsLogger.kt             # Secret-redacted telemetry, PSS/Heap profiler & local crash note
