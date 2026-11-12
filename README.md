# SolStream Studio • ULTRA (`v1.1.3`)

**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (~3.5 MB), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio powered by the proprietary **`SolApex™ HyperStream Engine`** (**`SolCore Ultra™ Video Engine`** • **`SolLuxe™ 15-Stage Audio Engine`** • **`SolRelay Omega™ Broadcast Engine`** • **`SolCodec™ Hardware Silicon Layer`**). It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 15-stage 32-bit float vocal commentary**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, and Kick** over encrypted **RTMPS**.

> **100% Unrooted Stock Android:** Works on normal, unrooted Android 10–17+ phones (`minSdk 29`) right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on 100+ Mbps Fiber. Root access is **never** required or used.

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, touch response, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolApex™ HyperStream Engine`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0/2.0 Direct-TBDR frame pacer (`SolCore Ultra™`), precomputed vertex-stage UV offsets, zero-ALU fast passthrough, `STRETCH (Full 16:9 Zero-Bars)` aspect scaler, 6-stage GPU visual filter pipeline, isolated 15-stage 32-bit float / 64-bit double vocal & game soundstage DSP (`SolLuxe™`), Smart `AUTO` 48 kHz studio mic lock (`VOICE_RECOGNITION` wideband path), consonant-safe multi-strike spark/hammer impulse blanker, `0-ms` instant vocal gate, Unified 3-Zone Gain Controller, `16 KB`-aligned Native C `48 kHz RNNoise` engine, drift-locked stereo game circular buffer (`±12 ms`), 4x cubic inter-sample look-ahead limiter, async hardware encoder capability scanner with live fallback ladder (`SolCodec™`), 2-stage ground-reality thermal armor (`44.0°C / 90%` SoC + `-40%` proportional 30-FPS bitrate + `QP 14` lossless floor), AES-256 Keystore multi-slot vault (`SolRelay Omega™`), and 0-Hz sleeping 3-tab Accessibility Ghost Pill (`FLAG_SPLIT_TOUCH`) are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini and ChatGPT!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, and `assembleRelease`) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK (`v1.1.3`)](#1-download--install-signed-release-apk-v113)
2. [How Human + Dual-AI Built This App](#2-how-human--dual-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-ultra-actually-do)
4. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#4-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
5. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#5-global-broadcast-inspirations-windows-macos--ios---android)
6. [For Android Developers & Engineers: Architecture, 15-Stage Audio & 6-Stage Video](#6-for-android-developers--engineers-architecture-15-stage-audio--6-stage-video)
7. [For AI Coding Assistants, Security Auditors & Forkers](#7-for-ai-coding-assistants-security-auditors--forkers)

---

## 1. Download & Install Signed Release APK (`v1.1.3`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.1.3.apk`** under **Assets** to download the full **Ultra Master Edition** directly onto your Android phone (all earlier versions `v1.0.2` – `v1.1.2` remain permanently available in Releases!).
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.1.3`** straight over `v1.0.1`, `v1.0.2`, `v1.1.0`, `v1.1.1`, or `v1.1.2` as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. How Human + Dual-AI Built This App

This repository was engineered through a three-way verification loop between a competitive mobile gamer and two frontier reasoning models checking each other's blind spots:

- **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  * Defined real-world competitive constraints and validated builds across **multiple 3.5-to-5.5 hour (`205m–330m`) continuous `1440p60 HEVC` (`20 Mbps`) Multiplayer and Battle Royale live streams** on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e`, `2340×1080` 120 Hz display, 8 GB RAM), **realme Buds Wireless 5 ANC** neckband earphones, and 5 GHz Fiber Wi-Fi (`0` dropped frames, `0` reconnects).
  * Identified real-world afternoon thermal behavior (`44.0°C` / `90%` SoC load vs. `46.3°C` Severe throttling), `Avg QP: 5–10` CABAC encoder heat during `30 FPS` cooldown, Samsung One UI `16 kHz` call-mode microphone downgrade when CODM opens, `STRETCH` full 16:9 zero-bars framing, `0.3s` touch-and-hold responsiveness, and multi-strike background impulse noise (mosquito-racket zaps & neighborhood construction) from live sessions.
- **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  * Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c`), `SolLuxe™` 15-stage 32-bit float / 64-bit double audio studio (`MicDspProcessor` + `SolDualAudioEngine`), `SolCore Ultra™` 6-stage OpenGL ES 3.0/2.0 Direct-TBDR visual pipeline (`SolVideoPipeline`), `SolCodec™` hardware encoder selector & 2-stage thermal armor (`CodecCapabilityProbe`), `SolRelay Omega™` hardware Keystore vault & RTMPS engine (`KeystoreVault` + `SolStreamService`), and GitHub Actions NDK CI/CD pipeline.
- **Independent Spec Audit & Edge-Case Verification — `OpenAI ChatGPT`:**
  * Audited code paths across multiple release tags against Android 10–17 AOSP rules, separating `advertisedByMetadata` from `verifiedSurfaceConfigure`, blocking hardware encoder probing while live, explicit `720p30 / 480p / 360p` fallback ladders, verified 10-bit HDR10 output checks, per-session RTMP callback identity, thread-safe shutdown, and a 16-test fresh JUnit verification suite.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `45–160 MB` of camera masks, AdMob ads, and coin timers; lock `1440p/HEVC` behind expensive subscriptions; let Android drop your microphone to muffled `16 kHz` call mode when your game opens; or overheat your phone during `30 FPS` thermal fallback by still stuffing `20 Mbps` (`QP 5`) into half the frames.

**SolStream Studio Ultra (`SolApex™ HyperStream Engine`) solves every one of those problems in a ~3.5 MB, unrooted, zero-ad studio:**

1. **5 Smart Device Tiers (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`) + 100% Manual Freedom:**
   * Automatically scans your phone's physical RAM tier (`4–24 GB`, accounting for kernel GPU/modem carveouts), display refresh rate (`60–185 Hz`), OpenGL ES version, and hardware encoders (`HEVC / H.264 / AV1`) in the background, highlights your phone's recommended tier, and leaves every single button unlocked for manual customization.
2. **Plays at 90–185 FPS on Your Screen, Streams Smoothly at 60/30 FPS (`360p` to `4K60` • `0.5` to `100 Mbps`):**
   * Your phone display stays at its native `120 / 144 / 165 / 185 Hz` refresh rate while dedicated hardware video silicon (`SolCodec™`) encodes `360p`, `480p`, `720p`, `1080p`, `1440p`, or `4K (2160p)` from `0.5 Mbps` (mobile data saver) up to `100 Mbps` (ultra fiber) with a **Live Resolution & Codec Fallback Ladder**.
3. **`SolCore Ultra™` Video Engine, `STRETCH` Zero-Bars Framing & 2-Stage Thermal Armor:**
   * **`STRETCH (Full 16:9 Zero-Bars)` Framing (`360p` to `4K`):** Maps all 4 corners of `19.5:9 / 20:9` phone screens directly to all 4 corners of any 16:9 stream (`FIT → STRETCH → SMART → FILL`) so viewers on laptops, TVs, and phones see **zero black bars** and **100% of your CODM HUD buttons**.
   * **Precomputed Vertex-Stage UV Offsets + Zero-ALU Fast Passthrough:** Computes neighbor texture coordinates 4 times per frame in the Vertex Shader instead of `3.68 million` times per frame in the Fragment Shader, with direct `Framebuffer 0` Tile-SRAM rendering and Android 12–17 `ADPF` GPU/CPU voltage hints.
   * **2-Stage Ground-Reality Thermal Armor (`44.0°C / 90%` SoC + `-40%` Proportional 30-FPS Bitrate + `QP 14` Floor):**
     * **Stage 1 (`>= 44.0°C` or `>= 90%` SoC Load in `Moderate Warmth`):** Uses 1-tap GPU fetch on *repeat* frames (keeping full `Bicubic/Lanczos` on every fresh game frame and **full `60 FPS`**) and trims bitrate by `15%` to slow heat buildup before hitting `46°C`.
     * **Stage 2 (`Severe` Warmth or Manual `30 FPS` Cooldown):** Steps stream pacer to `30 FPS` **and lowers bitrate by `40%` (`20 Mbps -> 12 Mbps`)** with a **`QP 14` lossless floor** (`KEY_VIDEO_QP_I/P/B_MIN = 14`) so the hardware encoder never wastes heat at `QP 5–10` or hogs the memory bus during CODM gunfire! Automatically restores full `60 FPS` and `100%` bitrate once cooled.
4. **`SolLuxe™` 15-Stage Voice Studio + Smart `AUTO` 48 kHz Mic Lock + Unified 3-Zone Dynamics:**
   * **Smart `AUTO` Built-In Studio Mic Lock (`48 kHz VOICE_RECOGNITION`):** Automatically locks onto your phone's primary bottom studio mic in `Auto` mode and uses Android CDD Section 5.4 `VOICE_RECOGNITION` wideband routing—preventing OEM `16 kHz` call-mode muffling and keeping Bluetooth earphones in stereo Game Mode even if Bluetooth `Calls` is left ON!
   * **Smartphone MEMS 3-Band Vocal EQ (`Male` | `Neutral` | `Female`):** Tunes 64-bit `Double` tight chest body (`125 / 150 / 185 Hz`, `Q=1.15`), deep two-hand gaming-grip mud cut (`300 / 335 / 375 Hz`, `Q=1.35`, **`-3.2 to -3.4 dB`**), callout articulation (`3.15 / 3.40 / 3.65 kHz`, **`+2.6 to +2.8 dB`**), **`5.1 / 5.35 / 5.6 kHz` Velvet Air High-Shelf (`+3.2 dB`) + Adaptive Clarity Exciter**, and Split-Band De-Esser (`6.1 / 6.6 / 7.1 kHz`, `0.095f` threshold).
   * **Consonant-Safe Multi-Strike Spark & Hammer Blanker + `0-ms` Instant Gate:** Clamps rapid bursts of mosquito-racket zaps (*"phat-phat-phat"*) and hammer strikes (`>6.5x` slew without `220 Hz` vocal body) while leaving normal speech consonants (`T, K, P, S, Ch`) 100% crisp, and opens the vocal gate in **`0 ms`** on your very first syllable.
   * **Unified Non-Overlapping 3-Zone Gain Controller:** Links `Whisper Callout Lift` (Zone 1 quiet speech) + `Speech-Gated Slow AGC` (Zone 2 conversational speech) + `12ms-Punch 3:1 Broadcast Compressor` (Zone 3 loud peaks) so keeping all 10 stages ON in **`SOL LUXE`** sounds open, crisp, and punchy with zero squashing.
5. **Up to `250%` Game Mix + Stackable 3D Stereo Game Soundstage (`±12 ms` Drift-Locked):**
   * Boosts quiet internal game capture up to **`250%`** safely through our 64-sample look-ahead `4x` Cubic True-Peak Limiter (`-1.16 dBTP`), with 4 stackable stream-only soundstage enhancers: **`Cinema 3D Wide`**, **`Theater Sub-Punch`**, **`Footstep Focus`**, and **`Late-Night Blast Tamer`**.
6. **6-Stage Stackable GPU Visual Filters, Custom Scenes, `.PNG` Overlays & `9:16` Shorts Live:**
   * Mix and match **`Cinematic Filmic`**, **`AMOLED Vivid Pop`**, **`Shadow Boost` (Dark-Corner Enemy Clarifier)**, **`Color Temp` (`Neutral / Warm / Cool`)**, **`GPU Crisp Sharpen`**, **`Bilinear / Bicubic / Lanczos`** upscalers, **Verified `True 10-Bit HDR10 (HLG)`**, and **`Hardware ROI`**.

---

## 4. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Android 13–17 & Earphone Setup (2 Minutes)

1. **Unlock Restricted Accessibility (Why the `⋮` 3-Dots Hide at First on Android 13–17):**
   * Because you install the APK directly from GitHub Releases rather than Google Play, Android 13–17 hides the `Allow restricted settings` menu until you tap the greyed-out Accessibility switch once!
   * In Card 1, tap **`3. Tap Accessibility 1st`** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK** on the popup.
   * Return to SolStream Studio and tap **`4. Unlock Restricted (⋮)`** (teleports straight into SolStream's App Info screen) → tap **`⋮`** in the top-right corner → tap **Allow restricted settings** and confirm your PIN/fingerprint.
   * Tap **`3. Tap Accessibility 1st`** again and toggle **SolStream Studio** **ON**! *(Future signed updates keep this unlocked forever.)*
2. **Earphones Setup (`Auto (Phone Studio Mic Locked)` for Bluetooth Neckbands, AirPods, Wired & USB-C):**
   * **Automatic Studio Mic Lock:** When `Mic Input` is set to **`Auto (Phone Studio Mic Locked)`** (the default), SolStream automatically locks onto your phone's `48 kHz` Built-In Studio Mic using `VOICE_RECOGNITION` wideband mode—even if you leave Bluetooth `Calls ON` in Android settings!
   * **When Using CODM In-Game Team Chat With Bluetooth Earphones:** If you also open the *in-game* CODM team mic, toggle **`Calls` OFF** (keep `Audio` **ON**) inside your Bluetooth earphone settings so CODM itself cannot switch your earphones out of Stereo Game Mode.
   * **Samsung Separate App Sound Tip:** Open *Samsung Settings → Sounds and vibration → Separate app sound* → lock *Call of Duty* to your Bluetooth device.
3. **Single-App Privacy Capture:**
   * When Android's *"Start recording or casting?"* popup appears, select **"A single app" → Call of Duty** so WhatsApp messages, phone calls, and OTP notifications never appear on your live stream!

---

### Step B: Pre-Stream Dashboard (Every Card, Button & Collapsible Drawer)

#### Top Utility Bar
- **`? Plain-English Guide & Glossary`:** Opens the complete in-app `SolApex™` manual and dictionary explaining every video, framing, audio, and network term.
- **`Copy Preset` / `Import Preset`:** Copies or imports your complete video/audio configuration as JSON (Stream Keys are **never** included, so presets are 100% safe to share with friends).

#### Card 0 — Smart Device Tier Presets (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`)
- Displays your phone's hardware scan recommendation (`Scanner Recommended for [Device]: Flagship Tier`) with a cyan highlight. Tap any tier to load its recommended resolution, FPS, bitrate, upscaler, and voice preset—then freely adjust any individual button below.

#### Card 1 — Pre-Flight Readiness & Direct Settings Setup
- **`1. Grant Mic & Notif` / `2. Unrestricted Batt`:** Grants Microphone/Notification permissions and exempts the stream from background battery throttling.
- **`3. Tap Accessibility 1st` / `4. Unlock Restricted (⋮)`:** Exact 1-2 sequence to unlock and enable the floating in-game pill and simultaneous squad-mic sharing.
- **`Samsung One UI, AirPods/Neckband & BR Cooling Guide`:** Quick reference checklist for `Auto` Studio Mic Lock, `STRETCH` framing, and Battle Royale thermal tips.

#### Card 2 — `SolRelay Omega™` Encrypted RTMPS Vault, `9:16` Shorts Key & Simulcast
- **`Platform Preset` (`YOUTUBE` | `TWITCH` | `KICK` | `CUSTOM`):** Auto-fills the official RTMPS ingest URL and selects platform-compatible codec rules.
- **`Primary Stream Key` (`Paste Key` | `Show Key` | `Clear Key` | `Save Vault`):** Encrypts your key inside Android's hardware-backed Keystore (`AES-256-GCM`) and verifies that encryption + storage succeeded before confirming.
- **`Get Key on Phone (YT Studio)` / `Open YouTube App`:** Lets phone-only streamers grab their permanent Default Variable Stream Key from `studio.youtube.com` without a PC and jump to the YouTube app in 1 tap.
- **`▼ Expand: 9:16 Shorts Key Slot, Twitch/Kick Simulcast & Dual-Format`:**
  * **Slot #2 (`9:16` Variable Shorts Key):** Save a Default/Variable Resolution YouTube Stream Key (*"Turn on manual settings"* left OFF in YT Studio). Whenever you switch Card 3 to **`9:16 Vertical Shorts`**, YouTube automatically places your vertical stream straight into the **YouTube Shorts Live Feed**!
  * **`Simulcast to Twitch` & `Simulcast to Kick`:** Encodes video **once** on your hardware chip (`H.264 CBR`) and fans out packets simultaneously to YouTube + Twitch + Kick.
  * **`Dual-Format 16:9 + 9:16 Simultaneous Companion`:** Opt-in mode for `Flagship / Ultra` phones with 2-instance hardware encoders to stream `16:9` Landscape and `9:16` Shorts simultaneously from a single screen capture.
- **`Test Internet RTMPS Ping & Upload`:** Verifies DNS, TLS 1.2/1.3 SNI handshake, idle RTT/jitter, sustained upload Mbps, and loaded router bufferbloat.

#### Card 3 — `SolCore Ultra™` Video Engine & `SolCodec™` (`360p–4K60` • `0.5–100 Mbps` • `STRETCH` & Stackable Visuals)
- **`Codec` (`HEVC` | `H264` | `AV1`), `Res` (`360p` | `480p` | `720p` | `1080p` | `1440p` | `4K`), `Stream` (`60 FPS` | `30 FPS`):** Hardware-only encoding with automatic live resolution/codec fallback ladder.
- **`Format` (`16:9 Wide 0°/180°` | `9:16 Vertical Shorts`):** Flip `180°` live while in landscape so your USB-C charging cable rests comfortably between your fingers, or select `9:16 Vertical Shorts`.
- **`− Mbps` / `Target` (`0.5 Mbps` to `100 Mbps`) / `+ Mbps`, `Rate` (`CBR` | `VBR`), `Ping Guard ABR 2.0`:** Fine-tune bitrate across 23 steps with dynamic-floor congestion protection and a 6-second Zombie-Socket auto-reconnect watchdog.
- **`Upscaler` (`BILINEAR` | `BICUBIC` | `LANCZOS`), `Framing` (`FIT` | `STRETCH (16:9 Full)` | `SMART` | `FILL`), `Zoom` (`104%–120%`):**
  * **`FIT`:** Keeps original phone aspect ratio with thin top/bottom bars.
  * **`STRETCH (16:9 Full)`:** Maps all 4 corners of your phone screen to fill 100% of `360p, 480p, 720p, 1080p, 1440p, or 4K` widescreen displays with **zero black bars** and **100% of your CODM HUD buttons visible**!
  * **`SMART` / `FILL`:** Partial (`104%–120%`) or full aspect zoom.
- **Stackable Live Visual Filters (Combine Any 1, 2, 3, or All Live):**
  * **`Cinematic Filmic`**, **`AMOLED Vivid Pop`**, **`Shadow Boost (Dark Corners)`**, **`Color Temp` (`NEUTRAL` | `WARM` | `COOL`)**, and **`GPU Crisp Sharpen`**.
- **`▼ Expand: Custom Scenes, .PNG Overlay, Text Banner & SolCodec Silicon`:**
  * **Zero-Permission Graphic Pickers (`Starting Soon Img` | `BRB Privacy Img` | `.PNG Overlay` | `9:16 Poster`):** Pick images from your gallery via Android's system Photo Picker with zero Storage permissions.
  * **Built-In GPU Live Text Banner**, **`9:16 Shorts Canvas` (`BLUR` | `DARK` | `POSTER`)**, and **`Stream-Sniper Minimap Blur Shield`**.
  * **`SolCodec™` Silicon Controls:** `OpenGL Engine` (`AUTO` | `ES30` | `ES20`), verified `True 10-Bit HDR10 (HLG)`, `Hardware ROI Focus` (`AUTO` | `CENTER` | `HUD` | `OFF`), `IDR Keyframe` (`1s / 2s`) + `B-Frames (0/1/2)`, and `QP Range (Min QP 14 Floor)`.

#### Card 4 — `SolLuxe™` 15-Stage Voice Studio, `250%` Game Soundstage & 10s Calibrator
- **`Mic Input` (`Auto (Phone Studio Mic Locked)` or Specific Device), `Source` (`AUTO (48kHz Studio Wideband)` | `MIC` | `VOICE_REC` | `VOICE_COMM`), `Master AAC` (`64–320 kbps`):** Locks `48 kHz` studio microphone routing and stereo AAC bitrate.
- **Voice Profile (`Neutral` | `Male Voice` | `Female Voice` — Pick 1):** Tunes 64-bit MEMS vocal EQ (`125/150/185 Hz` body, `300/335/375 Hz` grip mud cut, `3.15/3.40/3.65 kHz` callout peak), `5.1/5.35/5.6 kHz` Velvet Air Shelf, and Split-Band De-Esser (`6.1/6.6/7.1 kHz`).
- **`Voice Type` (`RAW` | `CLEAN` | `NEURAL` | `RADIO STUDIO` | `VELVET` | `SOL LUXE`) & `Denoiser` (`Native C 48kHz RNNoise` | `Light Cut` | `Chip HW NS` | `OFF`):** Selects your vocal preset and single active noise suppressor (`Lv1 Gentle 65%`, `Lv2 Natural 88%`, `Lv3 Max 100%`).
- **`Mic Boost` (`50%–250%`), `Game Mix` (`0%–250%`), `VAD Ducking` (`OFF` | `-20%` | `-35%` | `-50%`), `Mic Sync` (`0–160 ms`).**
- **Stackable Stereo Game Soundstage (Combine Any 1–4 Live — Stream-Only):**
  * **`Cinema 3D Wide`**, **`Theater Sub-Punch` (`+1.8 dB @ 65 Hz`)**, **`Footstep Focus` (`+2.0 dB @ 2.85 kHz`)**, and **`Blast Tamer`**.
- **`▼ Expand: Individual Voice Filter Switches (Mix & Match All 10 Stages)`:** Independently toggle `(1) HPF Rumble`, `(2) Spark/Hammer & Grip Blanker`, `(3) 0-ms Instant Breath Gate`, `(4) MEMS 3-Band EQ`, `(5) Split-Band De-Esser`, `(6) 5.2kHz Velvet Air & Exciter`, `(7) 12ms-Punch 3:1 Compressor`, `(8) Whisper Callout Lift`, `(9) Speech-Gated Slow AGC`, and `(10) Speaker Echo Canceler (AEC)`.
- **10-Second Guided CODM Grip Calibrator (`Record 10s Test` | `Play Test` | `Delete` | `Apply Advisor Recommendation`).**

#### Card 5 — Launch Broadcast, Report History, App Benchmark & Creator Support
- **`GO LIVE — START STREAM`:** Waits cleanly if the 1-second startup hardware scan is finishing, verifies Keystore encryption, runs silent-volume guard, requests screen capture, and launches the broadcast.
- **`Session Reports & History`:** View and copy your current privacy-redacted Session Report (including **`Latest Mbps` + `Session Avg Mbps`**, **`Ingest RTT` ping**, **live-captured `Active Audio Policy`**, **`★ Saved Stream Clip Highlights`**, and **`Android OS Thermal Headroom (PowerManager)`**), or browse your **Archived Past Reports (Last 10 Streams)**.
- **`Benchmark Other Stream App`**, **`Refresh Mics / HW Scan`** (refreshes mics safely without touching video encoders if live), **`Check GitHub Update`**, and **`❤️ Subscribe to @Sol_CODM`**.

---

### Step C: In-Game 3-Tab Floating Pill (`🎙️ Audio` | `🎬 Video` | `📡 Bitrate/Net`)

While playing CODM, the floating Ghost Pill sleeps at **38% opacity** with **0 Hz UI redraws** and **`FLAG_NOT_TOUCH_MODAL | FLAG_SPLIT_TOUCH`** so your `0.3s` touch-and-hold and multi-finger claw inputs have **0 ms delay**. **Double-tap** (or long-press) the pill to open the compact 3-tab studio:

- **Live Telemetry Header:** Shows `HH:MM:SS | Out Mbps | Fresh/Encoded FPS | Queue (Q:0f or Q:N/A) | Battery °C + SoC Thermal Load %`.
- **Top Quick-Action Bar:** **`Mic:ON/MUTE`**, **`Game:ON/MUTE`**, **`Scene:LIVE/BRB/START`**, **`★ Clip`**, and **`✕`**.
- **Tab 1 (`🎙️ Audio`):** Switch `Neutral / Male / Female` profile, `SOL LUXE` voice types, `RNNoise` level, `HPF`, `EQ + DeEss + Velvet + AGC`, `Mic Boost (50–250%)`, `Game Mix (0–250%)`, `Cinema 3D Wide + Sub-Punch + Footsteps + Blast Tamer`, `VAD Ducking`, and live `Mic Hardware` input.
- **Tab 2 (`🎬 Video`):** Cycle `LIVE / STARTING SOON / BRB` scenes, `Bilinear / Bicubic / Lanczos` upscaler, **`FIT / STRETCH / SMART / FILL` framing**, stackable `Cinematic Filmic + AMOLED Vivid + Shadow Boost + Color Temp`, `0°/180°` USB-C flip, `GPU Sharp`, and `.PNG Overlay / Live Banner / Minimap Blur Shield`.
- **Tab 3 (`📡 Bitrate/Net`):** Step live bitrate down/up (`0.5–100 Mbps`), toggle `Ping Guard ABR 2.0`, toggle **`30 FPS Cooldown Saver`** (steps to `30 FPS` + `-40%` proportional bitrate and auto-resets when cooled), view Simulcast/Companion status, or 2-tap **`Stop Stream`**.

---

## 5. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Ultra Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **Smart `AUTO` 48 kHz Studio Mic Lock (`VOICE_RECOGNITION`)** | Android CDD Section 5.4 Pro Audio | Locks `TYPE_BUILTIN_MIC` before `startRecording()` using `VOICE_RECOGNITION` wideband mode (where OEM AGC/call-mode muffling is forbidden by Google CDD) so Bluetooth earphones stay in stereo Game Mode. |
| **Isolated 15-Stage `SolLuxe™` 32-Bit Float / 64-Bit Double Bus** | OBS Studio, Reaper DAW & Elgato Wave Link | Captures mono `PCM_FLOAT` mic separately from stereo `AudioPlaybackCapture` with 64-bit `Double` recursive biquads and `THREAD_PRIORITY_AUDIO (-16)` so CODM gunfire threads are never preempted. |
| **Phase-Aligned Native C `48 kHz RNNoise`** | Xiph.Org RNNoise & Shure MVX2U DSP | Compiles official Xiph `RNNoise` in C with `16 KB` ELF alignment (`libsol_rnnoise.so`), a 480-sample dry delay line, and a pure single-pass rule that disables secondary low-pass filtering when C `RNNoise` runs. |
| **Consonant-Safe Multi-Strike Spark & Hammer Blanker + `0-ms` Gate** | Broadcast Vinyl De-Clicker / Radar Impulse Blanker | Detects high-slew non-vocal transients (`>6.5x` slew without `220 Hz` vocal body) to clamp mosquito-racket zaps (*"phat-phat-phat"*) by up to `92%` while preserving 100% of speech consonants (`T, K, P, S, Ch`). |
| **Smartphone MEMS Vocal EQ + `5.2 kHz` Velvet Air + 3-Zone Gain** | Neumann / Aphex 230 Broadcast Voice Channel | Carves out two-hand grip mud (`300–375 Hz`), boosts callout clarity (`3.15–3.65 kHz`), adds `5.1–5.6 kHz` Velvet Air + Clarity Exciter, and links `Whisper Lift + Slow AGC + 12ms-Punch Compressor` into 3 non-overlapping zones. |
| **Stackable `Cinema 3D Wide` Game Soundstage + `4x` Cubic Limiter** | Dolby Atmos for Gaming & FabFilter Pro-L2 | Mid-Side (`M/S`) matrix widens `>200 Hz` stereo cues by `+26%` and feeds a 64-sample look-ahead `4x` Catmull-Rom True-Peak Limiter (`-1.16 dBTP`) with `±12 ms` drift-locked A/V sync. |
| **`SolCore Ultra™` Precomputed Vertex UV + `STRETCH` Zero-Bars** | Desktop OBS + BenQ Zowie Black eQualizer + AMD CAS | Moves neighbor UV math to the Vertex Shader (4x/frame vs 3.68M/frame), maps full 16:9 zero-bars `STRETCH` across `360p–4K`, and verifies true 10-bit HLG HDR10 output. |
| **2-Stage Ground-Reality Thermal Armor + `QP 14` Floor** | Custom Mobile Esports Silicon Governor | Stage 1 (`>=44.0°C / 90%` SoC) cools repeat-frame GPU fetches and trims `15%` Mbps at `60 FPS`; Stage 2 (`Severe` / `30 FPS` Cooldown) scales Mbps by `-40%` with a `QP 14` floor to prevent `QP 5–10` CABAC overheating. |

---

## 6. For Android Developers & Engineers: Architecture, 15-Stage Audio & 6-Stage Video

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
- **JDK, Gradle & NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + Android Gradle Plugin (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`, `versionCode = 7`, `versionName = "1.1.3"`) + Android NDK r28+ (`CMake 3.22.1`, `-DANDROID_STL=none`, `-O3 -std=gnu11`).
- **16 KB Page Alignment, Fresh JUnit & Strict Lint:** Enforces `-Wl,-z,max-page-size=16384` in `CMakeLists.txt` and `packaging.jniLibs.useLegacyPackaging = false`, verified on every CI run via `readelf -lW` (`0x4000` alignment) alongside `apksigner` V3 certificate verification, strict Android Lint (`abortOnError = true`), and fresh un-cached execution (`--rerun-tasks`) of all **16 JUnit 4 unit tests** (`SolStreamUnitTests.kt`).

### `SolApex™ HyperStream Engine` — 4-Pillar Subsystem Map
1. **`SolCore Ultra™` (Video & GPU Compositor — `SolVideoPipeline.kt`):** Zero-copy `VirtualDisplay` + `SurfaceTexture` into single-pass Direct-TBDR OpenGL ES 3.0/2.0 shaders with precomputed vertex UV offsets, zero-ALU passthrough, `FIT / STRETCH / SMART / FILL` framing, repeat-tick 1-tap thermal cooler, and Android 12–17 `ADPF` hints.
2. **`SolLuxe™` (15-Stage Audio Studio — `MicDspProcessor.kt` + `SolDualAudioEngine.kt`):** Smart `AUTO` 48 kHz studio mic lock, 64-bit `Double` MEMS vocal biquads, consonant-safe spark blanker, C `RNNoise`, `5.1–5.6 kHz` Velvet Air + Exciter, Unified 3-Zone Gain Controller, 250% stereo game soundstage, and `4x` Cubic True-Peak Limiter.
3. **`SolRelay Omega™` (Broadcast & Network Engine — `SolStreamService.kt` + `KeystoreVault.kt`):** StrongBox/TEE AES-256-GCM key vault, TLS+SNI verification, Queue-Aware Ping Guard ABR 2.0, 6s Zombie-Socket watchdog, live TCP Ingest RTT probe, and single-encode multi-target simulcast.
4. **`SolCodec™` (Hardware Silicon Layer — `CodecCapabilityProbe.kt` + `SolVideoPipeline.kt`):** Paired resolution+FPS 2-frame EGL hardware encoder verifier (separating `advertisedByMetadata` from `verifiedSurfaceConfigure` and `verifiedRealEncode`), `QP 14` lossless floor, `KEY_ALLOW_FRAME_DROP = 0`, Hardware ROI, and live resolution/codec fallback ladder.

### Source Tree

~~~text
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
│       ├── MainActivity.kt                  # SolApex™ 5-Tier Dashboard, Live Scan Gate, STRETCH, Glossary, Reports
│       ├── SolStreamService.kt              # SolRelay Omega™ 3-Tab Pill (FLAG_SPLIT_TOUCH), 2-Stage Thermal Armor, ABR 2.0
│       ├── SolVideoPipeline.kt              # SolCore Ultra™ OpenGL ES 3.0/2.0, Vertex UV, STRETCH, QP 14 Floor, Fallback Ladder
│       ├── SolSceneAssetStore.kt            # Atomic staging-file Starting Soon, BRB, .PNG Overlay & GPU Text Banner store
│       ├── SolDualAudioEngine.kt            # SolLuxe™ Smart AUTO 48kHz Mic Lock, 250% Game Soundstage, ±12ms Sync, 4x Limiter
│       ├── MicDspProcessor.kt               # SolLuxe™ MEMS Vocal EQ, 5.2kHz Velvet Air + Exciter, 3-Zone Gain, Spark Blanker
│       ├── SolNativeRnnoise.kt              # Safe Kotlin JNI wrapper for libsol_rnnoise.so
│       ├── CodecCapabilityProbe.kt          # SolCodec™ non-blocking hardware verifier (honest metadata vs surface flags)
│       ├── KeystoreVault.kt                 # Verified Hardware AES-256-GCM multi-slot key vault & JSON preset share
│       └── DiagnosticsLogger.kt             # Privacy-redacted telemetry, Session Avg Mbps, Live Audio Policy & Clip Markers
└── test/java/com/solgaming/solstreamstudio/
    └── SolStreamUnitTests.kt                # 16-test JUnit 4 verification suite
~~~

### Build & Release Verification Command

~~~bash
gradle testDebugUnitTest --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37 --no-daemon --stacktrace
gradle lintDebug assembleDebug assembleRelease -Pandroid.suppressUnsupportedCompileSdk=36,37 --no-daemon --stacktrace
~~~

---

## 7. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`, and **ALWAYS** call `videoPipeline.prepareForImmediateStop()` synchronously before async teardown so `MediaProjection.Callback` unregisters without false `[CAPTURE_STOP]` logs.
2. **NEVER** open test `MediaCodec` encoder instances inside `CodecCapabilityProbe.scanDevice()` while `SolStreamService.isBroadcastLive == true`, and **NEVER** set `verifiedSurfaceConfigure = true` on fast metadata-only fallback scans.
3. **NEVER** call `mediaProjection.createVirtualDisplay(...)` more than once per consent token—adapt size via `onCapturedContentResize()` + `virtualDisplay.resize(...)`, share the single `SurfaceTexture` across primary and optional `9:16` companion surfaces, and render `Starting Soon / BRB` slates inside `SolVideoPipeline.runGlRenderLoop`.
4. **NEVER** allocate heap objects inside `MicDspProcessor.processMonoFrameInPlace`, `SolNativeRnnoise.processMono480InPlace`, or per-frame audio/OpenGL loops—preserve zero-GC steady state for 6+ hour marathon sessions.
5. **NEVER** stack multiple stationary noise suppressors simultaneously (`RNNoise`, `Light Cut`, and `Chip HW NS` are mutually exclusive in `MicDspProcessor`), and **NEVER** allow `Slow AGC` to boost gain when `confirmedSpeech == false`.
6. **NEVER** introduce `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `SYSTEM_ALERT_WINDOW`, `AudioManager.setCommunicationDevice()`, `-ffast-math` in `CMakeLists.txt` (blocked by Xiph `arch.h`), or software video encoders (`c2.android.av1.encoder`).

### Privacy & Security Auditability
- **Zero Telemetry / Zero Ads / No Root:** Connects *only* to your configured RTMPS servers (plus `speed.cloudflare.com` and `api.github.com` strictly when you manually tap the network test or update buttons).
- **Stream Key Protection:** Encrypted in `AndroidKeyStore` (`AES/GCM/NoPadding`), verified on commit, redacted via regex in `DiagnosticsLogger`, excluded from Preset JSON exports, and stripped from Release Logcat via `proguard-rules.pro`.
- **Minimal Accessibility Scope:** `canRetrieveWindowContent="false"`, `canPerformGestures="false"`, `canRequestTouchExplorationMode="false"`—used strictly to host `TYPE_ACCESSIBILITY_OVERLAY` (`FLAG_SPLIT_TOUCH`) and satisfy Android's foreground microphone-sharing policy with zero touch-response overhead.

---

## License & Commercial Protection

- Created & maintained by **Sol (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
- **Dual Licensed:**
  * **Free for Gamers, Streamers & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)**.
  * **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
- Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`, zero root access required) and neural voice denoising uses [`xiph/rnnoise`](https://github.com/xiph/rnnoise) (`BSD-3-Clause`).
