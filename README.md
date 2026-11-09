# SolStream Studio • ULTRA (`v1.1.2`)


**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (~3.5 MB), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio powered by the custom **`SolCore™ Direct-TBDR Video Engine`** and **`SOL LUXE™ 15-Stage Audio Engine`**. It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 15-stage 32-bit float vocal commentary**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, and Kick** over encrypted **RTMPS**.

> **100% Unrooted Stock Android:** Works on normal, unrooted Android 10–17+ phones (`minSdk 29`) right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on 100+ Mbps Fiber. Root access is **never** required or used.

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolCore™` + `SOL LUXE™`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0/2.0 Direct-TBDR frame pacer, HUD-safe aspect scaler, 6-stage GPU visual filter pipeline, isolated 15-stage 32-bit float / 64-bit double vocal & game soundstage DSP, sub-millisecond multi-strike spark/hammer impulse blanker, `16 KB`-aligned Native C `48 kHz RNNoise` engine, drift-locked stereo game circular buffer, 4x cubic inter-sample look-ahead limiter, async hardware encoder capability scanner, AES-256 Keystore multi-slot vault, and 0-Hz sleeping 3-tab Accessibility Ghost Pill are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini and ChatGPT!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, and `assembleRelease`) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK (`v1.1.2`)](#1-download--install-signed-release-apk-v112)
2. [How Human + Dual-AI Built This App](#2-how-human--dual-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-ultra-actually-do)
4. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#4-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
5. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#5-global-broadcast-inspirations-windows-macos--ios---android)
6. [For Android Developers & Engineers: Architecture, 15-Stage Audio & 6-Stage Video](#6-for-android-developers--engineers-architecture-15-stage-audio--6-stage-video)
7. [For AI Coding Assistants, Security Auditors & Forkers](#7-for-ai-coding-assistants-security-auditors--forkers)

---

## 1. Download & Install Signed Release APK (`v1.1.2`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.1.2.apk`** under **Assets** to download the full **Ultra Master Edition** directly onto your Android phone (or download `SolStream-Studio-v1.0.2.apk` if you prefer the classic lightweight edition—all versions remain permanently available in Releases!).
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.1.2`** straight over `v1.0.1`, `v1.0.2`, `v1.1.0`, or `v1.1.1` as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. How Human + Dual-AI Built This App

This repository was engineered through a three-way verification loop between a competitive mobile gamer and two frontier reasoning models checking each other's blind spots:

* **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  * Defined real-world competitive constraints and validated builds across **multiple 3-to-4.5 hour (`168m–273m`) continuous `1440p60 HEVC` Multiplayer and Battle Royale live streams** on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e`, `2340×1080` 120 Hz display, 8 GB RAM, Bypass Charging ON), **realme Buds Wireless 5 ANC** neckband earphones, and 5 GHz Fiber Wi-Fi (`0` dropped frames, `0` reconnects).
  * Identified real-world Battle Royale thermal hysteresis, router silent-drop (`sent=0kbps`) stalls, physical RAM carveout mapping, and multi-strike background impulse noise (mosquito-racket zaps & neighborhood construction) from live sessions.
* **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  * Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c`), `SOL LUXE™` 15-stage 32-bit float / 64-bit double audio studio (`MicDspProcessor` + `SolDualAudioEngine`), `SolCore™` 6-stage OpenGL ES 3.0/2.0 Direct-TBDR visual pipeline (`SolVideoPipeline`), zero-permission scene/overlay store (`SolSceneAssetStore`), async multi-encoder hardware scanner (`CodecCapabilityProbe`), hardware Keystore vault (`KeystoreVault`), 3-tab Accessibility Ghost Pill (`SolStreamService`), and GitHub Actions NDK CI/CD pipeline.
* **Independent Spec Audit & Edge-Case Verification — `OpenAI ChatGPT`:**
  * Audited code paths across multiple release tags against Android 10–17 AOSP rules, per-session RTMP callback identity, EGL + `VirtualDisplay` startup latches, paired resolution+FPS encoder probing, Keystore encryption verification, atomic staging file rollback, thread-safe shutdown, and a 16-test fresh JUnit verification suite.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `45–160 MB` of camera masks, AdMob ads, and coin timers; lock `1440p/HEVC` behind expensive subscriptions; ruin Bluetooth stereo footsteps when you use squad voice chat; or slap limiters across mixed audio that make gunshots sound underwater.

**SolStream Studio Ultra solves every one of those problems in a ~3.5 MB, unrooted, zero-ad studio:**

1. **5 Smart Device Tiers (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`) + 100% Manual Freedom:**
   * Automatically scans your phone's physical RAM tier (`4–24 GB`, accounting for kernel GPU/modem carveouts), display refresh rate (`60–185 Hz`), OpenGL ES version, and hardware encoders (`HEVC / H.264 / AV1`) in the background, highlights your phone's recommended tier, and leaves every single button unlocked for manual customization.
2. **Plays at 90–185 FPS on Your Screen, Streams Smoothly at 60/30 FPS (`360p` to `4K60` • `0.5` to `100 Mbps`):**
   * Your phone display stays at its native `120 / 144 / 165 / 185 Hz` refresh rate while dedicated hardware video silicon (`HEVC / H.264 / HW AV1`) encodes `360p`, `480p`, `720p`, `1080p`, `1440p`, or `4K (2160p)` from `0.5 Mbps` (mobile data saver) up to `100 Mbps` (ultra fiber).
3. **`SolCore™ Direct-TBDR` Video Engine & Battle Royale Cool-Run Armor:**
   * **Zero-Copy On-Chip Tile Rendering:** Renders directly to the hardware encoder surface (`Framebuffer 0`) so mobile GPUs keep pixel data inside ultra-fast on-chip Tile SRAM with zero off-screen FBO RAM bloat and zero quality capping.
   * **Steady 60 FPS Cadence + 2-Second Wall-Clock Keyframe Lock:** Feeds steady frames to hardware `CBR` encoders so bitrates never overshoot and YouTube always receives a keyframe every `2.0 seconds`—even during `Starting Soon`, `BRB`, or `30 FPS` Thermal Cooldown.
   * **Game-First Silicon Priority (`KEY_PRIORITY = 1`) + 60s Thermal Cooldown Hysteresis:** Gives your game first priority on the SoC memory bus and steps stream pacing to `30 FPS` if the phone reaches Android `Severe` thermal status, holding for 60 seconds after cooling to `Moderate` so frame rates never bounce back and forth.
4. **`SOL LUXE™` 15-Stage Voice Studio + `16 KB` Native C `48 kHz RNNoise` + Spark/Hammer Blanker:**
   * **Pick-1 Voice Profile (`Neutral` | `Male` | `Female`):** Tunes 64-bit `Double` chest warmth (`115 / 135 / 165 Hz`), mud cut (`310 / 345 / 400 Hz`), callout presence (`3.5 / 3.8 / 4.2 kHz`), and split-band De-Esser (`6.2 / 6.6 / 7.2 kHz`) specifically to your vocal cords.
   * **Sub-Millisecond Multi-Strike Spark & Hammer Blanker:** Freezes baseline adaptation during rapid bursts of 3–6 mosquito-racket zaps (*"phat-phat-phat"*), construction hammering, or finger taps—clamping non-vocal impulses by up to `92%` in `0.02 ms` and requiring consecutive vocal-cord harmonics before opening the speech gate.
   * **6 Broadcast Voice Types (`RAW` | `CLEAN` | `NEURAL` | `RADIO STUDIO` | `VELVET` | `SOL LUXE`) + 10 Individual Stage Switches:** Phase-aligned Native C `48 kHz RNNoise`, 64-bit HPF, Spark/Hammer & Grip Blanker, Breath Expander, 3-Band EQ, Split-Band De-Esser, Velvet Tube Warmth & Air, Whisper Callout Lift, 3:1 Compressor, Speech-Gated Slow AGC, and Optional Speaker `AEC`.
   * **10-Second Guided CODM Grip Calibrator & Auto-Advisor:** Measures 3 seconds of silent two-hand grip rumble/fan floor + 7 seconds of normal/loud callouts, then auto-recommends your ideal Mic Boost %, HPF Hz, and `RNNoise` level in 1 tap.
5. **Up to `250%` Game Mix + Stackable 3D Stereo Game Soundstage:**
   * Boosts quiet internal game capture up to **`250%`** safely through our 64-sample look-ahead `4x` Cubic True-Peak Limiter (`-1.16 dBTP`), with 4 stackable stream-only soundstage enhancers: **`Cinema 3D Wide`**, **`Theater Sub-Punch`**, **`Footstep Focus`**, and **`Late-Night Blast Tamer`** (never alters what you hear in your own earphones!).
6. **6-Stage Stackable GPU Visual Filters, Custom Scenes, `.PNG` Overlays & `9:16` Shorts Live:**
   * Mix and match **`Cinematic Filmic`**, **`AMOLED Vivid Pop`**, **`Shadow Boost` (Dark-Corner Enemy Clarifier)**, **`Color Temp` (`Neutral / Warm / Cool`)**, **`GPU Crisp Sharpen`**, **`Bilinear / Bicubic / Lanczos`** upscalers, **`True 10-Bit HDR10 (HLG)`**, and **`Hardware ROI`**.
   * Pick custom **`Starting Soon`**, **`BRB`**, and **transparent `.PNG` Stream Overlays** with **zero Storage permissions**, add a **Built-In GPU Live Text Banner**, blur your minimap/room code with **Stream-Sniper Shield**, and stream to the **YouTube Shorts Live Feed (`9:16` Blurred Canvas)** or **Simulcast to YouTube + Twitch + Kick at once**!

---

## 4. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Android 13–17 & Earphone Setup (2 Minutes)

1. **Unlock Restricted Accessibility (Why the `⋮` 3-Dots Hide at First on Android 13–17):**
   * Because you install the APK directly from GitHub Releases rather than Google Play, Android 13–17 hides the `Allow restricted settings` menu until you tap the greyed-out Accessibility switch once!
   * In Card 1, tap **`3. Tap Accessibility 1st`** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK** on the popup.
   * Return to SolStream Studio and tap **`4. Unlock Restricted (⋮)`** (teleports straight into SolStream's App Info screen) → tap **`⋮`** in the top-right corner → tap **Allow restricted settings** and confirm your PIN/fingerprint.
   * Tap **`3. Tap Accessibility 1st`** again and toggle **SolStream Studio** **ON**! *(Future signed updates keep this unlocked forever.)*
2. **Earphones Setup (Bluetooth Neckbands, AirPods, TWS, Wired 3.5mm & USB-C):**
   * **Wired 3.5mm & USB-C Earphones/Headsets:** Plug and play in full zero-latency stereo with your choice of inline headset mic, USB mic, or phone mic.
   * **Bluetooth Neckbands & AirPods (`realme Buds`, `AirPods`, etc.):** Turning on any Bluetooth headset's built-in call mic forces Bluetooth into muffled mono call mode (`HFP`). To keep **full stereo footsteps + low-latency Game Mode** in your Bluetooth earphones while using your phone's crisp **48 kHz studio mic**: open **Bluetooth Settings → Earphones Gear ⚙️ → toggle `Calls` OFF** (keep `Audio` **ON**).
   * **Samsung Separate App Sound Tip:** Open *Samsung Settings → Sounds and vibration → Separate app sound* → lock *Call of Duty* to your Bluetooth device so match transitions never jump to the phone speaker.
3. **Single-App Privacy Capture:**
   * When Android's *"Start recording or casting?"* popup appears, select **"A single app" → Call of Duty** so WhatsApp messages, phone calls, and OTP notifications never appear on your live stream!

---

### Step B: Pre-Stream Dashboard (Every Card, Button & Collapsible Drawer)

#### Top Utility Bar
* **`? Plain-English Guide & Glossary`:** Opens an in-app dictionary explaining every video, audio, and network term in plain English.
* **`Copy Preset` / `Import Preset`:** Copies or imports your complete video/audio configuration as JSON (Stream Keys are **never** included, so presets are 100% safe to share with friends).

#### Card 0 — Smart Device Tier Presets (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`)
* Displays your phone's hardware scan recommendation (`Scanner Recommended for [Device]: Flagship Tier`) with a cyan highlight. Tap any tier to load its recommended resolution, FPS, bitrate, upscaler, and voice preset—then freely adjust any individual button below.

#### Card 1 — Pre-Flight Readiness & Direct Settings Setup
* **`1. Grant Mic & Notif` / `2. Unrestricted Batt`:** Grants Microphone/Notification permissions and exempts the stream from background battery throttling.
* **`3. Tap Accessibility 1st` / `4. Unlock Restricted (⋮)`:** Exact 1-2 sequence to unlock and enable the floating in-game pill and simultaneous squad-mic sharing.
* **`Samsung One UI, AirPods/Neckband & BR Cooling Guide`:** Quick reference checklist for earphones and 4-hour Battle Royale thermal tips.

#### Card 2 — Encrypted RTMPS Vault, `9:16` Shorts Key & Simulcast
* **`Platform Preset` (`YOUTUBE` | `TWITCH` | `KICK` | `CUSTOM`):** Auto-fills the official RTMPS ingest URL and selects platform-compatible codec rules.
* **`Primary Stream Key` (`Paste Key` | `Show Key` | `Clear Key` | `Save Vault`):** Encrypts your key inside Android's hardware-backed Keystore (`AES-256-GCM`) and verifies that encryption + storage succeeded before confirming.
* **`Get Key on Phone (YT Studio)` / `Open YouTube App`:** Lets phone-only streamers grab their permanent Default Variable Stream Key from `studio.youtube.com` without a PC and jump to the YouTube app in 1 tap.
* **`▼ Expand: 9:16 Shorts Key Slot, Twitch/Kick Simulcast & Dual-Format`:**
  * **Slot #2 (`9:16` Variable Shorts Key):** Save a Default/Variable Resolution YouTube Stream Key (*"Turn on manual settings"* left OFF in YT Studio). Whenever you switch Card 3 to **`9:16 Vertical Shorts`**, YouTube automatically places your vertical stream straight into the **YouTube Shorts Live Feed** without needing Google account login!
  * **`Simulcast to Twitch` & `Simulcast to Kick`:** Encodes video **once** on your hardware chip (`H.264 CBR`) and fans out packets simultaneously to YouTube + Twitch + Kick with near-zero extra phone heat.
  * **`Dual-Format 16:9 + 9:16 Simultaneous Companion`:** Opt-in mode for `Flagship / Ultra` phones with 2-instance hardware encoders to stream `16:9` Landscape and `9:16` Shorts simultaneously from a single screen capture.
* **`Test Internet RTMPS Ping & Upload`:** Verifies DNS, TLS 1.2/1.3 SNI handshake, idle RTT/jitter, sustained upload Mbps, and loaded router bufferbloat.

#### Card 3 — `SolCore™` Video Engine (`360p–4K60` • `0.5–100 Mbps` • Stackable Visual Filters & Scenes)
* **`Codec` (`HEVC` | `H264` | `AV1`), `Res` (`360p` | `480p` | `720p` | `1080p` | `1440p` | `4K`), `Stream` (`60 FPS` | `30 FPS`):** Hardware-only encoding (`360p` and `480p` automatically align to your chip's macroblock boundaries so budget chips never fail).
* **`Format` (`16:9 Wide 0°/180°` | `9:16 Vertical Shorts`):** Flip `180°` live while in landscape so your USB-C charging cable rests comfortably between your fingers, or select `9:16 Vertical Shorts`.
* **`− Mbps` / `Target` (`0.5 Mbps` to `100 Mbps`) / `+ Mbps`, `Rate` (`CBR` | `VBR`), `Ping Guard ABR 2.0`:** Fine-tune bitrate across 24 steps with dynamic-floor congestion protection (`0.4 Mbps` floor for `360p/480p` mobile data up to `100 Mbps` fiber) and a 6-second Zombie-Socket auto-reconnect watchdog.
* **`Upscaler` (`BILINEAR` | `BICUBIC` | `LANCZOS`), `Framing` (`FIT` | `SMART` | `FILL`), `Zoom` (`104%–120%`):** `FIT` keeps 100% of your CODM HUD visible; `SMART` trims thin side borders on `20:9` phones.
* **Stackable Live Visual Filters (Combine Any 1, 2, 3, or All Live):**
  * **`Cinematic Filmic`:** ACES-inspired S-curve contrast that stops bright flashbangs and skies from blowing out while deepening shadow contrast.
  * **`AMOLED Vivid Pop`:** Boosts muted mid-tone colors without clipping red HUD icons.
  * **`Shadow Boost (Dark Corners)`:** Gently lifts dark indoor corners (`2%–15%` luma) so viewers spot enemies in dark rooms clearly.
  * **`Color Temp` (`NEUTRAL` | `WARM` | `COOL`) & `GPU Crisp Sharpen`:** Contrast-adaptive edge sharpener (keep `Sharp` **OFF** during 4-hour Battle Royale streams for the coolest phone temperature).
* **`▼ Expand: Custom Scenes, .PNG Overlay, Text Banner & Pro Silicon`:**
  * **Zero-Permission Graphic Pickers (`Starting Soon Img` | `BRB Privacy Img` | `.PNG Overlay` | `9:16 Poster`):** Pick images from your gallery via Android's system Photo Picker (saved atomically with staging-file rollback in private app storage with zero Storage permissions).
  * **Built-In GPU Live Text Banner:** Type any goal or handle (e.g., `SUB GOAL 75 • @Sol_CODM`), toggle **ON**, and position at **`TOP`** or **`BOTTOM`** (`0%` per-frame CPU redraw cost).
  * **`9:16 Shorts Canvas` (`BLUR` | `DARK` | `POSTER`):** Fills top/bottom vertical space with a frosted live gameplay blur, dark obsidian bars, or your custom poster.
  * **`Stream-Sniper Minimap Blur Shield`:** Blurs your minimap or room code corner (`TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`) **only on the outgoing stream** while your own screen stays 100% clear.
  * **Pro Silicon Controls:** `OpenGL Engine` (`AUTO` | `ES30` | `ES20`), `True 10-Bit HDR10 (HLG)`, `Hardware ROI Focus` (`AUTO` | `CENTER` | `HUD` | `OFF`), `IDR Keyframe` (`1s / 2s`) + `B-Frames (0/1/2)`, and `QP Max Cap`.

#### Card 4 — `SOL LUXE™` 15-Stage Voice Studio, `250%` Game Soundstage & 10s Calibrator
* **`Mic Input`, `Source` (`AUTO` | `MIC` | `VOICE_REC` | `VOICE_COMM`), `Master AAC` (`64–320 kbps`):** Selects microphone hardware and stereo AAC bitrate.
* **Voice Profile (`Neutral` | `Male Voice` | `Female Voice` — Pick 1):** Tunes 64-bit vocal EQ and split-band De-Esser frequencies to your voice.
* **`Voice Type` (`RAW` | `CLEAN` | `NEURAL` | `RADIO STUDIO` | `VELVET` | `SOL LUXE`) & `Denoiser` (`Native C 48kHz RNNoise` | `Light Cut` | `Chip HW NS` | `OFF`):** Selects your vocal preset and single active noise suppressor (`Lv1 Gentle 65%`, `Lv2 Natural 88%`, `Lv3 Max 100%`).
* **`Mic Boost` (`75%–250%`), `Game Mix` (`0%–250%`), `VAD Ducking` (`OFF` | `-20%` | `-35%` | `-50%`), `Mic Sync` (`0–160 ms`):** Balances commentary against gameplay (protected by the `4x` Cubic Look-Ahead Limiter).
* **Stackable Stereo Game Soundstage (Combine Any 1–4 Live — Stream-Only):**
  * **`Cinema 3D Wide`:** Phase-safe Mid-Side spatial matrix that widens `>200 Hz` stereo footsteps/ambience around your dead-center voice.
  * **`Theater Sub-Punch` (`+1.8 dB @ 65 Hz`)**, **`Footstep Focus` (`+2.0 dB @ 2.85 kHz`)**, and **`Blast Tamer`** (softens sudden loud explosion spikes for late-night headphone viewers).
* **`▼ Expand: Individual Voice Filter Switches (Mix & Match All 10 Stages)`:** Independently toggle `(1) HPF Rumble`, `(2) Spark/Hammer & Grip Blanker`, `(3) Idle Breath Expander`, `(4) 3-Band EQ`, `(5) Split-Band De-Esser`, `(6) Velvet Warmth & Air`, `(7) 3:1 Vocal Compressor`, `(8) Whisper Callout Lift`, `(9) Speech-Gated Slow AGC`, and `(10) Speaker Echo Canceler (AEC)`.
* **10-Second Guided CODM Grip Calibrator (`Record 10s Test` | `Play Test` | `Delete` | `Apply Advisor Recommendation`):** Hold your normal 2-hand CODM grip silently for 3s, speak callouts for 7s, and tap **`Apply Advisor Recommendation`** to set your calibrated Gain, HPF, and `RNNoise` level automatically.

#### Card 5 — Launch Broadcast, Report History, App Benchmark & Creator Support
* **`GO LIVE — START STREAM`:** Verifies Keystore encryption, runs silent-volume guard, requests screen capture, and launches the broadcast.
* **`Session Reports & History`:** View and copy your current privacy-redacted Session Report (including **`★ Saved Stream Clip Highlights`** and **`SoC Thermal Load %`**), browse your **Archived Past Reports (Last 10 Streams across days)**, or delete history when you choose.
* **`Benchmark Other Stream App`:** Passively benchmarks battery temperature, SoC thermal status, and RAM while running **Glip, PRISM Live Studio, Streamlabs, Zero Lag, or Turnip** for side-by-side comparison.
* **`❤️ Enjoying SolStream? Subscribe to @Sol_CODM to Support!`:** 1-tap voluntary support link to subscribe to [`@Sol_CODM`](https://youtube.com/@Sol_CODM) on YouTube.

---

### Step C: In-Game 3-Tab Floating Pill (`🎙️ Audio` | `🎬 Video` | `📡 Bitrate/Net`)

While playing CODM, the floating Ghost Pill sleeps at **38% opacity** with **0 Hz UI redraws** so it never blocks your HUD or steals frame time. **Double-tap** (or long-press) the pill to open the compact 3-tab studio:

* **Live Telemetry Header:** Shows `HH:MM:SS | Out Mbps | Fresh/Encoded FPS | Queue (Q:0f or Q:N/A) | Battery °C + SoC Thermal Load %`.
* **Top Quick-Action Bar (Always Visible):**
  * **`Mic:ON / MUTE`:** Instant anti-click stream mic mute (your CODM squad still hears you!).
  * **`Game:ON / MUTE`:** Instant stream game-audio mute.
  * **`Scene:LIVE / BRB / START`:** Switches between Live Gameplay, `BRB Privacy Shield`, and `Starting Soon` (automatically stepping to `30 FPS / 1.5 Mbps` with 2-second keyframes to cool your phone between matches).
  * **`★ Clip`:** Saves the exact `HH:MM:SS` stream timestamp into your Session Report so you can cut YouTube Shorts in seconds after your stream ends.
  * **`✕`:** Collapses back to the sleeping Ghost Pill.
* **Tab 1 (`🎙️ Audio`):** Switch `Neutral / Male / Female` profile, `SOL LUXE` voice types, `RNNoise` level, `HPF`, `EQ + DeEss + Velvet + AGC`, `Mic Boost (75–250%)`, `Game Mix (0–250%)`, `Cinema 3D Wide + Sub-Punch + Footsteps + Blast Tamer`, `VAD Ducking`, and debounced live `Mic Hardware` input.
* **Tab 2 (`🎬 Video`):** Cycle `LIVE / STARTING SOON / BRB` scenes, `Bilinear / Bicubic / Lanczos` upscaler, `FIT / SMART / FILL` framing, stackable `Cinematic Filmic + AMOLED Vivid + Shadow Boost + Color Temp`, `0°/180°` USB-C flip, `GPU Sharp`, and `.PNG Overlay / Live Banner / Minimap Blur Shield`.
* **Tab 3 (`📡 Bitrate/Net`):** Step live bitrate down/up (`0.5–100 Mbps`), toggle `Ping Guard ABR 2.0`, toggle **`30 FPS Cooldown Saver`** (instant 50% GPU heat reduction), view multi-platform Simulcast/Companion status, or 2-tap **`Stop Stream`**.

---

## 5. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Ultra Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **Isolated 15-Stage `SOL LUXE™` 32-Bit Float / 64-Bit Double Audio Bus** | OBS Studio, Reaper DAW & Elgato Wave Link | Captures mono `PCM_FLOAT` mic separately from stereo `AudioPlaybackCapture` with 64-bit `Double` recursive biquads so voice filters never pump or muddify game audio. |
| **Phase-Aligned Native C `48 kHz RNNoise`** | Xiph.Org RNNoise & Shure MVX2U DSP | Compiles official Xiph `RNNoise` in C with `16 KB` ELF alignment (`libsol_rnnoise.so`) and a 480-sample dry delay line so wet/dry neural blending (`65% / 88%`) stays 100% phase-coherent. |
| **Sub-Millisecond Multi-Strike Spark & Hammer Blanker** | Broadcast Vinyl De-Clicker / Radar Impulse Blanker | Detects high-slew non-vocal transients (`>3.4x` slew without `220 Hz` vocal-cord fundamental), freezes baseline adaptation across multi-zap mosquito-racket bursts (*"phat-phat-phat"*), and clamps impulses by up to `92%` in `0.02 ms`. |
| **Gender-Tuned `SOL LUXE (Studio + Velvet)` Chain** | Neumann / Aphex 230 Broadcast Voice Channel | Tunes fundamental chest warmth (`115/135/165 Hz`), mud cut, split-band De-Esser, DC-blocked even-order harmonic warmth, upward whisper lift, and speech-gated slow AGC. |
| **Stackable `Cinema 3D Wide` Game Soundstage** | Dolby Atmos for Gaming & Sennheiser GSX | Mid-Side (`M/S`) spatial matrix widens `>200 Hz` stereo cues by `+26%` while anchoring `<200 Hz` bass center—keeping your voice dead-center and preserving 100% mono speaker compatibility. |
| **`4x` Cubic Look-Ahead True-Peak Limiter (`-1.16 dBTP`)** | FabFilter Pro-L2 & EBU R128 Loudness Standard | Evaluates Catmull-Rom inter-sample peaks (`0.25 / 0.50 / 0.75`) across a 64-sample window with TPDF dither so `250%` Game Mix + loud callouts never clip. |
| **6-Stage Single-Pass `SolCore™ Direct-TBDR` OpenGL ES 3.0 Shader** | Desktop OBS + BenQ Zowie Black eQualizer + AMD CAS | Combines `Bicubic/Lanczos` scaling, `Shadow Boost` dark-corner lift, `ACES Filmic` S-curve, `AMOLED Vivid`, and contrast-adaptive sharpening in one direct Tile-SRAM GPU pass. |
| **Dynamic-Floor Queue-Aware ABR 2.0 + 6s Zombie-Socket Watchdog** | CAKE / FQ-CoDel SQM Bufferbloat Control | Caps outbound packet queue to 120 frames, rejects unavailable (`-1`) telemetry, and forces a clean off-thread Primary/Backup reconnect if `sent=0kbps` with a backed-up queue persists for 6 seconds. |

---

## 6. For Android Developers & Engineers: Architecture, 15-Stage Audio & 6-Stage Video

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
* **JDK, Gradle & NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + Android Gradle Plugin (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`) + Android NDK r28+ (`CMake 3.22.1`, `-DANDROID_STL=none`, `-O3 -std=gnu11`).
* **16 KB Page Alignment, Fresh JUnit & Strict Lint:** Enforces `-Wl,-z,max-page-size=16384` in `CMakeLists.txt` and `packaging.jniLibs.useLegacyPackaging = false`, verified on every CI run via `readelf -lW` (`0x4000` alignment) alongside `apksigner` V3 certificate verification, strict Android Lint (`abortOnError = true`), and fresh un-cached execution (`--rerun-tasks`) of all **16 JUnit 4 unit tests** (`SolStreamUnitTests.kt`).

### Heterogeneous Hardware Offloading Across 5 Phone Subsystems
1. **Display Processing Unit (`DPU`):** Zero-copy `VirtualDisplay` surface capture + dynamic `onCapturedContentResize()`.
2. **Mobile GPU (`SolCore™ Direct-TBDR` OpenGL ES 3.0 / 2.0):** Single-pass spatial scaling, color grading, and premultiplied-alpha `.PNG` / banner compositing directly to `Framebuffer 0`.
3. **Dedicated Media Silicon (`Exynos MFC` / `Snapdragon VideoCore` / `Mali VPU`):** Hardware-only `HEVC`, `H.264`, `AV1`, and `AAC-LC` encoding (`KEY_PRIORITY = 1`).
4. **CPU Efficiency Cores (`ADPF` + Native C NEON):** Zero-allocation 15-stage `SOL LUXE™` audio mixer and C `RNNoise` reporting `10 ms` work durations to `PerformanceHintManager`.
5. **Hardware Security Module (`TEE / StrongBox`):** `AndroidKeyStore` `AES/GCM/NoPadding` 256-bit stream key encryption with verified commit status.

### Source Tree
```text
app/src/
├── main/
│   ├── AndroidManifest.xml                  # Minimal unrooted permissions (zero Camera, Storage, Contacts, or SAW)
│   ├── cpp/
│   │   ├── CMakeLists.txt                   # 16 KB page-aligned C build for Xiph RNNoise v0.1.1 + Bark-MMSE fallback
│   │   └── sol_rnnoise_jni.c                # Zero-allocation JNI bridge with 480-sample phase-aligned dry delay line
│   ├── res/
│   │   ├── drawable/ic_solstream_logo.xml   # 3-layer Adaptive Icon (Background, Foreground, Android 13+ Monochrome)
│   │   ├── drawable/ic_stat_stream.xml      # Notification status icon
│   │   ├── values/strings.xml & styles.xml
│   │   └── xml/accessibility_service_config.xml
│   └── java/com/solgaming/solstreamstudio/
│       ├── MainActivity.kt                  # 5-Tier ULTRA Dashboard, Async Scan Guard, Glossary, Photo Pickers, Reports
│       ├── SolStreamService.kt              # 3-Tab Floating Pill, Per-Session RTMP Callbacks, ABR 2.0, 60s Thermal Hysteresis
│       ├── SolVideoPipeline.kt              # SolCore Direct-TBDR OpenGL ES 3.0/2.0, 360p-4K60, Stackable Filters, HDR10, ROI
│       ├── SolSceneAssetStore.kt            # Atomic staging-file Starting Soon, BRB, .PNG Overlay & GPU Text Banner store
│       ├── SolDualAudioEngine.kt            # 250% Game Soundstage, 4x Cubic Limiter, 10s Guided Grip Calibrator
│       ├── MicDspProcessor.kt               # 10-Stage 64-bit Double Vocal DSP (SOL LUXE + Multi-Zap Spark/Hammer Blanker)
│       ├── SolNativeRnnoise.kt              # Safe Kotlin JNI wrapper for libsol_rnnoise.so
│       ├── CodecCapabilityProbe.kt          # Non-blocking paired (Res+FPS) 2-frame EGL hardware scanner & 5-Tier classifier
│       ├── KeystoreVault.kt                 # Verified Hardware AES-256-GCM multi-slot key vault & JSON preset share
│       └── DiagnosticsLogger.kt             # Privacy-redacted telemetry, ★ Mark Clip recorder & Thermal/SoC/PSS profiler
└── test/java/com/solgaming/solstreamstudio/
    └── SolStreamUnitTests.kt                # 16-test JUnit 4 verification suite
```

### Build & Release Verification Command
```bash
gradle testDebugUnitTest --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37 --no-daemon --stacktrace
gradle lintDebug assembleDebug assembleRelease -Pandroid.suppressUnsupportedCompileSdk=36,37 --no-daemon --stacktrace
```

---

## 7. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`.
2. **NEVER** call `mediaProjection.createVirtualDisplay(...)` more than once per consent token—adapt size via `onCapturedContentResize()` + `virtualDisplay.resize(...)`, share the single `SurfaceTexture` across primary and optional `9:16` companion surfaces, and render `Starting Soon / BRB` slates inside `SolVideoPipeline.runGlRenderLoop`.
3. **NEVER** allocate heap objects inside `MicDspProcessor.processMonoFrameInPlace`, `SolNativeRnnoise.processMono480InPlace`, or per-frame audio/OpenGL loops—preserve zero-GC steady state for 6+ hour marathon sessions.
4. **NEVER** stack multiple stationary noise suppressors simultaneously (`RNNoise`, `Light Cut`, and `Chip HW NS` are mutually exclusive in `MicDspProcessor` / `SolDualAudioEngine`), and **NEVER** allow `Slow AGC` to boost gain when `confirmedSpeech == false`.
5. **NEVER** introduce `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `SYSTEM_ALERT_WINDOW`, `AudioManager.setCommunicationDevice()`, `-ffast-math` in `CMakeLists.txt` (blocked by Xiph `arch.h`), or software video encoders (`c2.android.av1.encoder`).

### Privacy & Security Auditability
* **Zero Telemetry / Zero Ads / No Root:** Connects *only* to your configured RTMPS servers (plus `speed.cloudflare.com` and `api.github.com` strictly when you manually tap the network test or update buttons).
* **Stream Key Protection:** Encrypted in `AndroidKeyStore` (`AES/GCM/NoPadding`), verified on commit, redacted via regex in `DiagnosticsLogger`, excluded from Preset JSON exports, and stripped from Release Logcat via `proguard-rules.pro`.
* **Minimal Accessibility Scope:** `canRetrieveWindowContent="false"`—used strictly to host `TYPE_ACCESSIBILITY_OVERLAY` and satisfy Android's foreground microphone-sharing policy without reading screen text.

---

## License & Commercial Protection

* Created & maintained by **Sol (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
* **Dual Licensed:**
  * **Free for Gamers, Streamers & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)**.
  * **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
* Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`, zero root access required) and neural voice denoising uses [`xiph/rnnoise`](https://github.com/xiph/rnnoise) (`BSD-3-Clause`).
