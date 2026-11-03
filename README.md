# SolStream Studio

**SolStream Studio** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight, zero-ad Android live-streaming broadcast encoder built for **120 FPS competitive mobile gaming** (*Call of Duty: Mobile*, *BGMI*, *Valorant Mobile*, etc.) while streaming crisp **1440p60 / 1080p60** hardware-encoded video and **7-stage split 32-bit float studio commentary** to YouTube Live over encrypted **RTMPS**.

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** `@Sol_CODM`) know **nothing about programming, Kotlin, OpenGL, or Android Studio**. My only role was coming up with the streamer wish-list, asking nonstop questions about how Windows/Mac/iOS broadcast tools work, refusing compromises on 120 FPS gameplay and voice clarity, and testing on real hardware.
>
> **Deep Respect for Human Developers:**
> Building this project with AI is **never** meant to disrespect, replace, or insult real software engineers. Without brilliant human open-source developers—especially **Pedro Sánchez (`pedroSG94`)**, who built and maintains the `RootEncoder` library—none of this could exist. I simply used AI because I had a streaming problem nobody had solved in a single lightweight Android app and zero coding skills to build it myself.
>
> **How to Suggest Fixes or Improvements:**
> * **Please do NOT open GitHub Issues asking me coding questions or telling me what code to change**—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini and ChatGPT!
> * **Instead, just submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions builds cleanly, and open a PR. After I run a quick AI safety/thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [How Human + Dual-AI Built This App](#1-how-human--dual-ai-built-this-app)
2. [For Casual Readers & Gamers: What Does SolStream Studio Actually Do?](#2-for-casual-readers--gamers-what-does-solstream-studio-actually-do)
3. [Plain-English User Manual: Every Button & Setting Explained](#3-plain-english-user-manual-every-button--setting-explained)
4. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#4-global-broadcast-inspirations-windows-macos--ios--android)
5. [For Android Developers & Engineers: Architecture & Reasoning](#5-for-android-developers--engineers-architecture--reasoning)
6. [For AI Coding Assistants / LLMs: Context & Strict Invariants](#6-for-ai-coding-assistants--llms-context--strict-invariants)
7. [For Security Auditors, Streamers & Forkers](#7-for-security-auditors-streamers--forkers)

---

## 1. How Human + Dual-AI Built This App

This repository was created through a three-way loop between a non-coder mobile gamer and two frontier reasoning models checking each other's blind spots:

* **Product Vision, Requirements & Hardware Testing — `Sol Gaming CODM` (`@Sol_CODM`):**
  * Defined real-world competitive gaming constraints on a **Samsung Galaxy S24 FE** (`Exynos 2400e`, `2340×1080` 120 Hz display, Bypass Charging ON), **realme Buds Wireless 5 ANC** Bluetooth neckband, **40 Mbps Airtel Fiber** upload, and an older Intel Core i3 laptop used strictly for reading YouTube Studio chat.
* **System Architecture, DSP/OpenGL Engineering & Code Generation — `Google Gemini` (Gemini 4 / Argon High-Thinking Model):**
  * Designed the modular 22-file zero-XML Android architecture, wrote the split 32-bit float microphone DSP (`MicDspProcessor`), dual audio mixer (`SolDualAudioEngine`), 120-to-60 FPS paced OpenGL ES 2.0 video pipeline (`SolVideoPipeline`), hardware Keystore vault, floating `AccessibilityService` pill, and GitHub Actions cloud APK compiler.
* **Independent Grounding, Spec Verification & Edge-Case Audit — `OpenAI ChatGPT` (GPT-6 Thinking Model):**
  * Audited architectural proposals against official Android 14–17 AOSP rules, Samsung One UI 8/9 security behavior, YouTube Live Streaming API frame-rate/bitrate specifications, Gradle/R8 shrinking rules, and Google Play's February 2027 16 KB native page-alignment requirements—catching exaggerated claims and locking down crash-proof boundaries before final compilation.

---

## 2. For Casual Readers & Gamers: What Does SolStream Studio Actually Do?

Most Android live-streaming apps ruin competitive gaming in at least one way: they heat up your phone with camera filters and social feeds, cap video to older H.264 quality, break your earphones' stereo footsteps when you open squad voice chat, or apply audio filters that make gunshots and explosions sound underwater.

**SolStream Studio fixes those problems in an ~5–8 MB ad-free app:**
* **Plays at 120 FPS, Streams at 60 FPS:** Your phone screen stays buttery smooth at 120 Hz for *Call of Duty: Mobile*, while the dedicated hardware video chip sends clean `1440p60` or `1080p60` video to YouTube.
* **Cleans ONLY Your Voice (Never Muddies Game Sound):** Separates your microphone from CODM's stereo audio—removing ceiling-fan wind, phone-grip thumps, and harsh `"S"` sounds on your voice while leaving footsteps and gunfire 100% crisp. Nothing changes what *you* hear in your own earphones.
* **Protects Your CODM HUD & Ping:** Fits wide `19.5:9` / `20:9` phone screens onto YouTube's `16:9` screen without cutting off your minimap or ammo counter, and automatically lowers video bitrate if your Wi-Fi gets busy so your in-game ping doesn't spike.
* **Tiny Sleeping Floating Button:** Gives you a barely-visible floating dot in-game that uses `0%` redraw power until you double-tap it to mute your mic, black out your screen (`BRB`), or tweak volume live.

---

## 3. Plain-English User Manual: Every Button & Setting Explained

### Step A: One-Time Phone & Earphone Setup (2 Minutes)
1. **Allow Restricted Settings (Samsung One UI / Android 13–17):** Because you download the APK from GitHub instead of the Play Store, Android locks Accessibility at first. Open **Settings → Apps → SolStream Studio → tap `⋮` (top right) → Allow restricted settings**, confirm your PIN, then go to **Settings → Accessibility → Installed apps → SolStream Studio → ON**.
2. **Bluetooth Neckband Stereo + 45ms Game Mode Trick:** Whenever any Bluetooth earphone's built-in mic turns on, Bluetooth drops from crisp Stereo Game Mode into muffled mono "phone call" mode. To keep **full stereo footsteps + 45ms low-latency** in your earphones while using your phone's crisp **48 kHz** mic:
   * Open **Bluetooth Settings → Earphones Gear ⚙️ → toggle `Calls` OFF** (keep **`Audio` ON**).
   * On Samsung phones, go to **Settings → Sounds and vibration → Separate app sound** and lock *Call of Duty* to Bluetooth so match loading screens never jump sound to your phone speaker.
3. **Hide Personal Notifications on Stream:** When Android pops up *"Start recording or casting?"*, select **"A single app" → Call of Duty** so WhatsApp messages, calls, and OTP codes never show up on YouTube!

---

### Step B: Pre-Stream Dashboard (Every Card & Button)

#### Card 1 — Pre-Flight Readiness & One UI Setup
* **`1. Grant Mic/Notif`:** Turns on Microphone and Notification permissions.
* **`2. Unrestricted Batt`:** Tells Android never to put the stream to sleep mid-match.
* **`3. Enable Accessibility`:** Opens Accessibility settings so the floating button and squad-mic sharing work.
* **`One UI Restricted Guide`:** Opens the built-in cheat sheet for Samsung settings anytime you forget a step.

#### Card 2 — YouTube RTMPS & Encrypted Stream Key Vault
* **`RTMPS Ingest Server URL`:** Pre-filled with YouTube's secure `rtmps://[a.rtmp.youtube.com:443/live2](https://a.rtmp.youtube.com:443/live2)` server.
* **`Stream Key` + `Paste Key` / `Show/Hide` / `Save Vault`:** Locks your YouTube Stream Key inside your phone's hardware security chip (`AES-256`).
* **`Test Airtel RTMPS Ping & Jitter`:** Runs a 3-second encrypted ping test to YouTube and tells you whether your Wi-Fi tonight is ready for `20–22 Mbps` or safer at `18 Mbps`.

#### Card 3 — Video Encoder, Bitrate & CODM HUD Framing
* **`Codec` (`HEVC` / `H264` / `AV1`):** Auto-scans your phone chip on first launch. Keep **`HEVC`** for sharp `1440p60` on YouTube; switch to **`H264`** for universal compatibility (`AV1` unlocks automatically on future phones with hardware AV1 encoders).
* **`Res` (`1440p` / `1080p`) & `Stream` (`60 FPS` / `30 FPS`):** Output resolution and frame rate sent to YouTube while CODM stays at 120 FPS.
* **`16:9 Wide (0° / 180°)` / `9:16 Shorts`:** Use `180°` if you rotate your phone the other way so your Bypass Charging USB-C cable sits comfortably between your fingers!
* **`Target` (`12–24 Mbps`) & `Rate` (`CBR` / `VBR`):** `20 Mbps CBR` is the sweet spot for 40 Mbps upload fiber.
* **`Queue-Aware Auto Bitrate (Ping Guard)`:** Keep **`ON`**—if Wi-Fi slows down, it lowers stream Mbps smoothly before lag hits CODM.
* **`Framing` (`FIT` / `SMART` / `FILL`) & `Smart Zoom`:** **`FIT`** shows 100% of your CODM HUD with thin black bars (putting all bitrate into gameplay). **`SMART`** zooms in slightly (`104%–116%`); **`FILL`** crops edges to fill 16:9.
* **`Optional GPU Crisp Sharpen`:** Keep **`OFF`** by default for the coolest phone temperature, or test **`ON (Subtle)`** for slightly crisper crosshair edges.

#### Card 4 — 32-Bit Float Studio Audio & 5s Mic Test Studio
* **`Mic Input`:** Picks between Auto/Phone Built-in Mics, Bluetooth Headset, Wired, or USB-C mics.
* **`Source` (`AUTO` / `MIC` / `VOICE_REC`):** Keep **`AUTO`**—uses Android's best sharing mode automatically.
* **`AAC` (`128 / 160 / 192 / 256 kbps`):** Stream audio quality (`160` or `192 kbps` recommended).
* **`Voice` (`RAW` / `CLEAN` / `STUDIO`):**
  * `RAW` = Unfiltered mic.
  * `CLEAN` = Everyday natural clean voice (cuts wind/grip rumble + breath hiss).
  * `STUDIO` = Rich radio/podcast voice (adds vocal warmth, removes hollow boxiness, boosts clarity over gunfire, and softens harsh `"S"` sounds).
* **`Fan Suppress` (`OFF / LOW / MED`) & `HPF` (`OFF / 75 / 85 / 100 Hz`):** Cuts stationary ceiling-fan hum and low hand-grip thumps (`85 Hz` is ideal).
* **`Gate | Comp` & `Podcast Voice EQ | De-Esser`:** Individual switches if you want to customize any single vocal effect.
* **`Mic Boost` (`75%–220%`) & `Game Mix` (`0%–120%`):** Balances your voice volume against CODM gunfire on stream.
* **`Ducking` (`OFF / -20% / -35%`):** Automatically lowers stream game sound slightly only during the exact moments you speak!
* **`Mic Sync` (`0` to `+160 ms`):** Delays mic slightly if your voice ever arrives ahead of gameplay audio.
* **`Record 5s Test` / `Play Test` / `Delete`:** Hold your phone in your real two-hand CODM grip with your ceiling fan on, record 5 seconds, and listen back before going live!

#### Card 5 — Go Live, Diagnostics, Glip Benchmark & Updates
* **`GO LIVE`:** Asks Android for screen capture and launches your YouTube broadcast.
* **`Session Notes & Report`:** Shows plain-English health notes (FPS, temperature, RAM, queue, mic status) with stream keys hidden—ready to copy-paste if troubleshooting is ever needed.
* **`Start Glip Benchmark`:** Passively records phone temperature and RAM while you stream in Glip so you can compare both apps fairly.
* **`Check GitHub Update`:** Checks `PppP01/SolStream-Studio` Releases on demand for newer signed APKs.

---

### Step C: In-Game Floating Ghost Pill (During CODM)
* **Move It Anywhere:** Drag the translucent pill out of your HUD's way.
* **Open Without Misclicks:** Single accidental thumb taps while aiming are ignored! **Double-tap** (or **hold for `0.4s`**) to open the live menu:
  * **`Stream Mic` (`ON / MUTED`):** Mutes your voice on YouTube while your CODM squad still hears you.
  * **`GAME + SQUAD STREAM` (`ON / MUTED`):** Mutes game audio on YouTube while you keep hearing everything in your earphones.
  * **`BRB Privacy Blackout`:** Turns the YouTube video screen pure black (and mutes mic) instantly if you need to check something private—without disconnecting your stream!
  * **Live Audio, Framing, Flip & Bitrate Buttons:** Change `RAW/CLEAN/STUDIO`, fan suppression, volumes, ducking, `180°` flip, or Mbps mid-match without leaving CODM.
  * **`Stop Stream (2-Tap Confirm)`:** Requires two taps within 3 seconds so you never end a stream by accident.

---

## 4. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Feature | Original Inspiration | How We Adapted It Safely for Android |
|---|---|---|
| **Isolated Mic-First 32-Bit Float Bus** | **OBS Studio**, **Reaper DAW** & **Elgato Wave Link** | Captures mono `PCM_FLOAT` mic separately from stereo `AudioPlaybackCapture` so voice DSP never touches game audio. |
| **64-Bit Biquad Filter Accumulators** | **Desktop DAW High-Precision EQ** | Runs recursive `75–100 Hz` High-Pass and vocal EQ state math in 64-bit `Double` inside zero-allocation 32-bit float loops. |
| **Vocal Presence + Dynamic De-Esser** | **Voicemeeter Banana**, **SteelSeries Sonar** & **Aphex** | Shapes `125 Hz` warmth, `-2.2 dB @ 350 Hz` mud cut, `+2.2 dB @ 3.8 kHz` callout presence, and `6.5 kHz` sidechain de-essing. |
| **120 Hz Display -> 60/30 FPS Stream Pacer** | **Desktop Capture-Card Passthrough** | Uses monotonic `System.nanoTime()` OpenGL pacing + `KEY_MAX_FPS_TO_ENCODER` so 120 Hz gaming feeds clean 60/30 FPS output. |
| **HUD-Safe Aspect Framing (`Fit / Smart / Fill`)** | **Apple iOS ReplayKit** & **OBS Canvas** | Captures native `19.5:9` dimensions and scales via OpenGL matrices so landscape HUD corners are never distorted or lost. |
| **Single-Pass Contrast Sharpening** | **AMD FidelityFX CAS** | Optional lightweight neighbor-contrast pass inside the mandatory `OES` shader (`0` extra FBO passes; `OFF` by default). |
| **Queue-Aware Adaptive Bitrate & Shedding** | **RootEncoder 2.8.1** & **CAKE/FQ-CoDel SQM** | Polls send-queue depth to step down video bitrate before Wi-Fi bufferbloat spikes CODM ping; sheds optional GPU/DSP load on warmth. |

---

## 5. For Android Developers & Engineers: Architecture & Reasoning

### Validated Cloud Build Toolchain (`build.yml` + `build.gradle.kts`)
* **JDK:** Temurin `21` (targeting `JavaVersion.VERSION_17` bytecode)
* **Gradle:** `8.14` (`gradle/actions/setup-gradle@v6`)
* **Android Gradle Plugin (AGP):** `8.13.2` (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`)
* **Kotlin & Shrinking:** `Kotlin 2.4.20` + explicit **`R8 9.5.26`** (`isMinifyEnabled = true`, `isShrinkResources = true`)
* **16 KB Native Page Readiness:** `packaging.jniLibs.useLegacyPackaging = false` configured ahead of Google Play's February 1, 2027 Android 15+ mandate.
* **Protocol Core:** `com.github.pedroSG94.RootEncoder:library:2.8.1` (`RtmpClient` used directly; `extra-sources` and camera wrappers omitted).

### Source Tree
```text
app/src/main/
├── AndroidManifest.xml                  # Strict minimal permissions (no Camera, Storage, Contacts, Cleartext, or SAW)
├── res/xml/accessibility_service_config.xml
└── java/com/solgaming/solstreamstudio/
    ├── MainActivity.kt                  # Zero-XML Pre-Stream Studio UI, 5s Mic Tester, RTMPS TLS RTT probe, Updater
    ├── SolStreamService.kt              # AccessibilityService + ForegroundService, RtmpClient, Ghost Pill, Watchdog
    ├── SolVideoPipeline.kt              # SurfaceTexture -> 60/30fps OpenGL ES 2.0 -> Hardware MediaCodec (Annex-B stripped)
    ├── SolDualAudioEngine.kt            # Split PCM_FLOAT Mic + AudioPlaybackCapture mixer -> Hardware AAC-LC
    ├── MicDspProcessor.kt               # Zero-allocation 32-bit float DSP (64-bit biquads, Wiener NS, VAD, EQ, De-Esser, Comp)
    ├── CodecCapabilityProbe.kt          # Hardware MediaCodec, PerformancePoint & COLOR_FormatSurface dry-run scanner
    ├── KeystoreVault.kt                 # AndroidKeyStore AES-256-GCM stream-key encryption & settings persistence
    └── DiagnosticsLogger.kt             # Secret-redacted plain-English telemetry & Glip baseline comparison store
```

### Key Engineering Decisions ("Why We Built It This Way")
1. **Why Split `SolDualAudioEngine` Instead of `RootEncoder`'s `MixAudioSource`?**
   `MixAudioSource` mixes PCM16 channels *before* invoking `CustomAudioEffect`, causing high-pass filters, gates, and compressors to pump and thin out game explosions. `SolDualAudioEngine` processes mono `480`-sample (`10 ms` @ `48 kHz`) float mic frames *first*, then mixes centered speech onto untouched stereo game audio ahead of a `-1.0 dBFS` master brickwall limiter.
2. **Why `AudioRecord.setPreferredDevice()` Instead of `AudioManager.setCommunicationDevice()`?**
   Calling `setCommunicationDevice()` alters global Android call routing and disrupts CODM's Bluetooth/speaker output during match transitions. Scoping input selection to our own `AudioRecord` instance leaves game audio routing untouched.
3. **Why OpenGL Black-Frame Clearing for `BRB` Instead of Detaching Capture?**
   Android 14+ (`API 34+`) enforces single-use `MediaProjection` consent tokens—calling `createVirtualDisplay()` a second time throws `SecurityException`. Rendering black frames inside `SolVideoPipeline.runGlRenderLoop` preserves the single `VirtualDisplay` and keeps YouTube's 60 FPS keyframe cadence alive.
4. **Why Annex-B Start-Code Stripping in `SolVideoPipeline`?**
   Hardware `MediaCodec` `csd` buffers emit `00 00 00 01` start codes; stripping prefixes before passing raw `VPS / SPS / PPS` NAL payloads into `RtmpClient.setVideoInfo(...)` guarantees compliant Enhanced RTMP sequence headers.

### Local Build & Persistent Release Signing
```bash
gradle assembleDebug assembleRelease -x lint -x lintVitalRelease -Pandroid.suppressUnsupportedCompileSdk=36,37
```
To preserve update signatures across GitHub Actions builds without uninstalling, generate a `.jks` keystore once and add `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` to **GitHub Settings → Secrets and variables → Actions** (automatically falls back to debug signing when unset).

---

## 6. For AI Coding Assistants / LLMs: Context & Strict Invariants

If a user pastes this repository into an AI assistant (Gemini, ChatGPT, Claude, Copilot, Cursor, etc.) for modification, **strictly obey these architectural invariants**:

1. **NEVER promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK`** from `MediaProjectionManager.createScreenCaptureIntent()`.
2. **NEVER call `mediaProjection.createVirtualDisplay(...)` more than once per consent token**, and always call `mediaProjection.registerCallback(...)` *before* `createVirtualDisplay(...)`.
3. **NEVER allocate objects (`FloatArray`, `ByteArray`, `ByteBuffer`) inside `MicDspProcessor.processMonoFrameInPlace` or the per-frame audio/GL loops**—preserve zero-GC steady state for 4-hour 120 FPS sessions.
4. **NEVER re-introduce `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, `GenericStream`, `AudioManager.setCommunicationDevice()`, software `AV1` (`c2.android.av1.encoder`), `KEY_ALLOW_FRAME_DROP = 0`, or non-standard `40/45/48 FPS` YouTube modes.**
5. **Native JNI Hook:** If adding native C++ `RNNoise` in a PR, hook directly into `MicDspProcessor.processMonoFrameInPlace` (`480` mono float samples @ `48 kHz`), compile with **Android NDK r28+** using `-Wl,-z,max-page-size=16384`, and verify ELF `LOAD` alignment (`2**14`).

---

## 7. For Security Auditors, Streamers & Forkers

* **Privacy & Security Auditability:**
  * **Zero Telemetry / Zero Ads:** Connects *only* to your configured RTMPS ingest server (plus `api.github.com` solely when you manually tap *"Check GitHub Update"*).
  * **Stream Key Protection:** Encrypted via `AndroidKeyStore` (`AES/GCM/NoPadding`), redacted via regex in `DiagnosticsLogger`, and stripped from Release Logcat via `proguard-rules.pro` (`-assumenosideeffects class android.util.Log`).
  * **Minimal Accessibility Scope:** `canRetrieveWindowContent="false"`—used strictly to host `TYPE_ACCESSIBILITY_OVERLAY` and satisfy Android's foreground microphone-sharing policy without reading screen text.
* **Forking for Other Games or Channels:**
  * You are welcome to fork this repo under the **Apache 2.0 License** for *BGMI*, *Valorant Mobile*, *Free Fire*, *Genshin Impact*, or custom RTMP servers (Twitch / Kick / Restream—simply select `H264` + `1080p60` in Card 3 and paste your platform's ingest URL in Card 2!).

---

## License

Licensed under the **Apache License 2.0**. Built with gratitude atop [pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder).
