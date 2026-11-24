# SolStream Studio • ULTRA (`v1.5.2`)

**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (`~5.9 MB` Signed Release APK • `~6.0 MB` Google Play `.aab`), **no-root**, zero-ad Android live-streaming broadcast studio powered by the custom **`SolApex™ HyperStream Engine`** (**`SolCore Ultra™ Video Engine`** • **`SolLuxe™ 3.0 Pure Audio Engine`** • **`SolRelay Omega™ Broadcast Engine`** • **`SolCodec™ Hardware Silicon Layer`**). It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 6-stage 32-bit float / 64-bit double pure condenser vocal commentary with a 12-Mode Hardware Acoustic Matrix**, **opt-in zero-camera GPU broadcast overlays (`SolHUD™`, Offline Corner QR, 2-PNG PNGTuber & 1-Tap Clean Thumbnails)**, **4 One-Tap Studio Profiles**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, Kick, and Custom RTMP/RTMPS servers**.

> **Unrooted Stock Android (`minSdk 29` Android 10+ • `targetSdk 36` Android 16 • `compileSdk 37`):** Built for normal, unrooted Android 10+ phones, foldables, and tablets right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on high-speed Fiber. Root access is **never** required or used; zero restricted battery-exemption, camera, or broad storage permissions are declared; and `allowBackup="false"` is paired with explicit `data_extraction_rules.xml` exclusions. *(Final Play Store eligibility also depends on completing Play Console Data Safety, Accessibility, and Foreground Service declarations during submission.)* See our [**Official Privacy Policy (`PRIVACY_POLICY.md`)**](./PRIVACY_POLICY.md).

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, touch response, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolApex™ HyperStream Engine v1.5.2`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0 (`Fast-FP16`) / ES 2.0 Direct-TBDR frame-interval pacer (`SolCore Ultra™`), Hardware TMU-assisted 5-tap Bicubic/Lanczos upscaler with gated cardinal neighbor texture fetches (`14.74 million` fewer texture reads/frame at `1440p60`), unified `16:9` & `9:16` **Graphics/Gralloc PSS Resize Guard** (`~180–258 MB` flat PSS across 3+ hour streams), scissored `glScissor` `9:16 BLUR` / `0%` extra GPU `DARK` / static `POSTER` Shorts canvas modes, `STRETCH (Full 16:9 Zero-Bars)` aspect scaler, 5-position aspect-preserving `.PNG` corner logo compositor (`Logo:OFF → FULL → TL → TR → BL → BR`), **`SolHUD™` zero-allocation live GPU telemetry badge** (visible to viewers even in Android 14+ Single-App Capture), **pure-Kotlin ISO/IEC 18004 offline Corner QR generator (`GL_NEAREST`)**, **zero-camera 2-PNG PNGTuber avatar** with built-in **Masked Esports Striker** portraits, **temporary offscreen RGBA8 FBO 1-Tap Clean Screenshot** (`Pictures/SolStream/` via zero-permission `MediaStore` + AndroidX `ExifInterface`), **4 hardware-clamped One-Tap Studio Profiles**, Dual-Layer Privacy Blur Shield (8 HUD zones + orientation-safe `Auto-Blur Keyboard`), Adaptive Dark-Corner Clarifier, isolated **`SolLuxe™ 3.0 Pure` 6-stage vocal & game soundstage DSP** (thread-safe atomic frame-boundary coefficient updates, automatic **4-Hardware × 3-Gender `12-Mode` Acoustic Matrix**, **`55 / 68 / 85 Hz` Auto/Manual 64-Bit Butterworth HPF**, **`<0.6 ms` Raised-Cosine Micro-Impulse Clamper**, `16 KB`-aligned Native C `48 kHz` Xiph `RNNoise` with hardware Flush-to-Zero `FZ` register guards + **`16 kHz`-safe Hybrid Neural-VAD Smooth Expander** with speech-and-mute-frozen noise floor, **Split-Band Dynamic Plosive Guard**, **zero-overlap Condenser EQ + Low-Band Analog Warmth Shaper**, real **`5.2 kHz` Bluetooth SCO Hiss LPF**, **Gender-Sidechain Soft-Knee Broadcast Leveler with continuous VAD-synced quiet-speech lift**, **Post-Comp Dynamic Sibilance-Ratio De-Esser**, and **`4×` Catmull-Rom Inter-Sample Peak Limiter at `-1.16 dBTP`**), universal 4-tier microphone capture fallback (`48 kHz PCM_FLOAT → 48 kHz/44.1 kHz/16 kHz PCM_16BIT` phase-continuously resampled to `48 kHz`), silence-safe `isClientSilenced` + `15s` cooldown mic auto-recovery, headphone-aware effective Speaker AEC guard, exact-launched 2-worker audio cleanup barrier, drift-locked stereo game circular buffer (`±12 ms`), central **Platform-Compatibility Validator** (`Twitch / Kick / Single-Encode Simulcast` hard limits + `B-Frames = 0` RTMP safety), bounded transient video & AAC `CodecException` retries with clean fatal-error stop, simultaneous-instance-verified companion encoder selection & AC-gated **`Bypass Charging (USB-PD)`** telemetry (`SolCodec™`), smart thermal bitrate governor with `SEVERE` override protection, AES-256 Keystore non-destructive vault, Pedro `RootEncoder` $\mu\text{s} \rightarrow \text{ms}$ RTT conversion, `setCheckServerAlive(false)` + **Network-Adaptive RTMP Write Chunking (`65,535 B` Wi-Fi / `4,096 B` Mobile Data & Custom)**, bounded leased-slot `ArrayBlockingQueue<PacketSlot>` simulcast pool with per-client callback identity guards, off-main-thread `5s` teardown barrier, **frozen post-stop Session Reports + 60-second single-slot storage auto-checkpointing**, **Fast `Wi-Fi ↔ 5G` Network Handover** (`SolRelay Omega™`), and 0-Hz sleeping 3-tab Accessibility Ghost Pill with separated **App CPU % • Thermal Pressure % • RAM Split** telemetry are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini, ChatGPT, and Claude!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, `assembleRelease`, `bundleRelease`, `zipalign -c -P 16`, and APK + AAB `16 KB` ELF checks) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK or Play Store Bundle (`v1.5.2`)](#1-download--install-signed-release-apk-or-play-store-bundle-v152)
2. [How Human + Multi-AI Built This App](#2-how-human--multi-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-ultra-actually-do)
4. [The 12 Custom `SolApex™ v1.5.2` Engineering Innovations](#4-the-12-custom-solapex-v152-engineering-innovations)
5. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#5-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
6. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#6-global-broadcast-inspirations-windows-macos--ios---android)
7. [For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec](#7-for-android-developers--engineers-architecture-6-stage-solluxe-30-pure--build-spec)
8. [For AI Coding Assistants, Security Auditors & Forkers](#8-for-ai-coding-assistants-security-auditors--forkers)
9. [Privacy Policy, Google Play Compliance & Open-Source Licenses](#9-privacy-policy-google-play-compliance--open-source-licenses)

---

## 1. Download & Install Signed Release APK or Play Store Bundle (`v1.5.2`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.5.2.apk`** under **Assets** to download the full **Ultra Master Edition** (`~5.9 MB` signed release APK) directly onto your Android phone (or download **`SolStream-Studio-v1.5.2-PlayStore.aab`** for Google Play Console distribution). All earlier versions (`v1.0.2` – `v1.5.1`) remain permanently available in Releases.
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.5.2`** (`versionCode = 14`) straight over any earlier version (`v1.5.1` or older) as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. How Human + Multi-AI Built This App

This repository was engineered through a continuous verification loop between a competitive mobile gamer and frontier AI models auditing each other's code line-by-line:

- **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  - Validated builds across multi-hour (`98m–330m`) continuous `1440p60` (`16:9`) and `1080×1920@60` (`9:16` Shorts) HEVC Multiplayer and Battle Royale live streams on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e • Samsung Xclipse 940 (Vulkan)`, `2340×1080` 120 Hz display, 8 GB RAM), **realme Buds Wireless 5 ANC** neckband earphones, and 5 GHz Fiber Wi-Fi (`0` sender drops, `0` reconnects, `0 ms` enqueue queue, `~7%` app CPU, `180 MB` peak PSS on a 3h 12m Shorts marathon with two mid-stream phone-call `[CALL_SHIELD]` recoveries).
  - Surfaced real-world audio, thermal, and UX edge cases directly from live sessions—driving `v1.5.0`'s frozen reports, `SolHUD™`, offline QR, zero-camera PNGTuber, 4 Studio Profiles, and 1-Tap Clean Thumbnails; `v1.5.1`'s `Bypass Charging (USB-PD)` detection and unified `9:16` resize guard; and `v1.5.2`'s Play Store hardening, modular split (`SolFloatingPillController`, `SolRtmpTransportSupport`, `ui/DashboardDialogs`), and upgraded **Masked Esports Striker** default PNGTuber avatar.
- **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  - Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c`), the 6-stage thread-safe **`SolLuxe™ 3.0 Pure`** audio engine (`MicDspProcessor.kt` + `SolDualAudioEngine.kt`), **`SolCore Ultra™`** OpenGL ES 3.0 `Fast-FP16` video pipeline & offscreen FBO clean screenshot engine (`SolVideoPipeline.kt`), pure-Kotlin **`SolHudAndQrRenderer.kt`** (ISO/IEC 18004 QR + zero-GC `SolHUD™` + pure-Kotlin corner MVP math + `MediaStore` saver), **Masked Esports Striker** avatar generator + AndroidX `ExifInterface` asset store (`SolSceneAssetStore.kt`), **`SolCodec™`** concurrent-instance capability scanner & AC-gated Bypass PD classifier (`CodecCapabilityProbe.kt`), **`SolRelay Omega™`** adaptive-chunk RTMPS transport (`SolRtmpTransportSupport.kt`, `SolStreamService.kt`), modular **`SolFloatingPillController.kt`** and **`ui/DashboardDialogs.kt`**, **Obsidian-Glass** dashboard (`MainActivity.kt`), **Solar Eclipse `'S'`** adaptive vector icon (`ic_solstream_logo.xml`), and pinned CI/CD APK + AAB verification pipeline (`build.yml`).
- **Independent Line-by-Line Source Audits — `OpenAI ChatGPT` & `Anthropic Claude`:**
  - Audited live commits against Pedro `RootEncoder 2.8.1`, Xiph `RNNoise v0.1.1`, W3C biquad stability, and Android 10–17 API rules—driving `v1.5.2`'s highest-impact reliability and Play Store fixes: moving transient video & AAC `CodecException` retries inside drain loops, wiring `onFatalAudioError` to stop cleanly instead of silent video, gating `Bypass Charging (USB-PD)` strictly on `isAcPlugged`, centralizing Twitch/Kick resolution/bitrate/AAC/strict-CBR enforcement, locking `maxBFrames = 0` for RootEncoder `2.8.1` (`CTS = 0`), hardening mic auto-recovery against false digital-silence restarts (`isClientSilenced` + `15s` cooldown), freezing DSP noise-floor drift during sustained speech & mute, separating `App CPU %` from `Thermal Pressure %`, verifying sysfs SoC thermal zone `type`, removing unused `typeWindowStateChanged` from Accessibility XML, adding `data_extraction_rules.xml`, enforcing `SOL_REQUIRE_XIPH_RNNOISE=ON` + `.aab` `16 KB` checks in CI, and expanding the JUnit 4 suite to **62 tests**.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `45–180 MB` of camera masks, AdMob ads, GPS/Contact trackers, and coin timers; lock `1440p/HEVC` behind subscriptions; click or distort your voice when a game switches the mic HAL to `16 kHz`; or get silently killed by Android's background memory manager at hour 3.

**SolStream Studio Ultra (`SolApex™ HyperStream Engine v1.5.2`) solves all of those problems in a `~5.9 MB` (Release), unrooted, zero-ad studio:**

1. **Obsidian-Glass Dashboard, Solar Eclipse `'S'` Branding & Live "Protect Long Streams" Banner:**
   - Features the full-bleed **Solar Eclipse `'S'`** adaptive vector icon, 3 glanceable top studio chips (`SolLuxe 3.0` • `1440p60 • HEVC` • `Protected ✓`), a 3-Tile Hero Summary Bar (`[ 🎥 1440p (2K) ] [ ⚡ 60 FPS ] [ 📡 20 Mbps ]`), collapsible pro accordion drawers, and a zero-permission **Protect Long Streams** readiness banner with brand-aware anti-kill tips (Samsung, Xiaomi/POCO, Vivo/iQOO, OnePlus/Realme/Oppo, Pixel).
2. **Plays at 90–185 FPS on Your Screen, Streams Steady 60/30 FPS (`360p` to `4K60` • `0.5` to `100 Mbps`):**
   - Your display stays at its native `120 / 144 / 165 / 185 Hz` refresh rate while our OpenGL `frameIntervalNs` frame pacer and dedicated hardware encoders (`SolCodec™`) encode rock-steady Constant Frame Rate video with a strict keyframe cadence and a **3-Attempt Hardware Encoder & Resolution Fallback Ladder**. *(Default YouTube `1440p60 HEVC` uses `20 Mbps` for cooler mobile thermals and 40 Mbps Wi-Fi headroom; YouTube recommends ~24 Mbps and you can step up to `24–100 Mbps` in one tap.)*
3. **`SolLuxe™ 3.0 Pure` — 6 Non-Fighting Pure Condenser Stages (Zero "Prit-Prit" Clicks):**
   - Built into 6 mathematically separated stages so zero filters fight or cancel each other: `55 / 68 / 85 Hz` 64-bit Butterworth HPF → `<0.6 ms` Raised-Cosine Micro-Impulse Clamper → Native C `48 kHz` `RNNoise` + `16 kHz`-Safe Hybrid Neural-VAD Smooth Expander (`8 ms` attack / `220 ms` release, `-14 dB` floor, speech-and-mute-frozen noise floor) → Split-Band Dynamic Plosive Guard (`<115 Hz` wind vs. vocal core) → 4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth Shaper → Gender-Sidechain Soft-Knee Leveler + Dynamic Sibilance-Ratio De-Esser + `4×` Peak Limiter (`-1.16 dBTP`).
   - *Internal Game Audio Note:* Android's `AudioPlaybackCapture` captures eligible `GAME`, `MEDIA`, and `UNKNOWN` audio only when permitted by each individual game (`USAGE_VOICE_COMMUNICATION` is excluded by Android). If a game blocks internal playback capture, SolStream falls back cleanly to microphone audio.
4. **Pro Broadcast Overlays, 1-Tap Clean Thumbnails & 4 One-Tap Studio Profiles:**
   - Add an opt-in **`SolHUD™`** live GPU telemetry badge (`FPS • °C • Mbps` visible to viewers even in Android 14+ Single-App Capture), an **offline Corner QR code** (UPI / YouTube / Discord), or a **zero-camera 2-PNG PNGTuber avatar** (with a built-in gender-matched **Masked Esports Striker** portrait)—all using `0 MB` VRAM when OFF. Grab pill-free `1080p` YouTube thumbnails mid-match with **`📸 Shot`**, and swap setups in 1 tap with **4 hardware-clamped Studio Profiles**.
5. **Crash-Proof 60-Second Auto-Checkpoint, Frozen Post-Stop Reports & Honest Telemetry:**
   - Checkpoints live session telemetry and `★ Saved Clips` to a single dedicated storage slot every 60 seconds (never polluting your 9-slot session history), freezes finalized end-of-stream reports, separates **App CPU %** from **Android Thermal Pressure %**, accurately classifies AC-only **`Bypass Charging (USB-PD)`**, and cleanly stops the stream if an unrecoverable audio or video encoder fault ever occurs so viewers never sit on frozen or silent video.

---

## 4. The 12 Custom `SolApex™ v1.5.2` Engineering Innovations

| # | Custom `SolApex™ v1.5.2` Innovation | The Real-World Gaming Problem It Solves | How Our Custom Math & Silicon Engineering Works |
|---|---|---|---|
| **1** | **6-Stage Non-Fighting `SolLuxe™ 3.0 Pure` Condenser Chain + Speech/Mute-Frozen Noise Floor** | Hard `0-ms` gates caused `"prit-prit"` speech-onset clicks; long uninterrupted commentary in `LIGHT`/`HW_NS` modes or muting the mic could drift noise-floor estimators. | Publishes settings atomically via `DspConfigSnapshot` at `10 ms` frame boundaries; uses an `8 ms` / `220 ms` Hybrid Neural-VAD smooth expander (`-14 dB` floor), `<0.6 ms` raised-cosine micro-impulse clamping, strictly frozen noise-floor adaptation during active speech or mute, and non-overlapping 4×3 EQ pockets. |
| **2** | **`16 kHz`-Safe Hybrid Neural-VAD + Silence-Safe `15s` Cooldown Mic Recovery + Headphone-Aware AEC** | Quiet microphones outputting digital zero could trigger false audio restarts; leaving Speaker AEC enabled on earphones could hollow out vocal bass. | Opens the expander on `vadProb >= 0.35` OR `+6 dB` `200–2500 Hz` SNR; `shouldTriggerMicRecovery()` never restarts a healthy quiet mic when Android reports `isClientSilenced == false`, enforces a `15s` cooldown, and recovers quickly on real read errors or OS-silenced sessions; enforces `effectiveSpeakerAec = activeSpeakerAecEnabled && !headphonesConnected`. |
| **3** | **Universal 4-Tier Mic Capture Fallback (`LinearMonoResampler48k`) + Bounded AAC Retries & 2-Worker Barrier** | Strict OEM HALs reject `48 kHz PCM_FLOAT`; transient AAC errors could silently kill audio while video stayed `LIVE`; independent workers could double-release codecs. | Tries `48 kHz PCM_FLOAT` first, falling back through `48 / 44.1 / 16 kHz PCM_16BIT` via phase-continuous linear resampling (constant 1-input-sample causal delay); retries transient AAC `CodecException` up to `5×` inside the loop, tracks `droppedAudioFrames`, escalates fatal audio failures to stop the stream cleanly, and releases shared resources once via `AudioWorkerBarrier`. |
| **4** | **Central Platform-Compatibility Validator (`Twitch / Kick / Simulcast`) & B-Frames Locked to `0`** | Changing resolution, bitrate, codec, or VBR after selecting Twitch or Kick could break ingest limits; RootEncoder `2.8.1` writes RTMP `CTS = 0`, conflicting with reordered B-frames. | `KeystoreVault.enforcePlatformLimits()` enforces `H.264`, strict `CBR` (rejecting VBR fallback), `<=1080p`, `<=6 Mbps` (Twitch) / `<=8 Mbps` (Kick), `<=160k AAC`, `2s` IDR, and `HDR10 OFF` across UI pills, presets, profile imports, Go Live, and Floating Pill bitrate steps, and locks `maxBFrames = 0` everywhere. |
| **5** | **Network-Adaptive RTMP Write Chunking (`65,535 B` / `4,096 B`) + Per-Client Guards + Header-First Aux Connect** | `128-byte` RTMP chunks waste CPU at `20 Mbps`; `setCheckServerAlive(true)` causes false ICMP disconnects; auxiliary video frames could queue ahead of SPS/PPS headers on connect. | Disables ICMP probes (`setCheckServerAlive(false)`) in favor of RTMP Ping + `8.5s` handshake & `6s` stall watchdogs; uses `65,535 B` chunks on Wi-Fi/Ethernet for major ingests (`4,096 B` on Cellular/Custom); queues `setVideoInfo()` before flipping auxiliary `connected = true`, drains stale packets immediately on `stop()`, and isolates every `RtmpClient` via `PrimaryRtmpCallback` / `AuxRtmpCallback`. |
| **6** | **Bounded Video `CodecException` Retries, Off-Main-Thread `5s` Teardown, Frozen Reports & AC-Gated Bypass PD** | Transient video encoder hiccups previously exited the drain loop without notifying the service; USB/Wireless `NOT_CHARGING` could falsely report `Bypass Charging (USB-PD)`. | Retries transient video `CodecException` up to `5×` inside `runEncoderDrainLoop()` before escalating to `onFatalPipelineError`; awaits `activeTeardownLatch` off-thread (`5s` ceiling); freezes finalized reports; gates `Bypass Charging (USB-PD)` strictly on `isAcPlugged` (`15–98%` `NOT_CHARGING`/`DISCHARGING` or `<=250 mA` at `15–95%`); and verifies sysfs SoC thermal zone `type`. |
| **7** | **Simultaneous-Instance-Verified Companion Codec Selection (`eligibleCompanionEncoders`)** | Devices where H.264 supports 2 concurrent instances but HEVC supports only 1 could fail trying to open a second HEVC instance. | Filters companion candidates strictly to codecs reporting `maxConcurrentInstances >= 2` with 1080p support, falling back cleanly from HEVC to H.264 across `SolVideoPipeline` and `SolStreamService`. |
| **8** | **Slider-Independent `3D Game Soundstage` + `4×` Catmull-Rom Inter-Sample Peak Limiting** | High `Game Mix %` or `Mic Boost %` settings can create inter-sample peaks that clip AAC encoders even when individual samples stay below `0 dBFS`. | Evaluates 4× Catmull-Rom cubic inter-sample peaks (`t = 0.25, 0.50, 0.75`) at `-1.16 dBTP` (`0.875`) with `NaN` guards on both the vocal bus and the 64-sample look-ahead stereo master bus, paired with phase-safe `62 Hz` Sub-Punch and `250 Hz + 2.45 kHz` Footstep Focus. |
| **9** | **Gated Neighbor Shader (`14.74M` Fewer Reads/Frame) + Unified `16:9` & `9:16` Graphics/Gralloc PSS Resize Guard** | Unconditional neighbor texel reads waste GPU power when Sharpen/Shadow Boost are OFF; background focus blips can trigger `VirtualDisplay.resize()` graphics memory spikes. | Gates cardinal neighbor fetches strictly inside `if (uSharpen > 0.01 \|\| uShadowBoost > 0.5)`, paces CFR frames via the OpenGL render loop (`frameIntervalNs`), blocks hidden/unfocused resizes across both `16:9` and `9:16` plus portrait resizes on `16:9`, and skips `9:16` blur passes when zoomed gameplay fills `100%` height. |
| **10** | **`SolHUD™` Zero-Allocation Telemetry, Pure-Kotlin ISO QR (`GL_NEAREST`) & Masked Esports Striker PNGTuber** | Single-App Capture hides floating pills; front cameras add `2–3°C` heat and require `CAMERA` permission; external QR libraries bloat APKs. | Renders `SolHUD™` (`480×72` = `138 KB` reused bitmap via `glTexSubImage2D`), pure-Kotlin ISO/IEC 18004 V1–10 Level M QR (`256×256` `GL_NEAREST`, up to 213 UTF-8 bytes), and 2-PNG PNGTuber (`512×512` max, driven by `vadSpeechActive` with a stylized **Masked Esports Striker** default + optional `sol_avatar_*` drawables) straight in OpenGL (`0 MB` VRAM when OFF). |
| **11** | **Offscreen RGBA8 FBO 1-Tap Clean Thumbnail + AndroidX `ExifInterface` + 4 Studio Profiles** | Calling `glReadPixels()` directly on `MediaCodec` surfaces returns black on write-only gralloc buffers; framework `ExifInterface` triggers Lint warnings and misses flipped EXIF orientation. | Renders a temporary `<=1920×1080` (`<=7.91 MiB`) RGBA8 FBO *after* `eglSwapBuffers()`, destroys it immediately (`0 MB` retained VRAM), flips rows off-thread, saves `quality = 92` JPEG to `Pictures/SolStream/` via scoped `MediaStore`, uses AndroidX `ExifInterface 1.4.2` with `OOM`-safe imports, and pairs with 4 secret-free hardware-clamped Studio Profiles. |
| **12** | **Play-Store-Aligned Manifest/Policy + Strict Xiph RNNoise CI + APK & AAB `16 KB` Alignment (`62` Tests)** | Silent local RNNoise fallbacks or unaligned `.so` files inside `.aab` bundles can slip into Play releases; unused accessibility event flags attract Play review rejections. | Declares zero restricted battery/camera/storage permissions, sets `isAccessibilityTool="false"` with zero accessibility event subscriptions, adds `data_extraction_rules.xml`, enforces `SOL_REQUIRE_XIPH_RNNOISE=ON` in CI (pinned commit `6cbfd53eb348a8d394e0757b4025c6ded34eb2b6` + NDK `28.2.13676358`), verifies `zipalign -c -P 16` + `16 KB` ELF segments inside **both** `.apk` and `.aab`, and passes **62 JUnit 4 tests**. |

---

## 5. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Long-Stream Protection & Android 13–17 Setup (2 Minutes)

1. **Prevent 3-Hour Background OS Kills (Important on 6–8 GB Phones!):**
   - **All Android Phones:** Tap **`Protect`** on the top **Long-Stream Protection** banner (or Step 2 in Card 1) → **Battery** → select **`Unrestricted`**. Once set, the banner turns green (`Protected ✓`) and its **`Tips`** button opens your brand's anti-kill checklist.
   - **Samsung One UI (`Galaxy S24 FE`, S23/S24/S25, A-Series, etc.):**
     1. Open **Settings → Device Care → Memory → Excluded apps** → tap **`+`** and add **both `SolStream Studio` and your game** permanently. *(Stops Samsung's automatic RAM cleaner from killing SolStream during 3+ hour streams.)*
     2. On 8 GB phones, open **Settings → Device Care → Memory → RAM Plus** → toggle **OFF** and restart once to prevent background flash-swap hitches mid-match.
     3. If using a 25W+ USB-PD PPS charger, plug in **before** launching your game and enable **Game Booster → Pause USB PD charging when gaming** for cooler sessions (reported accurately as **`Bypass Charging (USB-PD)`**).
   - **Xiaomi / Redmi / POCO (HyperOS / MIUI):** Set Battery Saver to **No restrictions**, enable **Autostart**, and **lock SolStream Studio in Recents**.
   - **OnePlus / Oppo / Realme / Vivo / iQOO:** Allow **Background Activity / High Background Power Consumption**, lock SolStream in Recents, and on Vivo/iQOO keep **Game Frame Interpolation OFF** (use Native 90/120 FPS).
2. **Enable Assistive Broadcast Dock (Google Play Store & Sideloaded APK):**
   - **Play Store install:** Tap **`3. Tap Accessibility 1st`** in Card 1 → tap **Accept & Open Settings** → switch **SolStream Studio** **ON** in 1 tap.
   - **Sideloaded GitHub APK (Android 13+):**
     1. Tap **`3. Tap Accessibility 1st`** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK**.
     2. Return and tap **`4. Unlock Restricted (⋮)`** → tap **`⋮`** at top-right (or **scroll to the bottom of App Info on Xiaomi/POCO/Vivo**) → tap **Allow restricted settings**.
     3. Return to Accessibility and switch **SolStream Studio** **ON**. Once all 4 checks are `READY`, Card 1 auto-collapses cleanly.
3. **Earphones & External Mics (`Auto` 12-Mode Matrix):**
   - **Bluetooth Neckbands & Earbuds:** Keep `Mic Input` on **`Auto (Phone Studio Mic Locked)`**. Your Bluetooth earphones stay in HD Stereo (`A2DP`) for footsteps while SolStream captures your phone's `48 kHz` studio mic (`PHONE_STUDIO` mode) with hardware Speaker AEC strictly disabled.
   - **Wired `3.5mm` & `USB-C` Mics:** Plugging in a wired or USB-C mic automatically switches `SolLuxe™ 3.0 Pure` to **`WIRED_USB_CLOSE`** mode (`+5 Hz` HPF shield, lighter chest boost, deeper `280–355 Hz` proximity mud cleanup).

---

### Step B: Pre-Stream Dashboard (Top-to-Bottom Guide)

#### Fixed Obsidian-Glass Header & 3-Tile Hero Summary Bar
- **Pinned Header:** Full-bleed **Solar Eclipse `'S'`** icon + `SolStream Studio (SolApex™ HyperStream • v1.5.2)` + 3 glanceable live chips (**Audio Preset**, **Res/FPS/Codec**, and **Protected ✓**) + visual **📖 User Guide & Privacy**, `Copy Preset`, and `Import Preset` (with Smart Cross-Phone Hardware & Destination Clamping).
- **Live Protect Long Streams Banner & Telemetry Banner:** Monitors battery-unrestricted status in real time alongside live pre-stream `Idle Temp °C` (or `N/A`), `Thermal Pressure %`, `Free RAM GB`, and power state (`Battery`, `Bypass Charging (USB-PD)`, `AC Fast Power`, `USB Power`, `Wireless Power`).
- **3-Tile Hero Summary Bar:** Tap **`[ 🎥 1440p (2K) ]`**, **`[ ⚡ 60 FPS ]`**, or **`[ 📡 20 Mbps ]`** directly at the top anytime.

#### Card 0 — Smart Device Tier Presets (`Lite` | `Balanced` | `High` | `Flagship` | `Ultra`)
- Configures optimal resolution, FPS, bitrate, and `SOL_LUXE` 6-stage studio audio for your device while respecting your selected Platform (`YouTube` vs `Twitch 6M` vs `Kick 8M`) and preserving explicit manual HPF overrides (`hpfAutoMode = false`).

#### Card 1 — Pre-Flight Readiness & Setup
- Auto-collapses once all 4 checks are `READY`, shows live Audio Route + Media Volume + `5 GHz Wi-Fi + BT` coexistence status, and provides the **All-Brand Anti-Kill & Setup Guide**.

#### Card 2 — Studio Profiles, Stream Key Vault & Simulcast (`SolRelay Omega™`)
- **4 One-Tap Studio Profiles (`Wi-Fi Max` | `Mobile Data` | `Shorts 9:16` | `Custom`):** Automatically adapted and clamped to your phone's verified hardware and active destination limits. Single-tap any slot to switch setups instantly; **long-press** any slot to save & rename your current setup or reset to hardware default (Stream Keys are **never** saved inside profiles).
- **4-Pill Platform Bar (`YouTube` | `Twitch` | `Kick` | `Custom`):** Auto-configures official ingest URLs and enforces platform caps (`1080p60 H.264 CBR 6M 160k` on Twitch; `8M` on Kick).
- **Hardware Keystore Vault & Smart Paste Guard:** Synchronized AES-256-GCM encryption locally, auto-saved in `onPause()`, strips invisible whitespace, and routes accidental URL pastes into the server box.
- **Collapsible Simulcast & 9:16 Shorts Drawer + 5-Metric Network Analyzer:** Pasting a Shorts key into Slot #2 automatically enables **`Use Slot #2 Key for 9:16 Vertical Shorts`**, while the built-in analyzer tests Download Mbps, Sustained Upload Mbps, Ingest TCP Connect RTT & Jitter, Loaded Router Bufferbloat, and Destination-Aware Safe Bitrate.

#### Card 3 — Video Engine (`SolCore Ultra™` — `360p–4K60` • `0.5–100M`)
- **Direct-Tap Segmented Pill Rows:** Resolution (`360p`–`4K`), Hardware Codec (`HEVC` / `H.264` / `AV1`), Frame Rate (`30` / `60 FPS`), and Framing (`FIT` / `STRETCH 16:9` / `SMART` / `FILL`). Restricted options automatically dim and show clear feedback when Twitch, Kick, or Single-Encode Simulcast is active.
- **Stackable Visual Filters:** `Cinematic Filmic`, `AMOLED Vivid Pop`, `Adaptive Dark-Corner Boost`, `Color Temp`, and `GPU Crisp Sharpen`.
- **Collapsible Pro Overlays, Scenes, Privacy Blur & `SolCodec™` Drawer:**
  - **`SolHUD™` Live GPU Telemetry Badge:** Opt-in live `FPS • °C • Mbps` badge (`TL / TR / BL / BR`) visible even during Android 14+ Single-App Capture.
  - **Offline Corner QR Code:** Enter a UPI link, YouTube channel URL, or Discord invite (up to 200 chars / 213 UTF-8 bytes), toggle ON/OFF, and pick any corner (`TL / TR / BL / BR`).
  - **Zero-Camera 2-PNG PNGTuber Avatar:** Pick custom Idle & Talking PNGs (or use the built-in gender-matched **Masked Esports Striker** default portrait), toggle ON/OFF, pick any corner, and adjust size (`12%–30%`).
  - **Custom Scenes, Logo, Shorts Canvas, Text Banner & Dual Privacy Blur:** Unified **`Logo:OFF → FULL → TL → TR → BL → BR`** `.PNG` overlay control, **`9:16 Shorts Canvas`** (`DARK — 0% Extra GPU`, `POSTER — Static Image`, `BLUR — Scissored Top/Bottom Blur`), live GPU text banner, orientation-safe `Auto-Blur Keyboard`, 8 HUD blur zones, and silicon controls (`OpenGL ES 3.0`, `HDR10`, `ROI`, `IDR Keyframe 2s/1s | B-Frames: 0 (RTMP Safe)`, `QP/Low-Latency`).

#### Card 4 — `SolLuxe™ 3.0 Pure` Voice Studio & 3D Game Audio
- **1-Tap Gender Profile (`Neutral 68Hz` | `Male 55Hz` | `Female 85Hz`):** Auto-matches HPF, Condenser EQ pockets, gender-scaled Leveler sidechain (`110/140/180 Hz`), De-Esser center (`6.2/6.7/7.2 kHz`), and the built-in PNGTuber accent while preserving explicit manual HPF choices.
- **6 Voice Presets:** `SOL_LUXE` (Pure Condenser), `RADIO STUDIO` (Punchy Cut-Through), `VELVET` (Warm Natural), `CLEAN` (Transparent Natural), `NEURAL` (C RNNoise Pure), and `RAW` (Dry Mic Bypass — retains Mute, Mic Boost & safety peak limiter).
- **SeekBar Volume Sliders (`−10% / +10%`):** Adjust Post-DSP Mic Boost (`50%–250%`) and Internal Stereo Game Mix (`0%–250%`), plus stackable `Cinema 3D Wide`, `Theater Sub-Punch`, `Footstep Focus`, `Blast Tamer`, and the **10-Second Studio Voice Test & Auto-Advisor**.

#### Card 5 — Launch Broadcast, Clips & Reports (`Launch Deck`)
- **`GO LIVE — START STREAM` ↔ `⏹ STOP LIVE STREAM`** (with 2-tap stop confirmation), **`Session Reports & History`** (highlights `⚡ Recovered 60s` if a previous stream was cut by the OS, keeps completed reports frozen, and allows clearing standalone checkpoints), **`★ Saved Clips & AI Highlights`**, **`Benchmark Other App`**, **`Refresh Mics / HW Scan`**, **`Check GitHub Update`**, and **`❤️ Subscribe to @Sol_CODM`**.

---

### Step C: In-Game 3-Tab Floating Pill & Safe Notification Shade

- **Short Closed Pill:** Displays **`● 05:36 | 17.6M | 60fps | Mic:ON | 38.4°C`** (sleeps at `38%` opacity, `0 Hz` idle redraws, `FLAG_SPLIT_TOUCH`, clamped safely inside viewport bounds across orientation changes).
- **Expanded 3-Tab Pill (Zero-Truncation Quick Bar):**
  - **Top Quick Bar:** `[ Mic:ON ] [ Game:ON ] [ LIVE ] [ ★ Clip ] [ ✕ ]` (fixed-width `✕` prevents any text cut-off on narrow screens).
  - **Tab 1 (`🎙️ Audio`):** Voice Profile, Preset, Denoiser Mode/Level, Auto/Manual HPF, EQ/De-Ess/Warm/AGC toggles, Mic/Game volume steps, 3D Soundstage, and Mic Hardware switcher.
  - **Tab 2 (`🎬 Video`):** Scene, Framing, Zoom, Upscaler, Sharpen, 180° Flip, Stackable Color Filters, unified **`Logo:OFF → FULL → TL → TR → BL → BR`** button, Banner, **`📸 Shot` (1-Tap Clean Thumbnail)**, **`HUD:OFF`**, **`QR:OFF`**, **`Avatar:OFF`**, Minimap Blur, and Keyboard Blur.
  - **Tab 3 (`📡 Bitrate/Net`):** Destination-clamped Live Bitrate steps, ABR toggle, `30FPS Cool` (with `SEVERE` live-temp toast guard), Stream/Game FPS, **`Queue(A+V)` + Enqueue Time + RTT (RTMP Ping / TCP Connect)**, separated **`🔥 App CPU: ~X% | Thermal Pressure: Y%`** & **`💾 RAM: App MB | Game+OS GB`**, target status, and 2-tap **`⏹ Stop Stream`**.
- **Safe Notification Bar (3 Actions Only):** Displays **`Mute Mic`**, **`Scene/BRB`**, and **`★ Clip`** — `Stop` is intentionally omitted from the notification shade so swiping notifications never ends a marathon stream accidentally.

---

## 6. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Ultra `v1.5.2` Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **6-Stage `SolLuxe™ 3.0 Pure` Condenser Chain** | Shure SM7B, Neumann U87, Neve 1073 & FabFilter Pro-C2 / Pro-DS | Non-overlapping 64-bit EQ pockets (`95/130/172 Hz` warmth, `315–365 Hz` mud cut, `920 Hz` dip, `4.5–5.1 kHz` sheen, `8.5/9.0 kHz` air shelf), `<0.6 ms` raised-cosine impulse clamper, `8 ms / 220 ms` Hybrid VAD expander with speech/mute-frozen noise floor, gender-sidechain soft-knee leveler, and post-comp sibilance-ratio de-esser. |
| **Phase-Safe `62 Hz` Sub-Punch + `250 Hz + 2.45 kHz` Footsteps + `4×` Limiter** | Dolby Atmos Gaming & FabFilter Pro-L2 | Boosts `62 Hz` game sub-bass (`+3.0 dB`) when silent and ducks to `0.0 dB` neutral during speech, lifts `250 Hz` indoor thuds + `2,450 Hz` crunch, and protects `250%` game mix through a 64-sample look-ahead `4×` Catmull-Rom Peak Limiter (`-1.16 dBTP`). |
| **`SolCore Ultra™` ES 3.0 `Fast-FP16` + Gated Neighbor Shader + GL Frame Pacer** | Desktop OBS Studio + AMD CAS | Uses `mediump` FP16 registers, gates cardinal neighbor fetches (`14.74M` fewer reads/frame when Sharpen/Shadow Boost are OFF), locks steady 60/30 FPS via `frameIntervalNs` pacing, and preserves 2-second CFR keyframes. |
| **`SolHUD™`, Offline Corner QR, Zero-Camera PNGTuber, Clean Thumbnails & 4 Studio Profiles** | OBS Profiles & Overlays + VTube Studio / PNGTuber Plus + NVIDIA ShadowPlay | Pure-Kotlin ISO/IEC 18004 QR (`GL_NEAREST`), reused `138 KB` HUD texture (`glTexSubImage2D`), voice-reactive 2-PNG avatar (`vadSpeechActive`, zero `CAMERA`), temporary offscreen RGBA8 FBO `<=1080p` JPEG thumbnail (`Pictures/SolStream/`), and 4 secret-free hardware-clamped Studio Profiles. |
| **Adaptive `64 KB / 4 KB` RTMP Chunking + $\mu\text{s} \rightarrow \text{ms}$ RTT Bridge + Fast Network Handover** | OBS RTMP Tuning + IRL Pro / Cloudflare Speed | Converts Pedro `getRtt()` microseconds to true milliseconds, disables false ICMP disconnects (`setCheckServerAlive(false)`), adapts write chunk size to network & destination, and triggers a fast `0.3s` RTMP reconnect on `Wi-Fi ↔ 5G` route switches. |

---

## 7. For Android Developers & Engineers: Architecture, 6-Stage `SolLuxe™ 3.0 Pure` & Build Spec

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
- **JDK, Gradle & Pinned NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + AGP `8.13.2` + `compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`, `versionCode = 14`, `versionName = "1.5.2"` + **Pinned Android NDK `28.2.13676358`** (`CMake 3.22.1`, `-DANDROID_STL=none`, `-DSOL_REQUIRE_XIPH_RNNOISE=ON` in CI, `-O3 -std=gnu11 -Wno-null-dereference`, `ndk.debugSymbolLevel = "SYMBOL_TABLE"`).
- **Cryptographic Source Pinning, APK + AAB `16 KB` Alignment & 62-Test JUnit Suite:** Fetches and verifies exact Xiph `RNNoise v0.1.1` commit **`6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`** (including `src/celt_lpc.c`), fails CI builds if Xiph RNNoise is missing, enforces `-Wl,-z,max-page-size=16384` + `useLegacyPackaging = false`, verifies **`zipalign -c -P 16 -v 4`** on the signed Release APK plus numeric `< 16384` ELF `LOAD` segment checks on **both** the Release `.apk` AND the Play Store `.aab` (`base/lib/*/*.so`) alongside `apksigner` V3 checks, runs strict Android Lint (`abortOnError = true`) and fresh un-cached execution (`--rerun-tasks`) of all **62 JUnit 4 unit tests** (`SolStreamUnitTests.kt`), and uploads both `SolStream-Studio-v1.5.2.apk` and `SolStream-Studio-v1.5.2-PlayStore.aab`.
- **Upcoming Google Play Milestones Tracked for `v2.0.0`:** April 2027 foldable adaptive support & screenshots (dashboard already supports dynamic resizing with `660dp` responsive centering) and August 2027 API 37 target upgrade (including Android 17 `ACCESS_LOCAL_NETWORK` review for LAN custom RTMP servers).

### The 6-Stage `SolLuxe™ 3.0 Pure` Vocal Architecture (`MicDspProcessor.kt`)

| Stage | Module Name | Exact Technical Specification (`48,000 Hz`, 64-Bit `Biquad64`, Atomic Frame-Boundary Config) |
|---|---|---|
| **Stage 1** | **64-Bit Butterworth HPF** | 2nd-order `Q = 0.707`: **`55 Hz` (`MALE`)**, **`68 Hz` (`NEUTRAL`)**, **`85 Hz` (`FEMALE`)** (`+5 Hz` `WIRED_USB_CLOSE`, `+10 Hz` `BLUETOOTH_SCO`, `+15 Hz` `SPEAKER_AEC`). Explicit manual Hz cutoffs (`hpfAutoMode = false`) are never overwritten by preset or profile switches. |
| **Stage 2** | **Raised-Cosine Micro-Impulse Clamper** | Tracks local `20 ms` RMS (`960 samples`). Triggers only on sub-millisecond spikes `<0.6 ms` ($\le 28$ samples) exceeding **`3.8×`** local RMS; clamps cleanly and recovers via a smooth **`2.0 ms` (`96-sample`)** raised-cosine ramp so `10–30 ms` `"P/T/K"` consonants stay crisp. |
| **Stage 3** | **Native C `RNNoise` + Hybrid `16 kHz`-Safe Neural-VAD Expander** | Stack-allocated `GetFloatArrayRegion` JNI (`480 samples`, `480-sample` dry delay, hardware `FZ` guards; `65% / 88% / 100%` true `1.00f` wet). Hybrid trigger opens on `vadProb >= 0.35f` OR `200–2500 Hz` RMS `>= +6 dB` (`2.0×`) above adaptive floor. Noise-floor adaptation is strictly frozen during active speech and mute. Smooth **`8.0 ms` attack**, **`220 ms` release**, **`140 ms` hold**, soft **`-14 dB` (`0.20×`)** floor (`-18 dB` / `0.126×` speakerphone + double-talk-safe bleed ducking). |
| **Stage 4** | **Split-Band Dynamic Plosive Guard** | Causal 2-stage `<115 Hz` low-pass monitor vs. `420 Hz (Q = 1.05)` vocal-core detector. Ducks **only** the `<115 Hz` band by up to `-9 dB` when wind-to-vocal ratio exceeds `2.5×`, passing deep vocal fundamentals untouched before Stage 5. |
| **Stage 5** | **4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth Shaper** | Strictly non-overlapping `Biquad64` pockets: Chest Warmth (`95/130/172 Hz`, `Q=0.85` M/N, `Q=1.0` F), Surgical Mud Cut (`315/340/365 Hz`, `Q=2.0`), Anti-Nasal (`920 Hz`, `Q=1.8`), Neve Silk Presence (`4.5/4.8/5.1 kHz`, `Q=1.15`; or `2.9/3.2/3.5 kHz @ +1.6 dB` in `RADIO STUDIO`), and Clean Air Shelf (`8.5/9.0 kHz`, `Q=0.707`; or real `5.2 kHz` 2nd-order Butterworth LPF on `BLUETOOTH_SCO`). Low-band cubic analog warmth (`0.09 / 0.08 / 0.06f`; `tube2fDrive = 0.0f` on Neutral & Female). |
| **Stage 6** | **VAD-Synced Soft-Knee Leveler + Dynamic De-Esser + `4×` Peak Limiter** | **Leveler:** Gender-matched HPF sidechain (`110/140/180 Hz`), `10 ms` attack / `130 ms` release, single continuous `6 dB` soft-knee curve (`2.5:1` default / `3.2:1` Radio / `2.0:1` Velvet above `-16 dBFS`) + up to `+3.5 dB` quiet-speech lift (`-38..-22 dBFS`) scaled by continuous `vadProb` ramp (`0.25 → 0.55`) × smoothed expander gain + speech-synced slow AGC.<br>**De-Esser:** Post-EQ/Post-Comp dynamic `6.2 / 6.7 / 7.2 kHz` (`Q=2.0`, max `-4.5 dB`) sibilance-to-vowel ratio check.<br>**Output:** Post-DSP Mic Boost (`50–250%`) → zero-allocation **`4×` Catmull-Rom Inter-Sample Peak Limiter at `-1.16 dBTP` (`0.875`)** with `NaN` guards + `4.5 ms` Low-End CPU Auto-Bypass watchdog. |

### Modular Source Tree (`v1.5.2`)

~~~text
.
├── PRIVACY_POLICY.md                        # Play Console Privacy Policy, Backup Exclusions, Data Deletion & Accessibility Disclosure
├── COMMERCIAL-LICENSE.md                    # Creator Broadcasting Exception & Commercial Redistribution Terms
├── README.md                                # Complete v1.5.2 User Manual & Engineering Reference
├── .github/workflows/build.yml              # Pinned NDK 28.2.13676358, Strict Xiph RNNoise, APK + AAB 16 KB ELF CI
└── app/
    ├── build.gradle.kts                     # v1.5.2 (versionCode 14) + AndroidX ExifInterface 1.4.2 + Strict CI RNNoise Flag
    ├── proguard-rules.pro                   # Explicit R8 @Keep rules for JNI, RNNoise, RootEncoder & Services
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Play-clean permissions + allowBackup="false" + dataExtractionRules
        │   ├── cpp/
        │   │   ├── CMakeLists.txt           # 16 KB page-aligned C build + SOL_REQUIRE_XIPH_RNNOISE CI guard
        │   │   └── sol_rnnoise_jni.c        # Zero-allocation stack JNI bridge + ARM/x86 FZ register guards + dry delay
        │   ├── res/
        │   │   ├── drawable/ic_solstream_logo.xml   # Full-bleed Solar Eclipse 'S' Adaptive Vector Icon
        │   │   ├── drawable/ic_stat_stream.xml      # Monochrome notification status icon
        │   │   ├── values/strings.xml & styles.xml  # Accurate Accessibility disclosure & Obsidian-Glass #050A14 Theme
        │   │   └── xml/
        │   │       ├── accessibility_service_config.xml # isAccessibilityTool="false", canRetrieveWindowContent="false", 0 events
        │   │       └── data_extraction_rules.xml        # Explicit Android 12+ cloud-backup & device-transfer exclusions
        │   └── java/com/solgaming/solstreamstudio/
        │       ├── MainActivity.kt               # Obsidian-Glass Dashboard, Platform-Locked Pills, Profiles & Live Controls
        │       ├── SolStreamService.kt           # Broadcast Lifecycle, Primary RTMPS, ABR, Thermal Governor & Audio/Video Safety
        │       ├── SolFloatingPillController.kt  # 3-Tab Floating Pill, Orientation-Safe IME Blur & Separated Telemetry Readouts
        │       ├── SolRtmpTransportSupport.kt    # SolRtmpTelemetryBridge, Leased-Slot SolAuxRtmpTarget & SolReportArchiveStore
        │       ├── SolVideoPipeline.kt           # ES 3.0 Fast-FP16, Bounded Codec Retries, Strict CBR, Gralloc Guard & FBO Shot
        │       ├── SolSceneAssetStore.kt         # AndroidX ExifInterface Atomic Asset Store + Masked Esports Striker Avatar
        │       ├── SolDualAudioEngine.kt         # AudioWorkerBarrier, Bounded AAC Retries, Silence-Safe Mic Recovery & 3D Game Bus
        │       ├── MicDspProcessor.kt            # SolLuxe™ 3.0 Pure 6-Stage Engine, Frozen Speech/Mute Floor & 4x Peak Limiter
        │       ├── SolNativeRnnoise.kt           # Safe Kotlin JNI wrapper for libsol_rnnoise.so
        │       ├── CodecCapabilityProbe.kt       # eligibleCompanionEncoders(), AC-Gated Bypass PD Classifier & Thermal Pressure
        │       ├── KeystoreVault.kt              # Synchronized AES-256-GCM Vault, Central enforcePlatformLimits() & 4 Profiles
        │       ├── DiagnosticsLogger.kt          # Frozen v1.5.2 Reports, Verified Sysfs SoC Temp, Audio Drops & 60s Checkpoint
        │       ├── recorder/
        │       │   └── SolHudAndQrRenderer.kt    # Pure-Kotlin ISO QR (V1–10 M), Zero-GC SolHUD, Corner MVP & MediaStore Saver
        │       └── ui/
        │           └── DashboardDialogs.kt       # Visual Guide Cards, Accessibility Disclosure, Reports, Speed Test & Update Check
        └── test/java/com/solgaming/solstreamstudio/
            └── SolStreamUnitTests.kt             # 62-test JUnit 4 suite (DSP, Sustained Speech, Resampler, Mic Guard, Platform & CI)
~~~

### Build & Release Verification Command

~~~bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37,37.0,38,38.0 --no-daemon --stacktrace
~~~

---

## 8. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`, **ALWAYS** call `startForeground()` before any early return in `startLiveBroadcast()`, **ALWAYS** await `activeTeardownLatch` off the main thread with an explicit timeout abort, and **ALWAYS** call `DiagnosticsLogger.lockFinalMicStatusBeforeStop()` + `videoPipeline.prepareForImmediateStop()` synchronously before async teardown.
2. **NEVER** catch transient video or AAC `MediaCodec.CodecException` *outside* the `while (isRunning)` drain loop—always retry up to the bounded limit inside the loop and escalate unrecoverable failures cleanly to `onFatalPipelineError` / `onFatalAudioError` (which stops the broadcast so viewers never receive frozen or silent streams).
3. **NEVER** bypass `KeystoreVault.enforcePlatformLimits()` on UI changes, profile imports, or encoder startup, and **NEVER** enable `maxBFrames > 0` while `RootEncoder 2.8.1`'s RTMP packetizer writes `CTS = 0`.
4. **NEVER** mutate `Biquad64` coefficients from the UI thread—publish an immutable `DspConfigSnapshot` atomically and apply filter updates strictly on the audio thread at `10 ms` frame boundaries; keep noise-floor updates frozen during active speech and mute.
5. **NEVER** restart a microphone solely for brief digital silence when Android reports `isClientSilenced == false`—keep `SolDualAudioEngine.shouldTriggerMicRecovery()` gated on read errors, OS-silenced status, and the `15s` cooldown, and never release shared audio resources until `AudioWorkerBarrier` confirms both workers have exited.
6. **NEVER** call `glReadPixels()` directly on a `MediaCodec` `InputSurface` (always render into a temporary offscreen RGBA8 FBO *after* `eglSwapBuffers()`, restore `glBindFramebuffer(0)` + `glViewport` in `finally`, and delete the FBO immediately), and **NEVER** allocate new `Bitmap` objects per frame inside the OpenGL render loop (`SolHUD™` must reuse its scratch bitmap via `glTexSubImage2D`).
7. **NEVER** enable `setCheckServerAlive(true)` on Pedro's `RtmpClient`, **NEVER** open test `MediaCodec` encoders while `isBroadcastLive || isBroadcastStartingOrLive`, **NEVER** poll `PowerManager.getThermalHeadroom()` faster than once every `10.5s`, **NEVER** classify USB or Wireless `NOT_CHARGING` as `Bypass Charging (USB-PD)`, and **NEVER** resize `VirtualDisplay` while `!capturedContentVisible` or to portrait dimensions during a `16:9` stream.
8. **NEVER** re-introduce `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`, `SYSTEM_ALERT_WINDOW` (prior to Phase 2 Offline Recorder), `AudioManager.setCommunicationDevice()`, `-ffast-math` in `CMakeLists.txt`, or CPU software video encoders (`c2.android.av1.encoder`).

---

## 9. Privacy Policy, Google Play Compliance & Open-Source Licenses

- **Privacy Policy & Zero Developer Data Collection:** See [**`PRIVACY_POLICY.md`**](./PRIVACY_POLICY.md) for our Google Play Console Privacy Policy, `data_extraction_rules.xml` backup exclusions, Data Retention & User Deletion controls, third-party speed-test/update metadata notices, and accurate `AccessibilityService` disclosure (`android:isAccessibilityTool="false"`, `android:canRetrieveWindowContent="false"`, `android:canPerformGestures="false"`, zero accessibility event subscriptions).
- **Created & Maintained By:** **Sol Studio (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
- **Dual Licensed:**
  - **Free for Gamers, Monetized Creators & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)** with an explicit **Additional Creator Broadcasting Permission** in **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)** covering monetized YouTube, Twitch, Kick, and Shorts channels.
  - **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
- **Open-Source Credits:** Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`, zero root access required), neural voice denoising uses [`xiph/rnnoise`](https://github.com/xiph/rnnoise) (`BSD-3-Clause`), and UI/image/async foundations use AndroidX (`Core`, `AppCompat`, `ExifInterface`), Material Components & Kotlin Coroutines (`Apache-2.0`).
