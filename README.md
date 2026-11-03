# SolStream Studio

**SolStream Studio** (`com.solgaming.solstreamstudio`) is a standalone, zero-ad, zero-camera-bloat Android live-streaming broadcast encoder engineered for **120 FPS competitive mobile gaming** (*Call of Duty: Mobile*) while streaming **1440p60 / 1080p60** hardware-encoded video and **split 32-bit float studio-processed commentary** to YouTube Live over encrypted **RTMPS**.

---

## 1. Origin Story: A Non-Coder Gamer's Vision + Dual-AI Engineering

Most mobile streaming apps force gamers into compromises: aging H.264-only video, bloated social feeds and camera overlays that heat up the phone, broken Bluetooth/squad microphone routing, or raw developer demos where audio filters muddy game explosions alongside speech.

* **Creator & Product Architect:** **Sol Gaming CODM** (`@sol_codm`) — a competitive *Call of Duty: Mobile* streamer (playing 120 FPS Search & Destroy on a Samsung Galaxy S24 FE with Bypass Charging, realme Buds Wireless 5 ANC neckband, 40 Mbps fiber upload, and an older i3 laptop used strictly for reading YouTube Studio chat). With zero prior coding background, the creator pushed to research top broadcast tools across Windows, macOS, Linux, iOS, and Android, cross-referencing official documentation with ground-reality player and developer reports.
* **Collaborative AI Engineering & Code Audit:** Designed, cross-audited, and written iteratively between **Google Gemini** and **OpenAI ChatGPT**—combining low-level Android AOSP media rules, hardware encoder silicon verification, studio DSP math, and strict thermal/reliability guardrails.
* **Protocol Foundation:** Built atop [pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder) (`v2.8.1`, Apache-2.0 License), using `RtmpClient` directly rather than multi-protocol demo wrappers.

---

## 2. Complete Feature Encyclopedia & Cross-Platform Inspirations

Every feature in `SolStream Studio` was adapted from proven desktop, mobile, and broadcast systems, with optional/heavy stages defaulted **OFF** so phone thermals and 120 FPS gameplay always come first.

### A. 7-Stage Split 32-Bit Float Studio Audio Engine (`SolDualAudioEngine` + `MicDspProcessor`)
Standard mobile mixers combine microphone and game audio in 16-bit integer *before* applying effects—meaning any noise gate or EQ damages footsteps and gunfire. `SolStream Studio` captures and processes your microphone on an isolated **32-bit float (`ENCODING_PCM_FLOAT`)** lane first, while leaving CODM stereo audio untouched until the final master bus:

1. **Isolated Dual-Path Capture & Smart Mic Sharing** *(Inspired by OBS Studio, Android AOSP & Glip reverse-engineering)*:
   * Captures mono `48,000 Hz` float microphone audio separately from stereo `48,000 Hz` `AudioPlaybackCapture` (`USAGE_GAME`, `USAGE_MEDIA`, `USAGE_UNKNOWN`).
   * **Smart Source Rule:** Defaults to `MediaRecorder.AudioSource.DEFAULT` for built-in phone mics alongside `AccessibilityService` sharing, and switches to `VOICE_RECOGNITION` when a Bluetooth/headset mic is explicitly chosen (with manual override for `AUTO`, `MIC`, and `VOICE_RECOGNITION`).
   * **Device-Level Routing:** Uses `AudioRecord.setPreferredDevice()` on enumerated `AudioDeviceInfo` inputs rather than globally hijacking `AudioManager.setCommunicationDevice()` (preventing mid-match CODM speaker/earphone jumps).
   * **Live Silencing Detector:** Registers `AudioManager.AudioRecordingCallback` (`isClientSilenced`) to display real-time `MIC OK` vs. `MIC SILENCED` status in the floating overlay.
2. **Stage 1 — 64-Bit Butterworth High-Pass Filter (`OFF / 75 Hz / 85 Hz [Default] / 100 Hz`)** *(Inspired by Reaper DAW & Broadcast Mixing Consoles)*:
   * Uses `Double` (64-bit) internal biquad accumulators inside 32-bit float buffers to slice off sub-bass ceiling-fan wind, AC rumble, plosives, and two-hand phone-grip frame thumps without thinning out 85 Hz+ male vocal warmth.
3. **Stage 2 — Continuous Noise-Floor Tracker & Optional Fan Suppressor (`OFF [Default] / LOW / MED`)** *(Inspired by WebRTC NS & OBS Spectral Suppression)*:
   * Tracks stationary room/ceiling-fan noise during speech pauses and applies smooth spectral high-band attenuation when enabled (`0%` CPU cost when `OFF`).
4. **Stage 3 — Voice Activity Detector (VAD) & Hysteresis Breath Expander (`ON [Default] / OFF`)** *(Inspired by Broadcast Voice Processors)*:
   * Combines dynamic SNR ratio + speech zero-crossing rate with a `150 ms` hold window and gentle `-13 dB` downward expansion—suppressing heavy clutch breathing and idle hiss without chopping quiet word endings.
5. **Stage 4 — Optional 3-Band "Podcast Voice" Parametric EQ (`OFF` in Clean, `ON` in Studio)** *(Inspired by Voicemeeter Banana & SteelSeries Sonar)*:
   * `+1.2 dB @ 125 Hz` (warm vocal depth), `-2.2 dB @ 350 Hz` (removes hollow phone-mic boxiness), and `+2.2 dB @ 3.8 kHz` (vocal callout clarity over CODM gunfire).
6. **Stage 5 — Optional Dynamic Sibilance De-Esser (`OFF` in Clean, `ON` in Studio)** *(Inspired by Aphex / FabFilter Broadcast Chains)*:
   * `6.5 kHz` bandpass sidechain detector tames piercing `"S"` and `"Sh"` harshness on mobile mic capsules when vocal presence is boosted.
7. **Stage 6 — `3:1` Broadcast Leveling Compressor (`ON [Default] / OFF`)** *(Inspired by OBS Compressor)*:
   * Smooths volume swings above `-12 dBFS` with `1.12x` makeup gain so quiet whispers and loud clutch reactions stay balanced.
8. **Stage 7 — Stereo Mixer, VAD Auto-Ducking, Lip-Sync Offset & `-1.0 dBFS` Master Limiter** *(Inspired by Elgato Wave Link)*:
   * Centers mono speech across untouched Left/Right stereo game audio (never alters what the player hears in their own earphones).
   * **VAD Auto-Ducking (`OFF [Default] / -20% / -35%`):** Smoothly dips stream game mix only while VAD detects active speech.
   * **Mic Lip-Sync Delay (`0–160 ms`):** Pre-allocated ring buffer aligns voice with gameplay/Bluetooth timing.
   * **True-Peak Brickwall Limiter (`-1.0 dBFS`):** Prevents combined boosted voice + grenade peaks from clipping before encoding to hardware **Stereo AAC-LC (`128 / 160 [Default] / 192 / 256 kbps` @ `48 kHz`)**.
9. **Pre-Stream 5-Second Mic Test Studio:**
   * Records a local 5-second sample in your real two-hand gaming grip (microphone-only, without starting `MediaProjection`), reports Peak dB, Noise Floor dB, and Clip count, plays back instantly, and deletes cleanly.

---

### B. Hardware Video Pipeline, 120-to-60 FPS Pacing & CODM HUD Framing (`SolVideoPipeline` + `CodecCapabilityProbe`)

1. **120 Hz Gameplay → Strict `60 FPS` / `30 FPS` Stream Pacing** *(Inspired by Desktop Capture-Card Passthrough)*:
   * Your phone screen continues rendering CODM at **120 FPS**, while `SolVideoPipeline`'s monotonic `System.nanoTime()` OpenGL ES pacer + Android 10+ `KEY_MAX_FPS_TO_ENCODER` deliver exact `60 FPS` (`2:1`) or `30 FPS` (`4:1`) to YouTube without frame-bursting.
2. **Automatic Hardware Silicon Scanner (`CodecCapabilityProbe`):**
   * Inspects `MediaCodecList`, `VideoCapabilities`, `EncoderCapabilities`, and `PerformancePoint` support, then performs a real `COLOR_FormatSurface` dry-run configuration.
   * Defaults to **Hardware `HEVC (H.265)`**, provides **Hardware `H.264 (AVC)`** fallback, automatically unlocks **Hardware `AV1`** only when verified hardware encoding silicon exists, and **strictly blocks software CPU encoders** (`c2.android.*` / `OMX.google.*`) so CODM never thermally throttles.
3. **Broadcast Rate Control & Color Signaling:**
   * Supports **`CBR` (YouTube Default)** and **`VBR` (Experimental Keyframe-Sharpness Comparison)**, `2-second` I-frame interval, mid-high hardware `KEY_COMPLEXITY`, and **SDR `Rec.709` Limited Range** color tags (with automatic fallback to baseline `MediaFormat` if any vendor driver rejects optional keys).
4. **`19.5:9` → `16:9` CODM HUD Safe Framing & Cable Flip** *(Inspired by iOS ReplayKit & OBS Canvas)*:
   * Captures `VirtualDisplay` at native panel dimensions so landscape games are never squished into portrait, then scales in OpenGL via **`FIT` (Default — preserves 100% of CODM minimap/ammo HUD while spending ~0 bits on static black bars)**, **`SMART` (`104%–116%` zoom)**, or **`FILL`**, plus **`9:16` Vertical Shorts** and **`0° / 180°` Landscape Flip** for left/right USB-C Bypass Charging cables.
5. **Optional Single-Pass GPU Crisp Sharpening (`OFF [Default] / ON`)** *(Inspired by AMD FidelityFX CAS)*:
   * Embedded inside the mandatory `GL_TEXTURE_EXTERNAL_OES` fragment pass (`0` extra render passes; `0%` GPU cost when `OFF`).
6. **Android 14–17 One-Shot `MediaProjection` Safe `BRB Privacy Blackout`:**
   * Android 14+ forbids calling `createVirtualDisplay()` more than once per consent token. Toggling **BRB Blackout** clears the OpenGL encoder surface to pure black at 60 FPS (and optionally mutes stream mic) *without* detaching `VirtualDisplay`—keeping YouTube stream health green with zero `SecurityException` crash risk.

---

### C. Network Armor, Security, Floating Live Pill & Self-Diagnostics

1. **Hardware Keystore Vault & RTMPS Jitter Probe (`KeystoreVault`):**
   * Encrypts your YouTube RTMPS URL (`rtmps://[a.rtmp.youtube.com:443/live2](https://a.rtmp.youtube.com:443/live2)`) and Stream Key on-device using `AndroidKeyStore` (`AES/GCM/NoPadding` 256-bit). Stream keys are redacted from all logs and stripped from Release Logcat via ProGuard. Includes a 1-tap **YouTube RTMPS TLS RTT & Jitter Tester** to recommend safe bitrates before going live.
2. **Queue-Aware Adaptive Bitrate & `SolStream Health & Recovery` (`SolStreamService`):**
   * Polls outgoing RTMP send-queue bytes and throughput with hysteresis: steps video bitrate down via `PARAMETER_KEY_VIDEO_BITRATE` when upload queues grow (preventing router bufferbloat ping spikes in CODM) and climbs back smoothly once stable.
   * Monitors `PowerManager` thermal status: automatically disables optional GPU sharpening at `Moderate` warmth and optional noise suppression at `Severe` warmth before OS throttling hits CODM.
   * Handles Android 15 QPR1+ lock-screen `MediaProjection.Callback.onStop()` gracefully—pausing video without crash-looping and showing an orange re-approval banner.
3. **`0-Hz` Sleeping Floating Accessibility Pill:**
   * Uses `TYPE_ACCESSIBILITY_OVERLAY` (requiring zero `SYSTEM_ALERT_WINDOW` permission). Collapses to a static `38%`-opacity Ghost Pill with **0 UI redraws per second** so Android's `SurfaceFlinger` wastes zero composition power, requiring a **Double-Tap or `0.4s` Hold** to expand so fast thumb flicks never open the menu accidentally.
4. **Automatic Plain-English Session Notes & Glip Benchmark (`DiagnosticsLogger`):**
   * Records chronological plain-English explanations alongside technical metrics (encoder chip, real FPS, Mbps, queue KB, battery °C, thermal headroom, PSS RAM, A/V drift ms, active `AudioRecordingConfiguration` states) plus a passive **Glip Baseline Benchmark** mode for fair A/B thermal/RAM testing.

---

## 3. Deliberate Architectural Rejections (For Future Developers & AIs)

To keep the app crash-free and cool over 3–5 hour sessions, the following approaches were **intentionally rejected** during design audits—do not re-introduce them:
* **No `GenericStream` or `extra-sources`:** Pulls RTSP/SRT/UDP/CameraX bloat; `RtmpClient` + R8 shrinking keeps the APK tiny.
* **No `MixAudioSource` Post-Mix Effects:** Filters game explosions alongside speech; always process mic float buffers *before* mixing.
* **No `AudioManager.setCommunicationDevice()`:** Globally alters call routing and breaks CODM audio output; use `AudioRecord.setPreferredDevice()`.
* **No Mid-Stream `VirtualDisplay` Recreation or `NoVideoSource` Swapping:** Crashes on Android 14–17 single-use `MediaProjection` tokens.
* **No `KEY_ALLOW_FRAME_DROP = 0`:** Prevents Surface input from dropping excess 120 Hz frames down to 60 FPS.
* **No Software AV1, Cloud AI Voice APIs, 64-Bit Sample Buffers, or 45/48 FPS Modes:** Software AV1 overheats CPUs; cloud AI adds 200–600 ms voice lag; 64-bit sample buffers waste memory bandwidth; YouTube's Live API standardizes on `30fps` and `60fps`.

---

## 4. Gamer Quick-Start Setup (Samsung One UI / Android 13–17)

1. **Unlock Restricted Accessibility Once:** Open **Settings → Apps → SolStream Studio → `⋮` (top right) → Allow restricted settings**, then enable **SolStream Studio** in **Settings → Accessibility → Installed apps**. *(Keep Google's extreme 'Advanced Protection' OFF if it blocks sideloaded Accessibility services.)*
2. **Preserve Stereo Footsteps + 45ms Game Mode on Bluetooth Neckbands:** If using the phone's 48 kHz mic while listening on Bluetooth earphones, open **Bluetooth Settings → Earphones Gear Icon → toggle Calls OFF (keep Audio ON)**, and lock *Call of Duty* to Bluetooth via Samsung **Separate App Sound**.
3. **Hide Notifications With Single-App Capture:** Choose **"A single app" → Call of Duty** on Android's screen-capture prompt so WhatsApp/OTP popups never appear on stream.

---

## 5. Developer & AI Contributor Onboarding

### Source Layout
```text
app/src/main/
├── AndroidManifest.xml                  # Minimal permissions (INTERNET, RECORD_AUDIO, FGS mediaProjection|microphone)
├── res/xml/accessibility_service_config.xml
└── java/com/solgaming/solstreamstudio/
    ├── MainActivity.kt                  # Pre-Stream Studio UI, 5s Mic Tester, RTMPS RTT probe, GitHub Updater
    ├── SolStreamService.kt              # AccessibilityService + ForegroundService, RtmpClient, Floating Pill, Watchdog
    ├── SolVideoPipeline.kt              # SurfaceTexture -> 60/30fps OpenGL ES 2.0 -> Hardware MediaCodec
    ├── SolDualAudioEngine.kt            # Split PCM_FLOAT Mic + AudioPlaybackCapture mixer -> Hardware AAC-LC
    ├── MicDspProcessor.kt               # Zero-allocation 32-bit float DSP (64-bit biquads, VAD, EQ, De-Esser, Comp)
    ├── CodecCapabilityProbe.kt          # Hardware MediaCodec & PerformancePoint verifier + AudioDeviceInfo scanner
    ├── KeystoreVault.kt                 # AndroidKeyStore AES-256-GCM stream-key vault & settings persistence
    └── DiagnosticsLogger.kt             # Redacted plain-English session report & Glip comparison benchmark
```

### Extending Audio DSP (`480`-Sample `10 ms` Native Hook) & Shaders
* **Audio / Native JNI `RNNoise`:** `MicDspProcessor.processMonoFrameInPlace(buffer: FloatArray, count: Int)` processes exact `480`-sample (`10 ms` @ `48 kHz`) mono float frames *before* game mixing. Any native C++/JNI `.so` added later must be compiled with **Android NDK r28+** (`-Wl,-z,max-page-size=16384`) for Android 15+ 16 KB page alignment (`useLegacyPackaging = false` is already configured).
* **Video Shader:** `GlShaderHelper.FRAGMENT_SHADER` in `SolVideoPipeline.kt` runs a single OES pass; avoid multi-pass FBO chains to protect mobile GPU thermals.

### Local Build & GitHub Actions Persistent Signing
```bash
gradle assembleDebug assembleRelease -x lint -x lintVitalRelease -Pandroid.suppressUnsupportedCompileSdk=36,37
```
To preserve update signatures across GitHub Actions releases without uninstalling, generate a `.jks` once (`keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias solstream`), Base64-encode it (`base64 -w 0 release.jks`), and store `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` under **GitHub Settings → Secrets and variables → Actions** (falls back to debug signing automatically if unset).

---

## License

Released under the **Apache License 2.0**.
