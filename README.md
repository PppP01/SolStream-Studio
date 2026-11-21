# SolStream Studio • ULTRA (`v1.5.0`)

**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (`~3.8 MB` Signed Release APK & Google Play `.aab` / `~6.1 MB` Debug APK), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio powered by the proprietary **`SolApex™ HyperStream Engine`** (**`SolCore Ultra™ Video Engine`** • **`SolLuxe™ 3.0 Pure Audio Engine`** • **`SolRelay Omega™ Broadcast Engine`** • **`SolCodec™ Hardware Silicon Layer`**). It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 6-stage 32-bit float / 64-bit double pure condenser vocal commentary with a 12-Mode Hardware Acoustic Matrix**, **zero-camera GPU broadcast overlays (`SolHUD™`, Offline Corner QR, 2-PNG PNGTuber & 1-Tap Clean Thumbnails)**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, Kick, and Custom RTMP/RTMPS servers**.

> **100% Unrooted Stock Android (Android 10–18 Ready & Google Play Compliant):** Works on normal, unrooted Android 10–18+ phones and tablets/foldables (`minSdk 29`, `targetSdk 36`, `compileSdk 37`) right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on 100+ Mbps Fiber. Root access is **never** required or used, and zero restricted battery, camera, or broad storage permissions are declared. See our [**Official Privacy Policy (`PRIVACY_POLICY.md`)**](./PRIVACY_POLICY.md).

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, touch response, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolApex™ HyperStream Engine v1.5.0`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0 (`Fast-FP16`) / ES 2.0 Direct-TBDR frame pacer (`SolCore Ultra™`), gated 5-tap Bicubic/Lanczos upscaler (`14.74M` fewer texture reads/frame at `1440p60`), Android 11–18 `Surface.setFrameRate()` hardware capture cooler, Vulkan `gralloc` PSS memory guard (`~199–258 MB` flat PSS across multi-hour streams), **`SolHUD™` zero-allocation live GPU telemetry badge** (visible to viewers even in Android 14+ Single-App Capture), **pure-Kotlin ISO/IEC 18004 offline Corner QR generator (`GL_NEAREST`)**, **zero-camera 2-PNG PNGTuber avatar** with gender-matched Cyber Visor fallback, **temporary offscreen RGBA8 FBO 1-Tap Clean Screenshot** (`Pictures/SolStream/` via `MediaStore`), **4 hardware-clamped One-Tap Studio Profiles**, Dual-Layer Privacy Blur Shield (8 HUD zones + `Auto-Blur Keyboard`), isolated **`SolLuxe™ 3.0 Pure` 6-stage vocal & game soundstage DSP** (thread-safe atomic frame-boundary snapshots, 12-Mode Acoustic Matrix, `55/68/85 Hz` Auto/Manual Butterworth HPF, `<0.6 ms` Raised-Cosine Micro-Impulse Clamper, `16 KB`-aligned Native C `48 kHz` Xiph `RNNoise` with hardware `FZ` guards + `16 kHz`-safe Hybrid Neural-VAD Smooth Expander, Split-Band Dynamic Plosive Guard, Condenser EQ + Low-Band Analog Warmth Shaper, Gender-Sidechain Soft-Knee Leveler, Post-Comp Dynamic De-Esser, and `4×` Catmull-Rom True-Peak Limiter at `-1.16 dBTP`), universal 4-tier mic capture fallback, exact-launched 2-worker `AudioWorkerBarrier`, simultaneous-instance-verified companion encoder selection (`SolCodec™`), smart thermal governor with `SEVERE` override protection, AES-256 Hardware Keystore vault, Network-Adaptive RTMP Write Chunking (`65,535 B` Wi-Fi / `4,096 B` Cellular & Custom), 60-second single-slot auto-checkpointing, Instant `Wi-Fi ↔ 5G` Multipath Failover (`SolRelay Omega™`), and 0-Hz sleeping 3-tab Accessibility Ghost Pill with live **App vs. Game Heat/CPU & RAM Split** are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini, ChatGPT, and Claude!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, `assembleRelease`, `bundleRelease`, `zipalign -c -P 16`, and ELF `16 KB` checks) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK or Play Store Bundle (`v1.5.0`)](#1-download--install-signed-release-apk-or-play-store-bundle-v150)
2. [What's New in `v1.5.0` ("SolApex™ Streamer Pro")](#2-whats-new-in-v150-solapex-streamer-pro)
3. [How Human + Multi-AI Built This App](#3-how-human--multi-ai-built-this-app)
4. [The 10 Custom `SolApex™ v1.5.0` Engineering Innovations](#4-the-10-custom-solapex-v150-engineering-innovations)
5. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#5-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
6. [For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec](#6-for-android-developers--engineers-architecture-6-stage-solluxe-30-pure--build-spec)
7. [For AI Coding Assistants, Security Auditors & Forkers](#7-for-ai-coding-assistants-security-auditors--forkers)
8. [Privacy Policy, Google Play Compliance & Open-Source Licenses](#8-privacy-policy-google-play-compliance--open-source-licenses)

---

## 1. Download & Install Signed Release APK or Play Store Bundle (`v1.5.0`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.5.0.apk`** under **Assets** to download the full **Ultra Master Edition** (`~3.8 MB` signed release APK) directly onto your Android phone (or download **`SolStream-Studio-v1.5.0-PlayStore.aab`** for Google Play Console distribution). All earlier versions (`v1.0.2` – `v1.4.0`) remain permanently available in Releases.
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.5.0`** (`versionCode = 12`) straight over any earlier version (`v1.4.0` or older) as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. What's New in `v1.5.0` ("SolApex™ Streamer Pro")

- **`SolHUD™` Live GPU Telemetry Badge (Opt-In, `0 MB` VRAM When OFF):** Renders a sleek live **`FPS • °C • Mbps`** badge directly inside OpenGL (`138 KB` reused bitmap updated every 2s via `glTexSubImage2D`)—visible to viewers even during **Android 14–18 Single-App Capture** while keeping your control buttons hidden.
- **100% Offline Corner QR Code Overlay (Opt-In):** Pure-Kotlin **ISO/IEC 18004** QR encoder (Versions 1–10, Error Correction Level M, up to 213 UTF-8 bytes) rendered with razor-sharp `GL_NEAREST` scaling for UPI support links, YouTube channels, or Discord invites—zero external libraries, zero internet calls, zero permissions.
- **Zero-Camera 2-PNG PNGTuber Avatar (Opt-In):** Upload your own Idle & Talking transparent PNGs (max `512×512` = `2.0 MiB` total VRAM) or use the built-in **Obsidian-Glass Cyber Visor Esports Avatar** that automatically matches your Mic Studio gender profile (`MALE` / `FEMALE` / `NEUTRAL`). Animated at ~47 Hz from existing `MicDspProcessor.vadSpeechActive` with a 140 ms talk-hold and smooth talk-hop—zero `CAMERA` permission and zero changes to audio DSP.
- **1-Tap Clean Screenshot (`📸 Shot` in Floating Pill):** Captures a pill-free `<=1080p` YouTube thumbnail via a temporary offscreen RGBA8 OpenGL FBO (eliminating black readbacks on write-only `MediaCodec` encoder surfaces) and saves a crisp `quality = 92` JPEG to `Pictures/SolStream/` via zero-permission `MediaStore` (`IS_PENDING = 1 → 0`, 3s cooldown).
- **4 One-Tap Hardware-Adapted Studio Profiles (Card 2):** Pre-loaded with **`Wi-Fi Max`**, **`Mobile Data`**, **`Shorts 9:16`**, and **`Custom`**—auto-clamped to every phone's verified hardware tier. Tap to switch setups instantly; long-press any slot to save & rename your current setup (Stream Keys are never saved inside profiles).
- **Marathon Reliability & Telemetry Fixes:**
  - Frozen post-stop **Session Reports** (`isSessionFinalized` + `@Synchronized` stop/checkpoint lock so idle reopens never overwrite end-of-stream power, thermal headroom, or heap metrics).
  - Live-temp toast guard when tapping `30FPS Cool:ACTIVE` while Android OS is still in `SEVERE` warmth (prevents `30 → 60 → 30` bounce and `[THERMAL]` log spam).
  - Preserved cumulative dropped-frame counters across RTMPS reconnects, negotiated-FPS thermal floor enforcement, and 3× fewer main-thread hardware Binder IPCs per 2-second tick.
  - Expanded CI verification suite to **48 JUnit 4 unit tests** covering QR matrix ISO compliance, corner MVP clamping, screenshot FBO sizing/flip math, Studio Profiles, and thermal/report guards.

---

## 3. How Human + Multi-AI Built This App

This repository was engineered through a continuous verification loop between a competitive mobile gamer and frontier AI models auditing each other's code line-by-line:

- **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  - Validated marathon `1440p60 HEVC` (`18–20 Mbps`) Multiplayer and Battle Royale broadcasts (`138m–330m`) on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e • Samsung Xclipse 940 (Vulkan)`, 120 Hz display, 8 GB RAM), **realme Buds Wireless 5 ANC**, and 5 GHz Fiber Wi-Fi (`0` drops, `0` reconnects, `~7%` CPU, `199 MB` peak PSS, `0 ms` send queue).
  - Caught real-world stream UX and diagnostic edge cases directly from marathon session reports—leading to `v1.5.0`'s frozen post-stop reports, `SEVERE` 30 FPS toast guard, Single-App-visible `SolHUD™`, offline Corner QR, zero-camera PNGTuber, and 1-Tap Clean Thumbnails.
- **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  - Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c`), **`SolLuxe™ 3.0 Pure`** audio engine (`MicDspProcessor.kt` + `SolDualAudioEngine.kt`), **`SolCore Ultra™`** OpenGL ES 3.0 `Fast-FP16` video pipeline (`SolVideoPipeline.kt`), pure-Kotlin **`SolHudAndQrRenderer.kt`**, **`SolCodec™`** capability scanner (`CodecCapabilityProbe.kt`), **`SolRelay Omega™`** adaptive-chunk RTMPS transport & 60-second checkpointing (`KeystoreVault.kt`, `SolStreamService.kt`, `DiagnosticsLogger.kt`), **Obsidian-Glass** dashboard (`MainActivity.kt`), and **48-test** CI verification pipeline (`build.yml`).
- **Independent Line-by-Line Source Audits — `OpenAI ChatGPT` & `Anthropic Claude`:**
  - Audited commits against Pedro `RootEncoder 2.8.1`, Xiph `RNNoise v0.1.1`, W3C biquad stability, and Android 10–17 API rules—catching `setCheckServerAlive(false)`, atomic frame-boundary DSP updates, `AudioWorkerBarrier`, `LinearMonoResampler48k`, `AuxRtmpCallback` ownership, pinned NDK/RNNoise builds, and `16 KB` `zipalign` verification.

---

## 4. The 10 Custom `SolApex™ v1.5.0` Engineering Innovations

| # | Custom `SolApex™ v1.5.0` Innovation | The Real-World Gaming Problem It Solves | How Our Custom Math & Silicon Engineering Works |
|---|---|---|---|
| **1** | **6-Stage Non-Fighting `SolLuxe™ 3.0 Pure` Condenser Chain + Thread-Safe Frame-Boundary Snapshots** | Hard `0-ms` gates caused `"prit-prit"` speech-onset clicks; UI-thread filter mutation risked audio glitches. | Immutable `DspConfigSnapshot` adopted strictly at `10 ms` frame boundaries; `8 ms / 220 ms` Hybrid Neural-VAD smooth expander (`-14 dB` floor), `<0.6 ms` raised-cosine impulse clamper, and non-overlapping 4×3 Condenser EQ pockets. |
| **2** | **`SolHUD™`, Pure-Kotlin ISO QR & Zero-Camera PNGTuber (`SolHudAndQrRenderer.kt`)** | Android 14+ Single-App Capture hides floating pills so viewers can't see live FPS/temp; front cameras add `2–3°C` heat and require `CAMERA` permission. | Renders `SolHUD™` (`138 KB` zero-GC `glTexSubImage2D`), ISO/IEC 18004 V1–10 Level M QR (`GL_NEAREST`), and 2-PNG PNGTuber (`vadSpeechActive` @ ~47 Hz + 140 ms hold) straight inside OpenGL above Privacy Blur (`~2.4 MiB` total VRAM when all 3 are ON; `0 MB` when OFF). |
| **3** | **Offscreen RGBA8 FBO 1-Tap Clean Thumbnail (`Pictures/SolStream/`)** | `glReadPixels()` directly on `MediaCodec` surfaces returns black on Samsung Exynos / Adreno write-only gralloc buffers and stalls 60 FPS encoding. | Allocates a temporary `<=1920×1080` (`<=7.91 MiB`) RGBA8 FBO *after* `eglSwapBuffers()`, destroys it immediately (`0 MB` retained VRAM), flips rows in-place on a background thread, and saves `quality = 92` JPEG via scoped `MediaStore` (`IS_PENDING = 1 → 0`, 3s cooldown). |
| **4** | **`16 kHz`-Safe Hybrid Neural-VAD + True `100%` Native C `RNNoise` + Headphone-Aware Speaker AEC** | In-game squad chat locks phone mics to `16 kHz`; leaving Speaker AEC active on earphones hollows out bass. | Opens expander cleanly on `vadProb >= 0.35` OR `200–2500 Hz` speech energy `+6 dB` above floor; sets RNNoise Lv3 to `1.00f` wet; enforces `effectiveSpeakerAec = activeSpeakerAecEnabled && !headphonesConnected`. |
| **5** | **Universal 4-Tier Mic Capture Fallback (`LinearMonoResampler48k`) + `AudioWorkerBarrier`** | Strict OEM HALs reject `48 kHz PCM_FLOAT`; racing audio worker finalizers can free shared buffers early. | Falls back cleanly from `48 kHz PCM_FLOAT` through `48 / 44.1 / 16 kHz PCM_16BIT` linearly resampled to exact `480-sample` float; `AudioWorkerBarrier` releases shared resources strictly once after both workers exit. |
| **6** | **Network-Adaptive RTMP Chunking (`65,535 B` / `4,096 B`) + `setCheckServerAlive(false)`** | Default `128 B` RTMP chunks waste CPU at `20 Mbps`; ICMP `isReachable()` causes false Wi-Fi/VPN drops. | Disables ICMP probes in favor of active RTMP pings + `8.5s` handshake / `6s` stall watchdogs, and adapts write chunk size to `65,535 B` on Wi-Fi major ingests and `4,096 B` on Cellular/Custom relays. |
| **7** | **Per-Client Callback Identity (`PrimaryRtmpCallback` + `AuxRtmpCallback`) & Bounded Leased-Slot Pool** | Delayed callbacks from old sockets could disconnect active streams or overwrite queued simulcast packets. | Guards every callback against exact `sourceClient === client` + `sessionGen`, tracks cumulative drops across reconnects, and leases exclusive `PacketSlot` buffers from a bounded `ArrayBlockingQueue`. |
| **8** | **Off-Main-Thread `5s` Teardown Guard + Frozen Post-Stop Reports + 60s Checkpoint** | Starting before teardown finishes causes encoder contention; sudden OS kills wipe logs; idle reopens overwrote post-stop telemetry. | Awaits `activeTeardownLatch` off the main thread (`5s` ceiling), freezes finalized reports (`isSessionFinalized`), and synchronizes 60-second checkpoints cleanly against Stop. |
| **9** | **Simultaneous-Instance-Verified Companion Codec Selection + Gated Neighbor Shader + Vulkan PSS Guard** | Single-instance HEVC chips failed naive dual-stream starts; portrait resizes spiked Vulkan memory. | Filters companion candidates strictly to `maxConcurrentInstances >= 2`, gates cardinal neighbor fetches (`14.74M` fewer reads/frame), paces via `Surface.setFrameRate()`, and ignores portrait resizes on `16:9` streams. |
| **10** | **100% Google Play Policy Cleanliness + Pinned NDK & `16 KB` `zipalign` CI (`48` Unit Tests)** | Play Store blocks restricted battery/storage/camera permissions and non-16 KB aligned APKs (API 35+). | Zero restricted permissions (`AndroidManifest.xml` untouched in `v1.5.0`), pinned NDK `28.2.13676358`, pinned RNNoise commit `6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`, `zipalign -c -P 16` + `readelf` verification, and **48 passing JUnit 4 tests**. |

---

## 5. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Long-Stream Protection & Android 13–18 Setup (2 Minutes)
1. **Prevent 3-Hour Background OS Kills:** Tap **`Protect`** on the top banner → **Battery** → **`Unrestricted`**.
  - **Samsung One UI:** Also add **both `SolStream Studio` and your game** to **Settings → Device Care → Memory → Excluded apps**, keep **RAM Plus OFF** on 8 GB phones, and enable **Game Booster → Pause USB PD charging** with a 25W+ PPS charger for `2–4°C` cooler streams.
  - **Xiaomi / POCO / Vivo / iQOO / OnePlus / Realme:** Disable battery restrictions, enable Autostart/Background Activity, and lock SolStream in Recents.
2. **Enable Assistive Broadcast Dock:**
  - **Play Store install:** Card 1 → **`3. Tap Accessibility 1st`** → Accept disclosure → switch **SolStream Studio ON**.
  - **Sideloaded GitHub APK (Android 13–18):** Tap greyed-out **SolStream Studio** once & OK → Card 1 **`4. Unlock Restricted (⋮)`** → tap **`⋮`** (or bottom of App Info on Xiaomi/POCO/Vivo) → **Allow restricted settings** → switch **ON**.

### Step B: Pre-Stream Dashboard (Cards 0–5)
- **Fixed Obsidian-Glass Header & 3-Tile Hero Bar:** Full-bleed **Solar Eclipse `'S'`** icon, 3 live studio chips (`SolLuxe 3.0` • `1440p60 • HEVC` • `Protected ✓`), visual **📖 User Guide**, `Copy/Import Preset`, **Long-Stream Protection** banner, and direct-tap `[ 🎥 Resolution ] [ ⚡ FPS ] [ 📡 Bitrate ]` tiles.
- **Card 0 — Smart Device Tier Presets (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`):** Syncs codec, resolution, bitrate, and voice HPF to your device tier and platform.
- **Card 1 — Pre-Flight Readiness:** Auto-collapses once all 4 checks are `READY`; shows live audio route and `5 GHz Wi-Fi + BT` coexistence status.
- **Card 2 — Studio Profiles, Key Vault & Simulcast (`SolRelay Omega™`):**
  - **4 One-Tap Studio Profiles (`Wi-Fi Max` | `Mobile Data` | `Shorts 9:16` | `Custom`):** Tap to apply hardware-clamped settings instantly; long-press any slot to save & rename your current setup or reset to default (Stream Keys are never saved inside profiles).
  - **4-Pill Platform Bar (`YouTube` | `Twitch` | `Kick` | `Custom`), AES-256 Hardware Keystore Vault, Collapsible Simulcast & 9:16 Shorts Drawer, and 5-Metric Network Analyzer.**
- **Card 3 — Video Engine (`SolCore Ultra™` — `360p–4K60` • `0.5–100M`):**
  - Direct-tap Resolution, Codec, FPS, and Framing (`FIT` / `STRETCH 16:9` / `SMART` / `FILL`) pills + stackable GPU filters.
  - **Collapsible Pro Overlays, Scenes & `SolCodec™` Drawer:** Configure **`SolHUD™`** (toggle + corner), **Offline Corner QR** (UPI/YouTube/Discord link + corner), **Zero-Camera 2-PNG PNGTuber** (pick Idle/Talk PNGs or use built-in Cyber Visor fallback + corner + `12–30%` size), custom Scenes/Logo/Text Banner, Dual Privacy Blur (`Minimap` + `Auto-Blur Keyboard`), and silicon controls (`OpenGL ES 3.0`, `HDR10`, `ROI`, `Keyframe/B-Frames/QP`).
- **Card 4 — `SolLuxe™ 3.0 Pure` Voice Studio & 3D Game Audio:**
  - 1-Tap Gender Profile (`Neutral 68Hz` | `Male 55Hz` | `Female 85Hz`), 6 Voice Presets (`SOL_LUXE`, `RADIO STUDIO`, `VELVET`, `CLEAN`, `NEURAL`, `RAW`), SeekBar volume sliders (`Mic 50–250%`, `Game 0–250%`), stackable 3D Game Soundstage, collapsible 6-Stage toggles, and **10-Second Studio Voice Test & Auto-Advisor**.
- **Card 5 — Launch Deck:** `GO LIVE` / 2-tap `STOP`, `Session Reports & History` (with `⚡ Recovered 60s` indicator), `★ Saved Clips & AI Highlights`, `Benchmark Other App`, `Refresh Mics / HW Scan`, and `Check GitHub Update`.

### Step C: In-Game 3-Tab Floating Pill & Safe Notification Shade
- **Top Quick Bar:** `[ Mic:ON ] [ Game:ON ] [ LIVE ] [ ★ Clip ] [ ✕ ]`
- **Tab 1 (`🎙️ Audio`):** Full live `SolLuxe™ 3.0 Pure` controls, volume steps, 3D Soundstage, and Mic switcher.
- **Tab 2 (`🎬 Video`):** Scene, Framing, Zoom, Upscaler, Sharpen, 180° Flip, Stackable Color Filters, unified `Logo:OFF` cycler, Banner, **`📸 Shot` (1-Tap Clean Thumbnail)**, **`HUD:OFF`**, **`QR:OFF`**, **`Avatar:OFF`**, Minimap Blur, and Keyboard Blur.
- **Tab 3 (`📡 Bitrate/Net`):** Live Bitrate steps, ABR toggle, `30FPS Cool` (with `SEVERE` live-temp toast protection), Stream/Game FPS, Queue/RTT, live **App vs. Game Heat/CPU & RAM Split**, target status, and 2-tap `⏹ Stop Stream`.
- **Safe Notification Bar (3 Actions Only):** `Mute Mic`, `Scene/BRB`, and `★ Clip` (no accidental Stop in shade).

---

## 6. For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
- **JDK, Gradle & Pinned NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + AGP `8.13.2` (R8 `9.5.26` for Kotlin `2.4.20`) + `compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`, `versionCode = 12`, `versionName = "1.5.0"` + **Pinned Android NDK `28.2.13676358`** (`CMake 3.22.1`, `-DANDROID_STL=none`, `ndk.debugSymbolLevel = "SYMBOL_TABLE"`).
- **CI Verification:** Fetches and verifies exact Xiph `RNNoise v0.1.1` commit **`6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`**, verifies **`zipalign -c -P 16 -v 4`**, `readelf -lW` `16 KB` ELF alignment across `arm64-v8a` and `x86_64`, `apksigner` V3 checks, strict Android Lint (`abortOnError = true`), and fresh un-cached execution (`--rerun-tasks`) of all **48 JUnit 4 unit tests** (`SolStreamUnitTests.kt`).

### The 6-Stage `SolLuxe™ 3.0 Pure` Vocal Architecture (`MicDspProcessor.kt`)

| Stage | Module Name | Exact Technical Specification (`48,000 Hz`, 64-Bit `Biquad64`, Atomic Frame-Boundary Config) |
|---|---|---|
| **Stage 1** | **64-Bit Butterworth HPF** | 2nd-order `Q = 0.707`: **`55 Hz` (`MALE`)**, **`68 Hz` (`NEUTRAL`)**, **`85 Hz` (`FEMALE`)** (`+5 Hz` `WIRED_USB_CLOSE`, `+10 Hz` `BLUETOOTH_SCO`, `+15 Hz` `SPEAKER_AEC`). Explicit manual Hz cutoffs (`hpfAutoMode = false`) are never overwritten. |
| **Stage 2** | **Raised-Cosine Micro-Impulse Clamper** | Tracks `20 ms` RMS; clamps only `<0.6 ms` spikes exceeding `3.8×` local RMS and recovers via a `2.0 ms` raised-cosine ramp so `10–30 ms` consonants stay crisp. |
| **Stage 3** | **Native C `RNNoise` + Hybrid `16 kHz`-Safe Neural-VAD Expander** | Stack-allocated `GetFloatArrayRegion` JNI (`480 samples`, `480-sample` dry delay, hardware `FZ` guards; `65% / 88% / 100%` wet). Hybrid trigger (`vadProb >= 0.35f` OR `200–2500 Hz` RMS `>= +6 dB` above floor), smooth `8.0 ms` attack / `220 ms` release, `-14 dB` floor (`-18 dB` speakerphone). |
| **Stage 4** | **Split-Band Dynamic Plosive Guard** | Causal `<115 Hz` monitor vs. `420 Hz (Q = 1.05)` vocal core; ducks only `<115 Hz` wind blasts up to `-9 dB`. |
| **Stage 5** | **4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth** | Non-overlapping `Biquad64` pockets: Chest (`95/130/172 Hz`), Mud Cut (`315/340/365 Hz`), Anti-Nasal (`920 Hz`), Silk Presence (`4.5/4.8/5.1 kHz`; or `2.9/3.2/3.5 kHz` in `RADIO STUDIO`), Air Shelf (`8.5/9.0 kHz`; or `5.2 kHz` LPF on `BLUETOOTH_SCO`), plus clean low-band analog warmth (`0.09 / 0.08 / 0.06f`). |
| **Stage 6** | **VAD-Synced Soft-Knee Leveler + Dynamic De-Esser + `4×` True-Peak Limiter** | Gender-matched HPF sidechain (`110/140/180 Hz`), `10 ms / 130 ms`, `6 dB` soft knee (`2.5:1` / `3.2:1` / `2.0:1`), VAD-synced quiet-speech lift + slow AGC, dynamic `6.2/6.7/7.2 kHz` De-Esser, Post-DSP Mic Boost (`50–250%`), and `4×` Catmull-Rom Inter-Sample True-Peak Limiter at **`-1.16 dBTP` (`0.875`)**. |

### Source Tree

~~~text
.
├── PRIVACY_POLICY.md                        # Public Google Play Console Privacy Policy, Data Deletion & Accessibility Disclosure
├── README.md                                # Complete v1.5.0 User Manual & Engineering Reference
├── .github/workflows/build.yml              # Pinned NDK 28.2.13676358, Pinned RNNoise Commit, 16KB zipalign + ELF CI
└── app/
    ├── build.gradle.kts                     # v1.5.0 (versionCode 12) + Pinned NDK + SYMBOL_TABLE + 16 KB JNI packaging
    ├── proguard-rules.pro                   # Explicit R8 @Keep rules for JNI, RNNoise, RootEncoder & Services
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Play-Store-clean permissions (zero battery/camera/storage/overlay bloat)
        │   ├── cpp/
        │   │   ├── CMakeLists.txt           # 16 KB page-aligned C build for Xiph RNNoise v0.1.1 + Bark-MMSE fallback
        │   │   └── sol_rnnoise_jni.c        # Zero-allocation stack JNI bridge + ARM/x86 FZ register guards + dry delay
        │   ├── res/
        │   │   ├── drawable/ic_solstream_logo.xml # Full-bleed Solar Eclipse 'S' Adaptive Vector Icon
        │   │   ├── drawable/ic_stat_stream.xml    # Monochrome notification status icon
        │   │   ├── values/strings.xml & styles.xml # Play Store Accessibility disclosure & Obsidian-Glass #050A14 Theme
        │   │   └── xml/accessibility_service_config.xml # isAccessibilityTool="false", canRetrieveWindowContent="false"
        │   └── java/com/solgaming/solstreamstudio/
        │       ├── MainActivity.kt          # Obsidian-Glass UI, 4 Studio Profiles, SolHUD/QR/PNGTuber UI, Visual Guide
        │       ├── SolStreamService.kt      # Adaptive Chunking, Severe Toast Guard, Live Avatar Sync, 60s Checkpoint, Pill
        │       ├── SolVideoPipeline.kt      # ES 3.0 Fast-FP16, SolHUD/QR/PNGTuber Compositor, Offscreen FBO Clean Screenshot
        │       ├── SolSceneAssetStore.kt    # Thread-safe Atomic Asset Store + Built-In Cyber Visor Avatar Generator
        │       ├── SolDualAudioEngine.kt    # AudioWorkerBarrier, 4-Tier PCM16/Resampler Fallback, Headphone-Aware AEC
        │       ├── MicDspProcessor.kt       # SolLuxe™ 3.0 Pure 6-Stage Engine, Atomic DspConfigSnapshot, 4x True-Peak Limiter
        │       ├── SolNativeRnnoise.kt      # Safe Kotlin JNI wrapper for libsol_rnnoise.so
        │       ├── CodecCapabilityProbe.kt  # eligibleCompanionEncoders(), 5000ms Scan Gate, Global SoC/GPU Mapper
        │       ├── KeystoreVault.kt         # Non-Destructive AES-256-GCM Vault, Schema v5, 4 Studio Profiles, Clamping
        │       ├── DiagnosticsLogger.kt     # Frozen Post-Stop v1.5.0 Reports, Single-Slot 60s Checkpoint & ★ Saved Clips
        │       └── recorder/
        │           └── SolHudAndQrRenderer.kt # Pure-Kotlin ISO QR (V1–10 M), Zero-GC SolHUD, Corner MVP & MediaStore Saver
        └── test/java/com/solgaming/solstreamstudio/
            └── SolStreamUnitTests.kt        # 48-test JUnit 4 verification suite (DSP, QR, MVP, FBO, Profiles, Thermal, RTMP)
~~~

### Build & Release Verification Command

~~~bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37,37.0,38,38.0 --no-daemon --stacktrace
~~~

---

## 7. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK`, **ALWAYS** call `startForeground()` before any early return in `startLiveBroadcast()`, **ALWAYS** await `activeTeardownLatch` off the main thread (`5s` timeout), and **ALWAYS** call `DiagnosticsLogger.lockFinalMicStatusBeforeStop()` + `videoPipeline.prepareForImmediateStop()` before async teardown.
2. **NEVER** call `glReadPixels()` directly on a `MediaCodec` `InputSurface` (use a temporary offscreen RGBA8 FBO and restore `glBindFramebuffer(0)` + `glViewport` in `finally`), and **NEVER** allocate new `Bitmap` objects per frame inside the OpenGL loop (`SolHUD™` must reuse its scratch bitmap via `glTexSubImage2D`).
3. **NEVER** mutate `Biquad64` coefficients from the UI thread—publish an immutable `DspConfigSnapshot` atomically at `10 ms` frame boundaries.
4. **NEVER** enable `setCheckServerAlive(true)`, **NEVER** release audio resources before `AudioWorkerBarrier` confirms both workers exited, and **NEVER** allow `VOICE_COMMUNICATION` / hardware AEC when headphones are connected.
5. **NEVER** re-introduce `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`, `SYSTEM_ALERT_WINDOW` (prior to Phase 2 Offline Recorder), `AudioManager.setCommunicationDevice()`, `-ffast-math`, or CPU software video encoders.

---

## 8. Privacy Policy, Google Play Compliance & Open-Source Licenses

- **Privacy Policy & Zero Data Collection:** See [**`PRIVACY_POLICY.md`**](./PRIVACY_POLICY.md) for our full Google Play Store Privacy Policy, Data Deletion controls, and `AccessibilityService` disclosure (`isAccessibilityTool="false"`, `canRetrieveWindowContent="false"`, `canPerformGestures="false"`).
- **Created & Maintained By:** **Sol Studio (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
- **Dual Licensed:**
  - **Free for Gamers, Streamers & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)**.
  - **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
- **Open-Source Credits:** Encrypted RTMPS transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`) and neural voice denoising uses [`xiph/rnnoise`](https://github.com/xiph/rnnoise) (`BSD-3-Clause`).
