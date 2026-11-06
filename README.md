# SolStream Studio (`v1.0.2`)

**SolStream Studio** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (~3 MB), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio custom-engineered for **120 FPS competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **1440p60 / 1080p60** hardware-encoded video and **split 32-bit float studio commentary** to YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed) over encrypted **RTMPS**.

> **100% Unrooted Stock Android:** Works on normal, unrooted Android 10–17+ phones right out of the box. Root access is **never** required or used.

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120 FPS gameplay and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture:** SolStream Studio is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES frame pacer, HUD-safe aspect scaler, isolated 32-bit float vocal DSP, drift-locked stereo game circular buffer, 4x cubic inter-sample look-ahead limiter, hardware encoder capability scanner, AES-256 Keystore vault, and 0-Hz sleeping Accessibility Ghost Pill are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini and ChatGPT!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`lintDebug`, `testDebugUnitTest`, and `assembleRelease`) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK](#1-download--install-signed-release-apk)
2. [How Human + Dual-AI Built This App](#2-how-human--dual-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-actually-do)
4. [Plain-English User Manual: Every Button & Setting Explained](#4-plain-english-user-manual-every-button--setting-explained)
5. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#5-global-broadcast-inspirations-windows-macos--ios---android)
6. [For Android Developers & Engineers: Architecture & Reasoning](#6-for-android-developers--engineers-architecture--reasoning)
7. [For AI Coding Assistants, Security Auditors & Forkers](#7-for-ai-coding-assistants-security-auditors--forkers)

---

## 1. Download & Install Signed Release APK

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.0.2.apk`** under **Assets** to download directly onto your Android phone (no GitHub login or `.zip` extraction needed).
3. All official Releases are signed with a permanent cryptographic key—future updates (`v1.1.0+`) install straight over your existing app without uninstalling or losing saved settings!

---

## 2. How Human + Dual-AI Built This App

This repository was created through a three-way verification loop between a competitive mobile gamer and two frontier reasoning models checking each other's blind spots:

* **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  * Defined real-world competitive constraints on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e`, `2340×1080` 120 Hz display, Bypass Charging ON), **realme Buds Wireless 5 ANC** neckband earphones, 5 GHz Fiber Wi-Fi, and 4+ hour continuous `1440p60` live sessions.
* **System Architecture, DSP/OpenGL Engineering & Code Generation — `Google Gemini`:**
  * Engineered the pure-Kotlin zero-XML Android architecture, isolated 32-bit float microphone DSP (`MicDspProcessor`), drift-locked dual audio mixer (`SolDualAudioEngine`), 120-to-60 FPS paced OpenGL video pipeline (`SolVideoPipeline`), verified hardware Keystore vault, live Accessibility Ghost Pill, and GitHub Actions CI/CD pipeline.
* **Independent Spec Audit & Edge-Case Verification — `OpenAI ChatGPT`:**
  * Audited code paths against Android 10–17 AOSP rules, Samsung One UI security behavior, YouTube Live ingestion specs, TLS hostname verification, reconnect concurrency, and thermal telemetry accuracy.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `100+ MB` of camera masks and social feeds, cap video to older H.264, ruin Bluetooth stereo footsteps when you use squad voice chat, or slap limiters across mixed audio that make gunshots sound underwater.

**SolStream Studio solves those problems in a ~3 MB, unrooted, ad-free app:**

* **Plays at 120 FPS, Streams Smoothly at 60/30 FPS:** Your phone screen stays locked at 120 Hz for *CODM*, while dedicated hardware media silicon (`HEVC / H.264 / HW AV1`) sends paced `1440p60` or `1080p60` video to YouTube.
* **Cleans ONLY Your Voice (Never Muddies Footsteps or Gunfire):** Processes *only* your isolated 32-bit float microphone before mixing with untouched stereo game audio via a drift-locked circular buffer and a 64-sample look-ahead 4x cubic inter-sample limiter (`-1.16 dBTP`). Nothing alters what *you* hear in your earphones.
* **Lobby-Safe Ping Guard (ABR 2.0):** Caps outgoing packet queues to stop router bufferbloat and steps down bitrate *only* during genuine upload congestion—never falsely cutting quality during calm lobbies or BRB screens.
* **6+ Hour Marathon Armor:** Validated over 4+ hour continuous `1440p60` broadcasts with 0 dropped frames, automatic `AudioRecord` & `AAC` self-healing, 12-hour auto-renewing wake-locks, and sequential failover to YouTube's backup RTMPS server.
* **0-Hz Sleeping In-Game Ghost Pill:** Floats at 38% opacity with zero redraw load while aiming; double-tap to mute stream mic (while your squad still hears you!), mute game audio, enable `1.5 Mbps` **BRB Privacy Blackout** (preserving manual mic mute privacy), or adjust bitrate live.

---

## 4. Plain-English User Manual: Every Button & Setting Explained

### Step A: One-Time Android 13–17 & Earphone Setup (2 Minutes)
1. **Unlock Restricted Accessibility (Why the `⋮` 3-Dots Hide at First on Android 13–17):**
   * Because the APK is installed from GitHub rather than Google Play, Android hides the `Allow restricted settings` menu until you try Accessibility once!
   * In Card 1, tap **`3. Tap Accessibility 1st`** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK** on the prompt.
   * Return to SolStream Studio and tap **`4. Unlock Restricted (⋮)`** (teleports straight into SolStream's App Info screen) → tap **`⋮`** (top-right) → **Allow restricted settings** and confirm PIN/fingerprint.
   * Tap **`3. Tap Accessibility 1st`** again and switch **SolStream Studio** **ON**! *(Future signed updates keep this unlocked permanently.)*
2. **Bluetooth Neckband Stereo + Low-Latency Game Mode Trick:**
   * Turning on any Bluetooth headset's built-in mic forces Bluetooth into muffled mono call mode (`HFP`). To keep **full stereo footsteps + 45 ms Game Mode** in your earphones while using your phone's crisp **48 kHz** mic: open **Bluetooth Settings → Earphones Gear ⚙️ → toggle `Calls` OFF** (keep `Audio` **ON**).
   * On Samsung phones: *Settings → Sounds and vibration → Separate app sound* → lock *Call of Duty* to Bluetooth.
3. **Single-App Privacy Capture:** When Android asks *"Start recording or casting?"*, choose **"A single app" → Call of Duty** so WhatsApp notifications, calls, and OTP codes never appear on stream!

### Step B: Pre-Stream Dashboard (Every Card & Button)

#### Card 1 — Pre-Flight Readiness & Direct Settings Setup
* **`1. Grant Mic & Notif` / `2. Unrestricted Batt`:** Requests Microphone/Notification permissions and tells Android never to throttle the stream in the background.
* **`3. Tap Accessibility 1st` / `4. Unlock Restricted (⋮)`:** Exact 1-2 sequence to unlock and enable the floating in-game pill and simultaneous squad-mic sharing.

#### Card 2 — YouTube RTMPS & Encrypted Stream Key Vault
* **`RTMPS Ingest Server URL`:** Pre-filled with `rtmps://a.rtmps.youtube.com:443/live2` (works with or without `:443`, enforces strict TLS hostname verification, and auto-configures `b.rtmps.youtube.com` backup failover).
* **`Stream Key` (`Paste Key` / `Show Key` / `Clear Key` / `Save Vault`):** Encrypts your key inside Android's hardware-backed Keystore (`AES-256-GCM`).
  * **For `16:9` Landscape `1440p60`:** Create a Custom `1440p (60 fps)` Stream Key in YouTube Studio (*Auto-stop* OFF).
  * **For `9:16` Vertical YouTube Shorts Live Feed:** Use YouTube Studio's **Default / Variable Resolution Stream Key** (*"Turn on manual settings"* OFF) and set Card 3 to **`9:16 Shorts`**—YouTube automatically places your vertical stream straight into the Shorts Live Feed without needing Google login!
* **`Test Internet RTMPS Ping & Upload`:** Verifies DNS, TLS 1.2/1.3 SNI handshake, idle RTT/jitter, sustained upload Mbps, and loaded router bufferbloat.

#### Card 3 — Video Encoder, Bitrate & CODM HUD Framing
* **`Codec` (`HEVC` / `H264` / `AV1`) & `Res` (`1440p` / `1080p`) & `Stream` (`60 FPS` / `30 FPS`):** Hardware-only encoding (software encoders are blocked to protect game FPS). Settings marked `[Next]` while live apply on your next stream; all other controls sync live immediately!
* **`16:9 Wide (0° / 180°)` / `9:16 Shorts`:** Use `180°` live flip if you rotate your phone so your charging cable sits comfortably between your fingers, or `9:16 Shorts` for vertical streaming.
* **`Target` (`12–24 Mbps`), `Rate` (`CBR` / `VBR`) & `Ping Guard ABR`:** `18–20 Mbps CBR` with Ping Guard `ON` is ideal for 40+ Mbps fiber.
* **`Framing` (`FIT` / `SMART` / `FILL`) & `GPU Crisp Sharpen`:** `FIT` preserves 100% of your CODM HUD; keep `GPU Crisp Sharpen` **OFF** for the coolest phone temperatures during 3+ hour streams, or toggle **ON** anytime for extra edge crispness.

#### Card 4 — 32-Bit Float Studio Audio & 5s Mic Test Studio
* **`Mic Input`, `Source` (`AUTO`) & `AAC` (`128–320 kbps`):** Selects microphone hardware without hijacking global game call routing.
* **`Voice` (`RAW` / `CLEAN` / `STUDIO`), `HPF` (`75–100 Hz`), `Light Noise Cut`, `Gate | Comp` & `EQ | De-Esser`:** Shapes clean broadcast commentary over gunfire.
* **`Mic Boost`, `Game Mix`, `Ducking` & `Mic Sync`:** Balances voice vs. game sound, ducks gunfire slightly only while you speak, and fine-tunes lip-sync (`0–160 ms`).
* **`Record 5s Test` / `Play Test` / `Delete`:** Test your real two-hand CODM grip and ceiling-fan noise floor before going live.

#### Card 5 — Launch Broadcast, Diagnostics & Updates
* **`GO LIVE`:** Checks media volume guard, requests screen capture, and starts streaming.
* **`Session Notes & Report` & `Start Glip Benchmark`:** Generates privacy-safe diagnostics (Battery °C, Thermal status, App Process PSS & Heap RAM, Fresh vs. Encoded FPS, Avg QP, sender queue frames, and monotonic Audio-vs-Video PTS sync) with stream keys automatically redacted.
* **`Refresh Mics / HW Scan` & `Check GitHub Update`:** Re-scans audio/video hardware asynchronously and checks GitHub Releases for newer signed APKs.

---

## 5. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **Isolated Mic-First 32-Bit Float Bus** | OBS Studio, Reaper DAW & Elgato Wave Link | Captures mono `PCM_FLOAT` mic separately from stereo `AudioPlaybackCapture` so voice filters never pump or muddify game audio. |
| **Drift-Locked Stereo Game Ring Buffer** | CoreAudio / PipeWire Adaptive Clock Sync | Bridges 20 ms Android playback capture bursts into 10 ms mixer frames with fractional linear interpolation—eliminating buffer-boundary clicks and multi-hour drift. |
| **4x Cubic Look-Ahead Limiter (`-1.16 dBTP`)** | FabFilter Pro-L2 & EBU R128 Broadcast Limiters | Evaluates Catmull-Rom inter-sample peaks (`0.25 / 0.50 / 0.75`) across a 64-sample look-ahead window with TPDF dither. |
| **120 Hz Display -> 60/30 FPS Frame Pacer** | Desktop Capture-Card Passthrough | Monotonic `System.nanoTime()` OpenGL pacing + dynamic Android 14+ `onCapturedContentResize()` keeps gameplay at 120 Hz while feeding steady 60/30 FPS to hardware silicon. |
| **Lobby-Safe Queue-Aware ABR 2.0** | CAKE / FQ-CoDel SQM Bufferbloat Control | Caps outbound packet queue to 120 frames and compares network throughput against actual encoder production so calm lobbies never trigger false bitrate drops. |

---

## 6. For Android Developers & Engineers: Architecture & Reasoning

### Validated Cloud Build Toolchain (`build.yml` + `build.gradle.kts`)
* **JDK & Gradle:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + Android Gradle Plugin (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`).
* **Shrinking & 16 KB Page Readiness:** R8 minification & resource shrinking enabled (`~3 MB` signed APK) + `packaging.jniLibs.useLegacyPackaging = false` ahead of Google Play's Android 15+ 16 KB page mandate.
* **Transport Layer:** Uses only the low-level `RtmpClient` socket transmitter from `com.github.pedroSG94.RootEncoder:library:2.8.1` (with strict `tlsHostVerification = true`, `resizeCache(120)`, and sequential `reConnect` failover); all capture, OpenGL, MediaCodec, audio DSP, and UI pipelines are standalone original code.

### Source Tree
```text
app/src/main/
├── AndroidManifest.xml                  # Minimal unrooted permissions (zero Camera, Storage, Contacts, or SAW)
├── res/xml/accessibility_service_config.xml
└── java/com/solgaming/solstreamstudio/
    ├── MainActivity.kt                  # Pure-Kotlin Studio UI, live stream sync, TLS+Upload+Bloat probe, Updater
    ├── SolStreamService.kt              # Accessibility + ForegroundService, ABR 2.0, BRB privacy state, Ghost Pill
    ├── SolVideoPipeline.kt              # SurfaceTexture + Dynamic Resize -> OpenGL -> HW MediaCodec + QP stats
    ├── SolDualAudioEngine.kt            # Drift-locked Stereo Ring + Mono Float Mic -> 4x Cubic Look-Ahead Limiter
    ├── MicDspProcessor.kt               # Zero-allocation 32-bit float vocal DSP (64-bit biquad state)
    ├── CodecCapabilityProbe.kt          # Hardware MediaCodec, PerformancePoint & Surface dry-run scanner
    ├── KeystoreVault.kt                 # Verified AndroidKeyStore AES-256-GCM vault & URL normalizer
    └── DiagnosticsLogger.kt             # Secret-redacted telemetry, PSS/Heap profiler & local crash note
```

### Build & Release Verification Command
```bash
gradle lintDebug testDebugUnitTest assembleDebug assembleRelease -Pandroid.suppressUnsupportedCompileSdk=36,37 --no-daemon --stacktrace
```
Release builds strictly require `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` in GitHub Secrets and verify via `apksigner` that no debug certificate ever ships in a Release APK.

---

## 7. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`.
2. **NEVER** call `mediaProjection.createVirtualDisplay(...)` more than once per consent token—adapt size via `onCapturedContentResize()` + `virtualDisplay.resize(...)`, and render BRB frames inside `SolVideoPipeline.runGlRenderLoop`.
3. **NEVER** allocate heap objects inside `MicDspProcessor.processMonoFrameInPlace` or per-frame audio/OpenGL loops—preserve zero-GC steady state for 6+ hour 120 FPS sessions.
4. **NEVER** introduce `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, `AudioManager.setCommunicationDevice()`, or software video encoders (`c2.android.av1.encoder`).
5. **Native JNI Hook (`v1.1.0+`):** Compile any native C/C++ DSP (`RNNoise`) with Android NDK r28+ using `-Wl,-z,max-page-size=16384` and verify ELF `LOAD` alignment (`2**14`).

### Privacy & Security Auditability
* **Zero Telemetry / Zero Ads / No Root:** Connects *only* to your configured RTMPS server (plus `speed.cloudflare.com` and `api.github.com` strictly when you manually tap the network or update buttons).
* **Stream Key Protection:** Encrypted in `AndroidKeyStore` (`AES/GCM/NoPadding`), redacted via regex in `DiagnosticsLogger`, and stripped from Release Logcat via `proguard-rules.pro`.
* **Minimal Accessibility Scope:** `canRetrieveWindowContent="false"`—used strictly to host `TYPE_ACCESSIBILITY_OVERLAY` and satisfy Android's foreground microphone-sharing policy without reading screen text.

---

## License & Attribution

* Created & maintained by **Sol (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
* Licensed under the **Apache License 2.0**.
* Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) under the Apache License 2.0 *(note: despite that library's name, SolStream Studio requires **zero root access** and runs on 100% stock unrooted Android devices)*.
