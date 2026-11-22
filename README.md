# SolStream Studio • ULTRA (`v1.5.1`)

**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (`~5.9 MB` Signed Release APK • `~6.0 MB` Google Play `.aab`), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio powered by the proprietary **`SolApex™ HyperStream Engine`** (**`SolCore Ultra™ Video Engine`** • **`SolLuxe™ 3.0 Pure Audio Engine`** • **`SolRelay Omega™ Broadcast Engine`** • **`SolCodec™ Hardware Silicon Layer`**). It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 6-stage 32-bit float / 64-bit double pure condenser vocal commentary with a 12-Mode Hardware Acoustic Matrix**, **opt-in zero-camera GPU broadcast overlays (`SolHUD™`, Offline Corner QR, 2-PNG PNGTuber & 1-Tap Clean Thumbnails)**, **4 One-Tap Studio Profiles**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, Kick, and Custom RTMP/RTMPS servers**.

> **100% Unrooted Stock Android (Android 10–18 Ready & Google Play Compliant):** Works on normal, unrooted Android 10–18+ phones and tablets/foldables (`minSdk 29`, `targetSdk 36`, `compileSdk 37`) right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on 100+ Mbps Fiber. Root access is **never** required or used, and zero restricted battery-exemption, camera, or broad storage permissions are declared. See our [**Official Privacy Policy (`PRIVACY_POLICY.md`)**](./PRIVACY_POLICY.md).

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, touch response, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolApex™ HyperStream Engine v1.5.1`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0 (`Fast-FP16`) / ES 2.0 Direct-TBDR frame pacer (`SolCore Ultra™`), Hardware TMU-assisted 5-tap Bicubic/Lanczos upscaler with gated cardinal neighbor texture fetches (`14.74 million` fewer texture reads/frame at `1440p60`), Android 11–18 `Surface.setFrameRate()` hardware capture cooler, unified `16:9` & `9:16` Vulkan `gralloc` PSS memory guard (`~199–258 MB` flat PSS across multi-hour streams), `40%` cooler `glScissor` `9:16 BLUR` / `0%` extra GPU `DARK` / static `POSTER` Shorts canvas modes, `STRETCH (Full 16:9 Zero-Bars)` aspect scaler, 5-position aspect-preserving `.PNG` corner logo compositor (`Logo:OFF → FULL → TL → TR → BL → BR`), **`SolHUD™` zero-allocation live GPU telemetry badge** (visible to viewers even in Android 14–18 Single-App Capture), **pure-Kotlin ISO/IEC 18004 offline Corner QR generator (`GL_NEAREST`)**, **zero-camera 2-PNG PNGTuber avatar** with gender-matched Cyber Visor fallback, **temporary offscreen RGBA8 FBO 1-Tap Clean Screenshot** (`Pictures/SolStream/` via zero-permission `MediaStore`), **4 hardware-clamped One-Tap Studio Profiles**, Dual-Layer Privacy Blur Shield (8 HUD zones + automatic `Auto-Blur Keyboard`), Adaptive Dark-Corner Clarifier, isolated **`SolLuxe™ 3.0 Pure` 6-stage vocal & game soundstage DSP** (thread-safe atomic frame-boundary coefficient updates, automatic **4-Hardware × 3-Gender `12-Mode` Acoustic Matrix**, **`55 / 68 / 85 Hz` Auto/Manual 64-Bit Butterworth HPF**, **`<0.6 ms` Raised-Cosine Micro-Impulse Clamper**, `16 KB`-aligned Native C `48 kHz` Xiph `RNNoise` with hardware Flush-to-Zero `FZ` register guards + **`16 kHz`-safe Hybrid Neural-VAD Smooth Expander**, **Split-Band Dynamic Plosive Guard**, **zero-overlap Condenser EQ + Low-Band Analog Warmth Shaper**, real **`5.2 kHz` Bluetooth SCO Hiss LPF**, **Gender-Sidechain Soft-Knee Broadcast Leveler with continuous VAD-synced quiet-speech lift**, **Post-Comp Dynamic Sibilance-Ratio De-Esser**, and **`4×` Catmull-Rom Inter-Sample True-Peak Limiter at `-1.16 dBTP`**), universal 4-tier microphone capture fallback (`48 kHz PCM_FLOAT → 48 kHz/44.1 kHz/16 kHz PCM_16BIT` linearly resampled to `48 kHz`), headphone-aware effective Speaker AEC guard, exact-launched 2-worker audio cleanup barrier, drift-locked stereo game circular buffer (`±12 ms`), simultaneous-instance-verified companion encoder selection & accurate **`Bypass Charging (USB-PD)`** telemetry (`SolCodec™`), smart thermal bitrate governor with `SEVERE` override protection, AES-256 Keystore non-destructive vault, Pedro `RootEncoder` $\mu\text{s} \rightarrow \text{ms}$ RTT conversion, `setCheckServerAlive(false)` + **Network-Adaptive RTMP Write Chunking (`65,535 B` Wi-Fi / `4,096 B` Mobile Data & Custom)**, bounded leased-slot `ArrayBlockingQueue<PacketSlot>` simulcast pool with per-client callback identity guards, off-main-thread `5s` teardown barrier, **frozen post-stop Session Reports + 60-second single-slot storage auto-checkpointing**, Instant `Wi-Fi ↔ 5G` Multipath Failover (`SolRelay Omega™`), and 0-Hz sleeping 3-tab Accessibility Ghost Pill with live **App vs. Game Heat/CPU & RAM Split** are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini, ChatGPT, and Claude!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, `assembleRelease`, `bundleRelease`, `zipalign -c -P 16`, and ELF `16 KB` checks) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK or Play Store Bundle (`v1.5.1`)](#1-download--install-signed-release-apk-or-play-store-bundle-v151)
2. [How Human + Multi-AI Built This App](#2-how-human--multi-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-ultra-actually-do)
4. [The 12 Custom `SolApex™ v1.5.1` Engineering Innovations](#4-the-12-custom-solapex-v151-engineering-innovations)
5. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#5-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
6. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#6-global-broadcast-inspirations-windows-macos--ios---android)
7. [For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec](#7-for-android-developers--engineers-architecture-6-stage-solluxe-30-pure--build-spec)
8. [For AI Coding Assistants, Security Auditors & Forkers](#8-for-ai-coding-assistants-security-auditors--forkers)
9. [Privacy Policy, Google Play Compliance & Open-Source Licenses](#9-privacy-policy-google-play-compliance--open-source-licenses)

---

## 1. Download & Install Signed Release APK or Play Store Bundle (`v1.5.1`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.5.1.apk`** under **Assets** to download the full **Ultra Master Edition** (`~5.9 MB` signed release APK) directly onto your Android phone (or download **`SolStream-Studio-v1.5.1-PlayStore.aab`** for Google Play Console distribution). All earlier versions (`v1.0.2` – `v1.5.0`) remain permanently available in Releases.
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.5.1`** (`versionCode = 13`) straight over any earlier version (`v1.5.0` or older) as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. How Human + Multi-AI Built This App

This repository was engineered through a continuous verification loop between a competitive mobile gamer and frontier AI models auditing each other's code line-by-line:

- **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  - Validated builds across multi-hour (`98m–330m`) continuous `1440p60 HEVC` (`18–20 Mbps`) Multiplayer and Battle Royale live streams on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e • Samsung Xclipse 940 (Vulkan)`, `2340×1080` 120 Hz display, 8 GB RAM), **realme Buds Wireless 5 ANC** neckband earphones, and 5 GHz Fiber Wi-Fi (`0` sender drops, `0` reconnects, `0 ms` send queue, `28 ms` A/V sync offset, `~7–8%` app CPU, `199–258 MB` peak PSS).
  - Caught real-world audio, UX, and OS reliability edge cases directly from live streams: eliminating speech-onset `"prit-prit"` gate clicks, isolating the Samsung `Device Care → Memory → Excluded apps` background-kill behavior on 8 GB phones, fixing Floating Pill `Game:...` truncation and unified `Logo:OFF` toggling, replacing the old boxed launcher icon with the full-bleed **Solar Eclipse `'S'`** design, catching post-stop idle report overwrites plus `SEVERE` 30 FPS manual-tap log loops in `v1.4.0` marathon logs—driving `v1.5.0`'s frozen reports, live-temp toast guard, Single-App-visible `SolHUD™`, offline Corner QR, zero-camera PNGTuber, 4 Studio Profiles, and 1-Tap Clean Thumbnails—and surfacing `v1.5.1`'s Play Store polish fixes (accurate `Bypass Charging (USB-PD)` current detection, unified `9:16` vertical Vulkan PSS resize guard, auto-enabled Slot #2 Shorts Key, accurate `DARK`/`POSTER`/`BLUR` Shorts canvas labels, and clean no-competitor benchmark headers).
- **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  - Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c`), the rebuilt 6-stage thread-safe **`SolLuxe™ 3.0 Pure`** audio engine (`MicDspProcessor.kt` + `SolDualAudioEngine.kt`), **`SolCore Ultra™`** OpenGL ES 3.0 `Fast-FP16` video pipeline & offscreen FBO clean screenshot engine (`SolVideoPipeline.kt`), pure-Kotlin **`SolHudAndQrRenderer.kt`** (ISO/IEC 18004 QR + zero-GC `SolHUD™` + pure-Kotlin corner MVP math + `MediaStore` saver), built-in Cyber Visor avatar generator (`SolSceneAssetStore.kt`), **`SolCodec™`** concurrent-instance capability scanner & Bypass PD classifier (`CodecCapabilityProbe.kt`), **`SolRelay Omega™`** adaptive-chunk RTMPS transport, 4 Studio Profiles & 60-second checkpointing (`KeystoreVault.kt`, `SolStreamService.kt`, `DiagnosticsLogger.kt`), **Obsidian-Glass** dashboard & visual User Guide (`MainActivity.kt`), **Solar Eclipse `'S'`** adaptive vector icon (`ic_solstream_logo.xml`), and pinned CI/CD verification pipeline (`build.yml`).
- **Independent Line-by-Line Source Audits — `OpenAI ChatGPT` & `Anthropic Claude`:**
  - Audited live commits against Pedro `RootEncoder 2.8.1`, Xiph `RNNoise v0.1.1`, W3C biquad filter stability (`max pole radius < 1.0`), and Android 10–17 API rules—surfacing key fixes shipped across `v1.4.0`–`v1.5.1`: `setCheckServerAlive(false)` code alignment, atomic frame-boundary DSP coefficient updates, Auto vs. Manual HPF preservation, headphone-aware effective Speaker AEC cutoff, exact-launched 2-worker audio cleanup barrier, `PCM_16BIT`/alternate-rate mic fallback, `AuxRtmpTarget` per-client callback ownership, off-main-thread `activeTeardownLatch` timeout handling, simultaneous-instance companion codec filtering, pinned RNNoise commit + NDK `28.2.13676358`, `16 KB` `zipalign` verification, and an expanded **52-test JUnit 4 suite**.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `45–180 MB` of camera masks, AdMob ads, GPS/Contact trackers, and coin timers; lock `1440p/HEVC` behind subscriptions; click or distort your voice when a game switches the mic HAL to `16 kHz`; or get silently killed by Android's background memory manager at hour 3.

**SolStream Studio Ultra (`SolApex™ HyperStream Engine v1.5.1`) solves all of those problems in a `~5.9 MB` (Release), unrooted, zero-ad studio:**

1. **Obsidian-Glass Dashboard, Solar Eclipse `'S'` Branding & Live "Protect Long Streams" Banner:**
  - Features the full-bleed **Solar Eclipse `'S'`** adaptive vector icon, 3 glanceable top studio chips (`SolLuxe 3.0` • `1440p60 • HEVC` • `Protected ✓`), a 3-Tile Hero Summary Bar (`[ 🎥 1440p (2K) ] [ ⚡ 60 FPS ] [ 📡 20 Mbps ]`), collapsible pro accordion drawers, and a zero-permission **Protect Long Streams** readiness banner with brand-aware anti-kill tips (Samsung, Xiaomi/POCO, Vivo/iQOO, OnePlus/Realme/Oppo, Pixel).
2. **Plays at 90–185 FPS on Your Screen, Streams Steady 60/30 FPS (`360p` to `4K60` • `0.5` to `100 Mbps`):**
  - Your display stays at its native `120 / 144 / 165 / 185 Hz` refresh rate while Android 11–18 `Surface.setFrameRate()` hardware pacing and dedicated hardware encoders (`SolCodec™`) encode rock-steady Constant Frame Rate video with a strict 2-second keyframe cadence and a **3-Attempt Bare-Metal Hardware Encoder Fallback Ladder**.
3. **`SolLuxe™ 3.0 Pure` — 6 Non-Fighting Pure Condenser Stages (Zero "Prit-Prit" Clicks):**
  - Rebuilt from the ground up into 6 mathematically separated stages so zero filters fight or cancel each other: `55 / 68 / 85 Hz` 64-bit Butterworth HPF → `<0.6 ms` Raised-Cosine Micro-Impulse Clamper → Native C `48 kHz` `RNNoise` + `16 kHz`-Safe Hybrid Neural-VAD Smooth Expander (`8 ms` attack / `220 ms` release, `-14 dB` floor) → Split-Band Dynamic Plosive Guard (`<115 Hz` wind vs. vocal core) → 4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth Shaper → Gender-Sidechain Soft-Knee Leveler + Dynamic Sibilance-Ratio De-Esser + `4×` True-Peak Limiter (`-1.16 dBTP`).
4. **Pro Broadcast Overlays, 1-Tap Clean Thumbnails & 4 One-Tap Studio Profiles (`v1.5.0` / `v1.5.1`):**
  - Add an opt-in **`SolHUD™`** live GPU telemetry badge (`FPS • °C • Mbps` visible to viewers even in Android 14–18 Single-App Capture), an **offline Corner QR code** (UPI / YouTube / Discord), or a **zero-camera 2-PNG PNGTuber avatar** (with a built-in gender-matched Obsidian Cyber Visor fallback)—all using `0 MB` VRAM when OFF. Grab pill-free `1080p` YouTube thumbnails mid-match with **`📸 Shot`**, and swap setups in 1 tap with **4 hardware-clamped Studio Profiles**.
5. **Crash-Proof 60-Second Auto-Checkpoint, Frozen Post-Stop Reports & Live App vs. Game Resource Split:**
  - Checkpoints live session telemetry and `★ Saved Clips` to a single dedicated storage slot every 60 seconds (never polluting your 9-slot session history), freezes finalized end-of-stream reports so reopening later never overwrites your real metrics, accurately detects **`Bypass Charging (USB-PD)`**, and guarantees even an unexpected OS kill never wipes your report or clip timestamps.

---

## 4. The 12 Custom `SolApex™ v1.5.1` Engineering Innovations

| # | Custom `SolApex™ v1.5.1` Innovation | The Real-World Gaming Problem It Solves | How Our Custom Math & Silicon Engineering Works |
|---|---|---|---|
| **1** | **6-Stage Non-Fighting `SolLuxe™ 3.0 Pure` Condenser Chain + Thread-Safe Frame-Boundary Snapshots** | Hard `0-ms` gates, `4.5 ms` consonant blankers, and step-cliff whisper boosts caused `"prit-prit"` speech-onset clicks and hollow mids; mutating biquad coefficients from the UI thread risked live audio glitches. | Publishes settings atomically via an immutable `DspConfigSnapshot` adopted strictly at `10 ms` frame boundaries on the audio thread; replaces hard gates with an `8 ms` / `220 ms` Hybrid Neural-VAD smooth expander (`-14 dB` floor), `<0.6 ms` raised-cosine micro-impulse clamping, and strictly non-overlapping 4×3 EQ pockets. |
| **2** | **`16 kHz`-Safe Hybrid Neural-VAD + True `100%` Native C `RNNoise` + Headphone-Aware Speaker AEC** | When CODM activates in-game voice chat, Samsung's HAL locks the mic to `16 kHz`; meanwhile, leaving Speaker AEC enabled with earphones plugged in could silently route through `VOICE_COMMUNICATION` and hollow out vocal bass. | Opens the expander smoothly whenever neural `vadProb >= 0.35` OR `200–2500 Hz` speech energy exceeds the adaptive noise floor by `+6 dB`; sets RNNoise Level 3 to true `1.00f` wet strength; enforces `effectiveSpeakerAec = activeSpeakerAecEnabled && !headphonesConnected` on every route change and verifies `AcousticEchoCanceler.getEnabled()`. |
| **3** | **Universal 4-Tier Mic Capture Fallback (`LinearMonoResampler48k`) + Exact-Launched 2-Worker Barrier** | Strict OEM audio HALs on some phones reject `48 kHz ENCODING_PCM_FLOAT`; independent worker finalizers could race and release shared DSP/AAC resources while the other audio thread was still exiting. | Tries `48 kHz PCM_FLOAT` first (zero overhead on flagships), falling back cleanly through `48 kHz / 44.1 kHz / 16 kHz PCM_16BIT` linearly resampled to exact `480-sample / 48 kHz` float; `AudioWorkerBarrier` releases shared resources strictly once after both `SolFloatAudioMixer` and `SolAacDrain` exit. |
| **4** | **Network-Adaptive RTMP Write Chunking (`65,535 B` / `4,096 B`) + `setCheckServerAlive(false)`** | Pedro's default `128-byte` RTMP chunking creates heavy packetization overhead at `20 Mbps`, while `setCheckServerAlive(true)` invokes ICMP `socket.isReachable()` which causes false disconnects on restricted Wi-Fi/VPNs. | Disables ICMP reachability probes (`setCheckServerAlive(false)`) in favor of real RTMP pings + `8.5s` handshake & `6s` stall watchdogs, and dynamically sets `setWriteChunkSize` to **`65,535 bytes` (64 KB)** on Wi-Fi/Ethernet for YouTube/Twitch/Kick and **`4,096 bytes` (4 KB)** on Cellular and unknown custom RTMP servers. |
| **5** | **Per-Client Callback Ownership (`PrimaryRtmpCallback` + `AuxRtmpCallback`), Cumulative Drop Tracking & Leased-Slot Pool** | Delayed async `onDisconnect()` callbacks from a previous `RtmpClient` could mark a newly reconnected auxiliary target offline; naive ring buffers could overwrite queued simulcast packets; reconnects could reset drop counters. | Wraps every primary and auxiliary `RtmpClient` in a dedicated per-client callback checking `sourceClient === client`, preserves `cumulativeDroppedFramesBeforeReconnect` across socket swaps, resets `connected = false` before client swaps, and leases exclusively owned `PacketSlot` buffers from a bounded `ArrayBlockingQueue`. |
| **6** | **Off-Main-Thread `5s` Teardown Guard, Frozen Post-Stop Reports, `SEVERE` Toast Guard & Accurate Bypass PD Detection** | Starting a new stream before a slow encoder finished tearing down caused contention; idle report reopens overwrote post-stop metrics; tapping `30FPS Cool` during `SEVERE` warmth bounced `30→60→30`; Pause USB PD misreported as `AC Fast Power`. | Awaits `activeTeardownLatch` off the main thread (`5s` ceiling), freezes finalized telemetry (`isSessionFinalized` + `sessionEndMs`), preserves recovered 60s checkpoints across idle speed tests, detects `Bypass Charging (USB-PD)` via `NOT_CHARGING` or smoothed AC current `<= 250 mA` (`15–95%`), and blocks premature manual release during `SEVERE` warmth with a live-temp toast. |
| **7** | **Simultaneous-Instance-Verified Companion Codec Selection (`eligibleCompanionEncoders`)** | Devices where H.264 supports 2 concurrent instances but HEVC supports only 1 could pass a generic dual-encoder check and then fail trying to open a second HEVC instance. | Filters companion candidates strictly to codecs reporting `maxConcurrentInstances >= 2` with 1080p support, falling back cleanly from HEVC to H.264 in both `SolVideoPipeline` and `SolStreamService`. |
| **8** | **Slider-Independent `3D Game Soundstage` + `4×` Catmull-Rom Inter-Sample True-Peak Limiting** | High `Game Mix %` or `Mic Boost %` settings can create inter-sample peaks that clip AAC encoders even when individual samples stay below `0 dBFS`. | Evaluates 4× oversampled Catmull-Rom cubic inter-sample peaks (`t = 0.25, 0.50, 0.75`) at `-1.16 dBTP` (`0.875`) on both the vocal bus and the 64-sample look-ahead stereo master bus, paired with phase-safe `62 Hz` Sub-Punch and `250 Hz + 2.45 kHz` Footstep Focus. |
| **9** | **Gated Neighbor Shader (`14.74M` Fewer Reads/Frame) + `Surface.setFrameRate()` + Unified `16:9` & `9:16` Vulkan PSS Guard** | Unconditional neighbor texel reads waste GPU power when Sharpen/Shadow Boost are OFF, and background focus blips can trigger `VirtualDisplay.resize()` Vulkan memory spikes across both landscape and vertical streams. | Gates the 4 cardinal neighbor fetches strictly inside `if (uSharpen > 0.01 \|\| uShadowBoost > 0.5)`, paces `SurfaceFlinger` capture in hardware, uses an atomic resize pair (`shouldAcceptCaptureResize`) that blocks hidden/unfocused resizes across both `16:9` and `9:16` plus portrait resizes on `16:9`, and skips `9:16` blur passes when zoomed gameplay fills `100%` height. |
| **10** | **`SolHUD™` Zero-Allocation Telemetry, Pure-Kotlin ISO QR (`GL_NEAREST`) & Zero-Camera PNGTuber (`v1.5.0+`)** | Android 14–18 Single-App Capture hides floating pills so viewers can't see live FPS/temp; front cameras add `2–3°C` heat and require `CAMERA` permission; external QR libraries bloat APKs. | Renders `SolHUD™` (`480×72` = `138 KB` reused bitmap via `glTexSubImage2D`), pure-Kotlin ISO/IEC 18004 V1–10 Level M QR (`256×256` `GL_NEAREST`, up to 213 UTF-8 bytes), and 2-PNG PNGTuber (`512×512` max, driven at ~47 Hz by `vadSpeechActive` + 140 ms talk-hold & smooth talk-hop) straight in OpenGL above Privacy Blur (`~2.4 MiB` VRAM total when all 3 are ON; `0 MB` when OFF). |
| **11** | **Offscreen RGBA8 FBO 1-Tap Clean Thumbnail + 4 Hardware-Clamped Studio Profiles (`v1.5.0+`)** | Calling `glReadPixels()` directly on `MediaCodec` surfaces returns solid black on Samsung Exynos/Adreno write-only gralloc buffers and stalls 60 FPS encoding; switching Wi-Fi/4G/Shorts manually takes 6+ taps. | Renders a temporary `<=1920×1080` (`<=7.91 MiB`) RGBA8 FBO *after* `eglSwapBuffers()`, destroys it immediately (`0 MB` retained VRAM), flips rows in-place off-thread, and saves `quality = 92` JPEG to `Pictures/SolStream/` via scoped `MediaStore` (`IS_PENDING = 1 → 0`, 3s cooldown). Pairs with 4 secret-free, hardware-clamped Studio Profiles in Card 2. |
| **12** | **100% Google Play Policy Cleanliness + Pinned Reproducible NDK & `16 KB` `zipalign` CI (`52` Tests)** | Play Store flags `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, camera/storage bloat, unverified native downloads, and non-16 KB aligned APKs (enforced for API 35+ updates). | Declares zero restricted battery/camera/storage permissions, explicitly sets `isAccessibilityTool="false"`, pins NDK `28.2.13676358` and Xiph RNNoise commit `6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`, verifies `zipalign -c -P 16` + `readelf` ELF segments, and passes **52 JUnit 4 tests** on every build. |

---

## 5. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Long-Stream Protection & Android 13–18 Setup (2 Minutes)

1. **Prevent 3-Hour Background OS Kills (Important on 8 GB Phones!):**
  - **All Android Phones:** Tap **`Protect`** on the top **Long-Stream Protection** banner (or Step 2 in Card 1) → **Battery** → select **`Unrestricted`**. Once set, the banner turns green (`Protected ✓`) and its **`Tips`** button opens your brand's anti-kill checklist.
  - **Samsung One UI (`Galaxy S24 FE`, S23/S24/S25, A-Series, etc.):**
    1. Open **Settings → Device Care → Memory → Excluded apps** → tap **`+`** and add **both `SolStream Studio` and your game (e.g., `Call of Duty`)** permanently. *(This stops Samsung's automatic RAM cleaner from killing SolStream during 3+ hour streams and costs zero battery when idle.)*
    2. On 8 GB phones, open **Settings → Device Care → Memory → RAM Plus** → toggle **OFF** and restart once to prevent background flash-swap hitches mid-match.
    3. If using a 25W+ USB-PD PPS charger, enable **Game Booster → Pause USB PD charging when gaming** for `2–4°C` cooler sessions (reported accurately as **`Bypass Charging (USB-PD)`**).
  - **Xiaomi / Redmi / POCO (HyperOS / MIUI):** Set Battery Saver to **No restrictions**, enable **Autostart**, and **lock SolStream Studio in Recents**.
  - **OnePlus / Oppo / Realme / Vivo / iQOO:** Allow **Background Activity / High Background Power Consumption**, lock SolStream in Recents, and on Vivo/iQOO keep **Game Frame Interpolation OFF** (use Native 90/120 FPS).
2. **Enable Assistive Broadcast Dock (Google Play Store & Sideloaded APK):**
  - **Play Store install:** Tap **`3. Tap Accessibility 1st`** in Card 1 → tap **Accept & Open Settings** → switch **SolStream Studio** **ON** in 1 tap.
  - **Sideloaded GitHub APK (Android 13–18):**
    1. Tap **`3. Tap Accessibility 1st`** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK**.
    2. Return and tap **`4. Unlock Restricted (⋮)`** → tap **`⋮`** at top-right (or **scroll to the bottom of App Info on Xiaomi/POCO/Vivo**) → tap **Allow restricted settings**.
    3. Return to Accessibility and switch **SolStream Studio** **ON**. Once all 4 checks are `READY`, Card 1 auto-collapses cleanly.
3. **Earphones & External Mics (`Auto` 12-Mode Matrix):**
  - **Bluetooth Neckbands & Earbuds (`realme Buds Wireless 5 ANC`, etc.):** Keep `Mic Input` on **`Auto (Phone Studio Mic Locked)`**. Your Bluetooth earphones stay in `100%` HD Stereo (`A2DP`) for footsteps while SolStream captures your phone's `48 kHz` studio mic (`PHONE_STUDIO` mode) with hardware Speaker AEC strictly disabled.
  - **Wired `3.5mm` & `USB-C` Mics:** Plugging in a wired or USB-C mic automatically switches `SolLuxe™ 3.0 Pure` to **`WIRED_USB_CLOSE`** mode (`+5 Hz` HPF shield, lighter chest boost, deeper `280–355 Hz` proximity mud cleanup).

---

### Step B: Pre-Stream Dashboard (Top-to-Bottom Guide)

#### Fixed Obsidian-Glass Header & 3-Tile Hero Summary Bar
- **Pinned Header:** Full-bleed **Solar Eclipse `'S'`** icon + `SolStream Studio (SolApex™ HyperStream • v1.5.1)` + 3 glanceable live chips (**Audio Preset**, **Res/FPS/Codec**, and **Protected ✓**) + visual **📖 User Guide & Privacy**, `Copy Preset`, and `Import Preset` (with Smart Cross-Phone Hardware Clamping).
- **Live Protect Long Streams Banner & Telemetry Banner:** Monitors battery-unrestricted status in real time alongside live pre-stream `Idle Temp °C`, `SoC Load %`, `Free RAM GB`, and power state (`Battery`, `Bypass Charging (USB-PD)`, `AC Fast Power`, etc.).
- **3-Tile Hero Summary Bar:** Tap **`[ 🎥 1440p (2K) ]`**, **`[ ⚡ 60 FPS ]`**, or **`[ 📡 20 Mbps ]`** directly at the top anytime.

#### Card 0 — Smart Device Tier Presets (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`)
- Configures optimal resolution, FPS, bitrate, and `SOL_LUXE` 6-stage studio audio for your device while respecting your selected Platform (`YouTube` vs `Twitch 6M` vs `Kick 8M`) and preserving explicit manual HPF overrides (`hpfAutoMode = false`).

#### Card 1 — Pre-Flight Readiness & Setup
- Auto-collapses once all 4 checks are `READY`, shows live Audio Route + Media Volume + `5 GHz Wi-Fi + BT` coexistence status, and provides the **All-Brand Anti-Kill & Setup Guide**.

#### Card 2 — Studio Profiles, Stream Key Vault & Simulcast (`SolRelay Omega™`)
- **4 One-Tap Studio Profiles (`Wi-Fi Max` | `Mobile Data` | `Shorts 9:16` | `Custom`):** Automatically adapted and clamped to your phone's verified hardware capabilities out of the box. Single-tap any slot to switch setups instantly; **long-press** any slot to save & rename your current setup or reset to hardware default (Stream Keys are **never** saved inside profiles).
- **4-Pill Platform Bar (`YouTube` | `Twitch` | `Kick` | `Custom`):** Auto-configures official ingest URLs and platform caps (`1080p60 H.264 6M 160k` on Twitch; `8M` on Kick).
- **Hardware Keystore Vault & Smart Paste Guard:** AES-256-GCM encrypted locally, auto-saved in `onPause()`, strips invisible whitespace, and routes accidental URL pastes into the server box.
- **Collapsible Simulcast & 9:16 Shorts Drawer + 5-Metric Network Analyzer:** Pasting a Shorts key into Slot #2 automatically enables **`Use Slot #2 Key for 9:16 Vertical Shorts`**, while the built-in analyzer tests Download Mbps, Sustained Upload Mbps, Ingest TCP RTT & Jitter, Loaded Router Bufferbloat, and Platform-Aware Safe Bitrate.

#### Card 3 — Video Engine (`SolCore Ultra™` — `360p–4K60` • `0.5–100M`)
- **Direct-Tap Segmented Pill Rows:** Resolution (`360p`–`4K`), Hardware Codec (`HEVC` / `H.264` / `AV1`), Frame Rate (`30` / `60 FPS`), and Framing (`FIT` / `STRETCH 16:9` / `SMART` / `FILL`).
- **Stackable Visual Filters:** `Cinematic Filmic`, `AMOLED Vivid Pop`, `Adaptive Dark-Corner Boost`, `Color Temp`, and `GPU Crisp Sharpen`.
- **Collapsible Pro Overlays, Scenes, Privacy Blur & `SolCodec™` Drawer:**
  - **`SolHUD™` Live GPU Telemetry Badge:** Opt-in live `FPS • °C • Mbps` badge (`TL / TR / BL / BR`) visible even during Android 14–18 Single-App Capture.
  - **Offline Corner QR Code:** Enter a UPI link, YouTube channel URL, or Discord invite (up to 200 chars / 213 UTF-8 bytes), toggle ON/OFF, and pick any corner (`TL / TR / BL / BR`).
  - **Zero-Camera 2-PNG PNGTuber Avatar:** Pick custom Idle & Talking PNGs (or use the built-in gender-matched **Obsidian-Glass Cyber Visor Esports Avatar**), toggle ON/OFF, pick any corner, and adjust size (`12%–30%`).
  - **Custom Scenes, Logo, Shorts Canvas, Text Banner & Dual Privacy Blur:** Unified **`Logo:OFF → FULL → TL → TR → BL → BR`** `.PNG` overlay control, **`9:16 Shorts Canvas`** (`DARK — 0% Extra GPU`, `POSTER — Static Image`, `BLUR — 40% Cooler Scissor Blur`), live GPU text banner, `Auto-Blur Keyboard`, 8 HUD blur zones, and silicon controls (`OpenGL ES 3.0`, `HDR10`, `ROI`, `Keyframe/B-Frames/QP`).

#### Card 4 — `SolLuxe™ 3.0 Pure` Voice Studio & 3D Game Audio
- **1-Tap Gender Profile (`Neutral 68Hz` | `Male 55Hz` | `Female 85Hz`):** Auto-matches HPF, Condenser EQ pockets, gender-scaled Leveler sidechain (`110/140/180 Hz`), De-Esser center (`6.2/6.7/7.2 kHz`), and the built-in PNGTuber Cyber Visor color accent while preserving explicit manual HPF choices.
- **6 Voice Presets:** `SOL_LUXE` (Pure Condenser), `RADIO STUDIO` (Punchy Cut-Through), `VELVET` (Warm Natural), `CLEAN` (Transparent Natural), `NEURAL` (C RNNoise Pure), and `RAW` (Dry Mic Bypass).
- **SeekBar Volume Sliders (`−10% / +10%`):** Adjust Post-DSP Mic Boost (`50%–250%`) and Internal Stereo Game Mix (`0%–250%`), plus stackable `Cinema 3D Wide`, `Theater Sub-Punch`, `Footstep Focus`, `Blast Tamer`, and the **10-Second Studio Voice Test & Auto-Advisor**.

#### Card 5 — Launch Broadcast, Clips & Reports (`Launch Deck`)
- **`GO LIVE — START STREAM` ↔ `⏹ STOP LIVE STREAM`** (with 2-tap stop confirmation), **`Session Reports & History`** (highlights `⚡ Recovered 60s` if a previous stream was cut by the OS and keeps completed reports frozen), **`★ Saved Clips & AI Highlights`**, **`Benchmark Other App`**, **`Refresh Mics / HW Scan`**, **`Check GitHub Update`**, and **`❤️ Subscribe to @Sol_CODM`**.

---

### Step C: In-Game 3-Tab Floating Pill & Safe Notification Shade

- **Short Closed Pill:** Displays **`● 05:36 | 17.6M | 60fps | Mic:ON | 38.4°C`** (sleeps at `38%` opacity, `0 Hz` idle redraws, `FLAG_SPLIT_TOUCH`).
- **Expanded 3-Tab Pill (Zero-Truncation Quick Bar):**
  - **Top Quick Bar:** `[ Mic:ON ] [ Game:ON ] [ LIVE ] [ ★ Clip ] [ ✕ ]` (fixed-width `✕` prevents any text cut-off on narrow screens).
  - **Tab 1 (`🎙️ Audio`):** Voice Profile, Preset, Denoiser Mode/Level, Auto/Manual HPF, EQ/De-Ess/Warm/AGC toggles, Mic/Game volume steps, 3D Soundstage, and Mic Hardware switcher.
  - **Tab 2 (`🎬 Video`):** Scene, Framing, Zoom, Upscaler, Sharpen, 180° Flip, Stackable Color Filters, unified **`Logo:OFF → FULL → TL → TR → BL → BR`** button, Banner, **`📸 Shot` (1-Tap Clean Thumbnail)**, **`HUD:OFF`**, **`QR:OFF`**, **`Avatar:OFF`**, Minimap Blur, and Keyboard Blur.
  - **Tab 3 (`📡 Bitrate/Net`):** Live Bitrate steps, ABR toggle, `30FPS Cool` (with `SEVERE` live-temp toast guard), Stream/Game FPS, Queue/RTT, live **`🔥 Heat: App ~CPU | Game+OS ~SoC`** & **`💾 RAM: App MB | Game+OS GB`**, target status, and 2-tap **`⏹ Stop Stream`**.
- **Safe Notification Bar (3 Actions Only):** Displays **`Mute Mic`**, **`Scene/BRB`**, and **`★ Clip`** — `Stop` is intentionally omitted from the notification shade so swiping notifications never ends a marathon stream accidentally.

---

## 6. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Ultra `v1.5.1` Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **6-Stage `SolLuxe™ 3.0 Pure` Condenser Chain** | Shure SM7B, Neumann U87, Neve 1073 & FabFilter Pro-C2 / Pro-DS | Non-overlapping 64-bit EQ pockets (`95/130/172 Hz` warmth, `315–365 Hz` mud cut, `920 Hz` dip, `4.5–5.1 kHz` sheen, `8.5/9.0 kHz` air shelf), `<0.6 ms` raised-cosine impulse clamper, `8 ms / 220 ms` Hybrid VAD expander, gender-sidechain soft-knee leveler, and post-comp sibilance-ratio de-esser. |
| **Phase-Safe `62 Hz` Sub-Punch + `250 Hz + 2.45 kHz` Footsteps + `4×` Limiter** | Dolby Atmos Gaming & FabFilter Pro-L2 | Boosts `62 Hz` game sub-bass (`+3.0 dB`) when silent and ducks to `0.0 dB` neutral during speech, lifts `250 Hz` indoor thuds + `2,450 Hz` crunch, and protects `250%` game mix through a 64-sample look-ahead `4×` Catmull-Rom True-Peak Limiter (`-1.16 dBTP`). |
| **`SolCore Ultra™` ES 3.0 `Fast-FP16` + Gated Neighbor Shader + `Surface.setFrameRate()`** | Desktop OBS Studio + AMD CAS + Android 11–18 SurfaceFlinger | Uses `mediump` FP16 registers, gates cardinal neighbor fetches (`14.74M` fewer reads/frame when Sharpen/Shadow Boost are OFF), paces capture in hardware, and preserves steady 2-second CFR keyframes. |
| **`SolHUD™`, Offline Corner QR, Zero-Camera PNGTuber, Clean Thumbnails & 4 Studio Profiles** | OBS Profiles & Overlays + VTube Studio / PNGTuber Plus + NVIDIA ShadowPlay | Pure-Kotlin ISO/IEC 18004 QR (`GL_NEAREST`), reused `138 KB` HUD texture (`glTexSubImage2D`), voice-reactive 2-PNG avatar (`vadSpeechActive`, zero `CAMERA`), temporary offscreen RGBA8 FBO `<=1080p` JPEG thumbnail (`Pictures/SolStream/`), and 4 secret-free hardware-clamped Studio Profiles. |
| **Adaptive `64 KB / 4 KB` RTMP Chunking + $\mu\text{s} \rightarrow \text{ms}$ RTT Bridge + `Wi-Fi ↔ 5G` Failover** | OBS RTMP Tuning + IRL Pro / Cloudflare Speed | Converts Pedro `getRtt()` microseconds to true milliseconds, disables false ICMP disconnects (`setCheckServerAlive(false)`), adapts write chunk size to network & destination, and switches `Wi-Fi ↔ 5G` in `0.3s`. |

---

## 7. For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
- **JDK, Gradle & Pinned NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + AGP `8.13.2` (with R8 `9.5.26` for Kotlin `2.4.20`) + `compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`, `versionCode = 13`, `versionName = "1.5.1"` + **Pinned Android NDK `28.2.13676358`** (`CMake 3.22.1`, `-DANDROID_STL=none`, `-O3 -std=gnu11 -Wno-null-dereference`, `ndk.debugSymbolLevel = "SYMBOL_TABLE"`).
- **Cryptographic Source Pinning, 16 KB `zipalign` + ELF Alignment & 52-Test JUnit Suite:** Fetches and verifies exact Xiph `RNNoise v0.1.1` commit **`6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`**, enforces `-Wl,-z,max-page-size=16384` + `useLegacyPackaging = false`, verifies **`zipalign -c -P 16 -v 4`** on the signed Release APK alongside `readelf -lW` across all `arm64-v8a` and `x86_64` `.so` libraries and `apksigner` V3 checks, runs strict Android Lint (`abortOnError = true`) and fresh un-cached execution (`--rerun-tasks`) of all **52 JUnit 4 unit tests** (`SolStreamUnitTests.kt`), and uploads both `SolStream-Studio-v1.5.1.apk` and `SolStream-Studio-v1.5.1-PlayStore.aab`.

### The 6-Stage `SolLuxe™ 3.0 Pure` Vocal Architecture (`MicDspProcessor.kt`)

| Stage | Module Name | Exact Technical Specification (`48,000 Hz`, 64-Bit `Biquad64`, Atomic Frame-Boundary Config) |
|---|---|---|
| **Stage 1** | **64-Bit Butterworth HPF** | 2nd-order `Q = 0.707`: **`55 Hz` (`MALE`)**, **`68 Hz` (`NEUTRAL`)**, **`85 Hz` (`FEMALE`)** (`+5 Hz` `WIRED_USB_CLOSE`, `+10 Hz` `BLUETOOTH_SCO`, `+15 Hz` `SPEAKER_AEC`). Explicit manual Hz cutoffs (`hpfAutoMode = false`) are never overwritten by preset or profile switches. |
| **Stage 2** | **Raised-Cosine Micro-Impulse Clamper** | Tracks local `20 ms` RMS (`960 samples`). Triggers only on sub-millisecond spikes `<0.6 ms` ($\le 29$ samples) exceeding **`3.8×`** local RMS; clamps cleanly and recovers via a smooth **`2.0 ms` (`96-sample`)** raised-cosine ramp so `10–30 ms` `"P/T/K"` consonants stay crisp. |
| **Stage 3** | **Native C `RNNoise` + Hybrid `16 kHz`-Safe Neural-VAD Expander** | Stack-allocated `GetFloatArrayRegion` JNI (`480 samples`, `480-sample` dry delay, hardware `FZ` guards; `65% / 88% / 100%` true `1.00f` wet). Hybrid trigger opens on `vadProb >= 0.35f` OR `200–2500 Hz` RMS `>= +6 dB` (`2.0×`) above adaptive floor. Smooth **`8.0 ms` attack**, **`220 ms` release**, **`140 ms` hold**, soft **`-14 dB` (`0.20×`)** floor (`-18 dB` / `0.126×` speakerphone + double-talk-safe bleed ducking). |
| **Stage 4** | **Split-Band Dynamic Plosive Guard** | Causal 2-stage `<115 Hz` low-pass monitor vs. `420 Hz (Q = 1.05)` vocal-core detector. Ducks **only** the `<115 Hz` band by up to `-9 dB` when wind-to-vocal ratio exceeds `2.5×`, passing deep vocal fundamentals untouched before Stage 5. |
| **Stage 5** | **4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth Shaper** | Strictly non-overlapping `Biquad64` pockets: Chest Warmth (`95/130/172 Hz`, `Q=0.85` M/N, `Q=1.0` F), Surgical Mud Cut (`315/340/365 Hz`, `Q=2.0`), Anti-Nasal (`920 Hz`, `Q=1.8`), Neve Silk Presence (`4.5/4.8/5.1 kHz`, `Q=1.15`; or `2.9/3.2/3.5 kHz @ +1.6 dB` in `RADIO STUDIO`), and Clean Air Shelf (`8.5/9.0 kHz`, `Q=0.707`; or real `5.2 kHz` 2nd-order Butterworth LPF on `BLUETOOTH_SCO`). Low-band cubic analog warmth (`0.09 / 0.08 / 0.06f`; `tube2fDrive = 0.0f` on Neutral & Female). |
| **Stage 6** | **VAD-Synced Soft-Knee Leveler + Dynamic De-Esser + `4×` True-Peak Limiter** | **Leveler:** Gender-matched HPF sidechain (`110/140/180 Hz`), `10 ms` attack / `130 ms` release, single continuous `6 dB` soft-knee curve (`2.5:1` default / `3.2:1` Radio / `2.0:1` Velvet above `-16 dBFS`) + up to `+3.5 dB` quiet-speech lift (`-38..-22 dBFS`) scaled by continuous `vadProb` ramp (`0.25 → 0.55`) × smoothed expander gain + speech-synced slow AGC.<br>**De-Esser:** Post-EQ/Post-Comp dynamic `6.2 / 6.7 / 7.2 kHz` (`Q=2.0`, max `-4.5 dB`) sibilance-to-vowel ratio check.<br>**Output:** Post-DSP Mic Boost (`50–250%`) → zero-allocation **`4×` Catmull-Rom Inter-Sample True-Peak Limiter at `-1.16 dBTP` (`0.875`)** + `4.5 ms` Low-End CPU Auto-Bypass watchdog. |

### Source Tree

~~~text
.
├── PRIVACY_POLICY.md                        # Public Google Play Console Privacy Policy, Data Deletion & Accessibility Disclosure
├── COMMERCIAL-LICENSE.md                    # Creator Broadcasting Exception & Commercial Redistribution Terms
├── README.md                                # Complete v1.5.1 User Manual & Engineering Reference
├── .github/workflows/build.yml              # Pinned NDK 28.2.13676358, Pinned RNNoise Commit, 16KB zipalign + ELF CI
└── app/
    ├── build.gradle.kts                     # v1.5.1 (versionCode 13) + Pinned NDK + SYMBOL_TABLE + 16 KB JNI packaging
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
        │       ├── MainActivity.kt          # Obsidian-Glass UI, 4 Studio Profiles, Auto-Shorts Key, Per-Mode Canvas & Guide
        │       ├── SolStreamService.kt      # Adaptive Chunking, Severe Toast Guard, Avatar Sync, Cumulative Drops, 60s Checkpoint
        │       ├── SolVideoPipeline.kt      # ES 3.0 Fast-FP16, Unified 16:9 & 9:16 Vulkan Guard, Overlays & Offscreen FBO Shot
        │       ├── SolSceneAssetStore.kt    # Thread-safe Atomic Asset Store + Built-In Gender-Matched Cyber Visor Avatar
        │       ├── SolDualAudioEngine.kt    # AudioWorkerBarrier, 4-Tier PCM16/Resampler Fallback, Headphone-Aware AEC, 3D Game Bus
        │       ├── MicDspProcessor.kt       # SolLuxe™ 3.0 Pure 6-Stage Engine, Atomic DspConfigSnapshot, 4x True-Peak Limiter
        │       ├── SolNativeRnnoise.kt      # Safe Kotlin JNI wrapper for libsol_rnnoise.so
        │       ├── CodecCapabilityProbe.kt  # eligibleCompanionEncoders(), Bypass PD Classifier, 5000ms Scan Gate, SoC/GPU Mapper
        │       ├── KeystoreVault.kt         # Non-Destructive AES-256-GCM Vault, Schema v5, 4 Studio Profiles, Cross-Phone Clamping
        │       ├── DiagnosticsLogger.kt     # Frozen Post-Stop v1.5.1 Reports, Smart Benchmark Header, 60s Checkpoint & ★ Clips
        │       └── recorder/
        │           └── SolHudAndQrRenderer.kt # Pure-Kotlin ISO QR (V1–10 M), Zero-GC SolHUD, Corner MVP & MediaStore Saver
        └── test/java/com/solgaming/solstreamstudio/
            └── SolStreamUnitTests.kt        # 52-test JUnit 4 suite (DSP, Barrier, QR, MVP, FBO, Profiles, Bypass PD, Resize Guard)
~~~

### Build & Release Verification Command

~~~bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37,37.0,38,38.0 --no-daemon --stacktrace
~~~

---

## 8. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`, **ALWAYS** call `startForeground()` before any early return in `startLiveBroadcast()`, **ALWAYS** await `activeTeardownLatch` off the main thread with an explicit timeout abort, and **ALWAYS** call `DiagnosticsLogger.lockFinalMicStatusBeforeStop()` + `videoPipeline.prepareForImmediateStop()` synchronously before async teardown.
2. **NEVER** mutate `Biquad64` coefficients from the UI thread (`MainActivity` / Floating Pill)—publish an immutable `DspConfigSnapshot` atomically and apply filter updates strictly on the audio thread at `10 ms` frame boundaries.
3. **NEVER** call `glReadPixels()` directly on a `MediaCodec` `InputSurface` (always render into a temporary offscreen RGBA8 FBO *after* `eglSwapBuffers()`, restore `glBindFramebuffer(0)` + `glViewport` in `finally`, and delete the FBO immediately), and **NEVER** allocate new `Bitmap` objects per frame inside the OpenGL render loop (`SolHUD™` must reuse its scratch bitmap via `glTexSubImage2D`).
4. **NEVER** enable `setCheckServerAlive(true)` on Pedro's `RtmpClient` (ICMP probe causes false firewall disconnects), **NEVER** release shared audio engine resources until `AudioWorkerBarrier` confirms both `SolFloatAudioMixer` and `SolAacDrain` have exited, and **NEVER** allow `VOICE_COMMUNICATION` / hardware `AcousticEchoCanceler` when headphones are connected (`computeEffectiveSpeakerAec`).
5. **NEVER** open test `MediaCodec` encoder instances inside `CodecCapabilityProbe.scanDevice()` while `SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive`, **NEVER** poll `PowerManager.getThermalHeadroom()` faster than once every `10.5 seconds` (even when `NaN`), and **NEVER** resize `VirtualDisplay` while `!capturedContentVisible` (on either `16:9` or `9:16`) or to portrait dimensions during a `16:9` landscape stream.
6. **NEVER** re-introduce `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`, `SYSTEM_ALERT_WINDOW` (prior to Phase 2 Offline Recorder), `AudioManager.setCommunicationDevice()`, `-ffast-math` in `CMakeLists.txt`, or CPU software video encoders (`c2.android.av1.encoder`).

---

## 9. Privacy Policy, Google Play Compliance & Open-Source Licenses

- **Privacy Policy & Zero Data Collection:** See [**`PRIVACY_POLICY.md`**](./PRIVACY_POLICY.md) for our full Google Play Store Privacy Policy, Data Retention & User Deletion controls, and `AccessibilityService` disclosure (`android:isAccessibilityTool="false"`, `android:canRetrieveWindowContent="false"`, `android:canPerformGestures="false"`, zero personal data collection, zero analytics/ad SDKs).
- **Created & Maintained By:** **Sol Studio (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
- **Dual Licensed:**
  - **Free for Gamers, Monetized Creators & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)** with an explicit **Additional Creator Broadcasting Permission** in **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)** covering monetized YouTube, Twitch, Kick, and Shorts channels.
  - **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
- **Open-Source Credits:** Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`, zero root access required), neural voice denoising uses [`xiph/rnnoise`](https://github.com/xiph/rnnoise) (`BSD-3-Clause`), and UI/async foundations use AndroidX, Material Components & Kotlin Coroutines (`Apache-2.0`).
