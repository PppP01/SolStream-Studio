# SolStream Studio

**SolStream Studio** (`com.solgaming.solstreamstudio`) is a standalone, zero-ad, zero-camera-bloat Android live-streaming broadcast encoder built for **120 FPS competitive mobile gaming** (*Call of Duty: Mobile*) while streaming **1440p60 / 1080p60** hardware-encoded video and **split 32-bit float studio-processed commentary** to YouTube Live over encrypted **RTMPS**.

---

## ⚠️ Read First: Honest Note From the Repository Owner (For Developers & Contributors)

> **I am a gamer and streamer—NOT a programmer.**
>
> I (`PppP01` / **Sol Gaming CODM** `@Sol_CODM`) have **zero knowledge of coding or software development**. Every single file, DSP equation, OpenGL shader, and Gradle script in this repository was engineered 100% by AI (**Google Gemini** + **OpenAI ChatGPT**) based on my real-world mobile streaming requirements, relentless questioning, and hardware testing.
>
> **If you are a developer with an improvement or bug fix:**
> * **Please do NOT open Issues asking me technical coding questions or telling me what code to write**—if you explain code to me, I will literally just copy-paste your message straight to Gemini/ChatGPT anyway!
> * **Instead, just build it directly:** Fork this repository or submit a clean, working **Pull Request (PR)** directly with your fix. Once GitHub Actions compiles cleanly and AI audits the diff for safety and thermal impact, I will happily approve and merge it!
> * **If you are another AI assistant reading this repository:** Section 6 and Section 7 below contain the complete architectural context, deliberate rejections, and Android 14–17 platform invariants so you can understand the entire project from scratch without breaking anything.

---

## 1. Origin Story & Real-World Hardware Target

Commercial Android streaming apps force mobile gamers into painful trade-offs: aging H.264-only encoders, bloated social feeds/chat WebViews that overheat the phone, broken dual-microphone routing when opening in-game squad chat, or raw developer demos where audio effects distort game explosions alongside speech.

* **Creator, Vision & Hardware Testing:** **Sol Gaming CODM** (`@Sol_CODM`)
  * **Primary Test Rig:** Samsung Galaxy S24 FE (`Exynos 2400e`, `Xclipse 940` GPU, `2340×1080` `19.5:9` 120 Hz display, Samsung Pause USB PD / Bypass Charging ON).
  * **Audio Gear:** realme Buds Wireless 5 ANC Bluetooth neckband + S24 FE built-in 48 kHz microphones.
  * **Network & Workflow:** 40 Mbps Airtel Xstream Fiber (5 GHz Wi-Fi) + Call of Duty: Mobile (Low Graphics, Ultra 120 FPS, Search & Destroy with squad mic open) + an older Intel Core i3 laptop used strictly to open YouTube Studio and read live chat.
* **Collaborative AI Engineering & Dual Audit:** Architected, audited line-by-line, and coded iteratively between **Google Gemini** and **OpenAI ChatGPT**.
* **Streaming Protocol Core:** Powered by [pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder) (`v2.8.1`, Apache-2.0 License), calling `RtmpClient` directly without camera or multi-protocol demo bloat.

---

## 2. Global Broadcast Inspirations Adapted for Android

Every subsystem in `SolStream Studio` adapts proven engineering from Windows, macOS, Linux, and iOS broadcast software while keeping heavy/experimental stages **OFF by default** to protect 120 FPS gameplay thermals:

| SolStream Studio Subsystem | Inspired By (Windows / Mac / iOS / Web) | What It Solves on Android |
|---|---|---|
| **Split Mic-vs-Game 32-Bit Float Audio Bus** | **OBS Studio**, **Reaper DAW** & **Elgato Wave Link** | Processes noise suppression, High-Pass, Gate, EQ, De-Esser, and Compression **strictly on your mic** before mixing with stereo game audio—never muddies footsteps or alters what you hear in your earphones. |
| **64-Bit Internal Biquad Filter Precision** | **Pro Tools / Reaper DAW** Low-Frequency Math | Uses 64-bit `Double` accumulators inside recursive `75–100 Hz` High-Pass and vocal EQ filters so bass filtering introduces zero rounding noise while keeping stream buffers fast in 32-bit float. |
| **Podcast Vocal Curve + Dynamic De-Esser** | **Voicemeeter Banana**, **SteelSeries Sonar** & **Aphex** | Adds `125 Hz` chest warmth, cuts `350 Hz` phone-mic boxiness, boosts `3.8 kHz` callout clarity over gunfire, and tames harsh `6.5 kHz` `"S/Sh"` sibilance. |
| **Smart Mic Sharing & Silencing Watchdog** | **Android AOSP**, **WebRTC** & **Glip** analysis | Combines a `0-Hz` sleeping `TYPE_ACCESSIBILITY_OVERLAY` pill with `AudioSource.DEFAULT` / `VOICE_RECOGNITION` selection and `AudioRecordingCallback` (`MIC OK` vs `MIC SILENCED` live badge). |
| **120 Hz Gameplay → 60/30 FPS Stream Pacer** | **Desktop Capture-Card Passthrough** | Keeps CODM running at native 120 FPS on your screen while pacing encoder output cleanly at `60 FPS` (`2:1`) or `30 FPS` (`4:1`). |
| **CODM HUD Framing (`Fit` / `Smart` / `Fill`)** | **Apple iOS ReplayKit** & **OBS Canvas** | Fits `19.5:9` / `20:9` phones into `16:9` YouTube without stretching aim or cutting off your Search & Destroy minimap/ammo HUD—while concentrating 100% of bitrate onto active pixels in `Fit` mode. |
| **Optional Single-Pass GPU Crisp Shader** | **AMD FidelityFX CAS** | Optional subtle contrast-adaptive sharpening inside the mandatory single `OES` texture pass (`OFF` by default; auto-disables if phone warms up). |
| **Queue-Aware Bitrate & Health Recovery** | **RootEncoder 2.8.1** & **SQM Bufferbloat** | Watches real outgoing send-queue bytes and drops bitrate smoothly *before* router queues spike in-game CODM ping, with automatic backoff reconnects. |

---

## 3. Complete Feature & Control Encyclopedia

### A. Pre-Stream Studio Dashboard (`MainActivity.kt`)
1. **Card 1 — Pre-Flight Readiness & Samsung One UI Guide:**
   * Live `READY / NEEDED` checklights for Microphone, Android 13+ Notifications, Unrestricted Battery (anti-Deep-Sleep), and Accessibility Service, plus a built-in step-by-step Samsung One UI *Restricted Settings* & Bluetooth setup popup.
2. **Card 2 — Encrypted YouTube RTMPS & Hardware Keystore Vault (`KeystoreVault.kt`):**
   * Pre-filled `rtmps://[a.rtmp.youtube.com:443/live2](https://a.rtmp.youtube.com:443/live2)` TLS port-443 ingest URL (editable for custom RTMP/RTMPS servers).
   * **AES-256-GCM Hardware Keystore Encryption:** Encrypts your YouTube Stream Key inside Android's hardware keystore (`SolStreamMasterAesKey_v1`), with 1-tap Clipboard Paste and Show/Hide toggle.
   * **1-Tap Airtel / Wi-Fi RTMPS Ping & Jitter Probe:** Runs 4 encrypted TLS handshakes to `a.rtmp.youtube.com:443`, measures average RTT + jitter in milliseconds, and recommends tonight's safest Mbps target.
3. **Card 3 — Video Encoder, 120→60 FPS Pacing & HUD Framing (`SolVideoPipeline.kt` + `CodecCapabilityProbe.kt`):**
   * **Auto Hardware Silicon Scanner:** Probes `MediaCodecList`, `VideoCapabilities`, `EncoderCapabilities`, and `PerformancePoint` on first launch and runs a real `COLOR_FormatSurface` dry-run check.
   * **Video Codecs:** Hardware **`HEVC (H.265)`** (default), Hardware **`H.264 (AVC)`** (universal compatibility), and Hardware **`AV1`** (unlocks automatically *only* on chips with verified hardware AV1 encoding; CPU software encoders are strictly blocked).
   * **Resolutions & Frame Rates:** **`1440p` (`2560×1440`)** or **`1080p` (`1920×1080`)** at **`60 FPS`** (normal `2:1` cadence from 120 Hz) or **`30 FPS`** (low-bandwidth `4:1` saver).
   * **Orientation & Cable-Side Flip:** **`16:9 Landscape (0°)`**, **`16:9 Landscape Flipped (180°)`** (so flipping your phone to plug in a Bypass Charging cable never turns the stream upside down), and **`9:16 Vertical Shorts`**.
   * **Bitrate & Rate Control:** Presets for **`12 / 16 / 18 / 20 [Default] / 22 / 24 Mbps`**, **`CBR` (Recommended)** vs **`VBR` (Keyframe Quality Test)**, **`2-second` keyframes**, **`Rec.709` SDR Limited color signaling**, and **Queue-Aware Adaptive Bitrate (`ON / OFF`)**.
   * **Aspect-Ratio Framing & Visuals:** **`FIT` (Default — full CODM HUD safe)**, **`SMART` (`104% / 108% / 112% / 116%` zoom)**, **`FILL` (full 16:9 crop)**, and **Optional GPU Crisp Sharpen (`OFF [Default] / ON`)**.
4. **Card 4 — 7-Stage 32-Bit Float Studio Audio & 5-Second Mic Test Studio (`SolDualAudioEngine.kt` + `MicDspProcessor.kt`):**
   * **Enumerated Mic Input Picker:** Auto-lists Built-in Phone Mics, Bluetooth SCO/BLE Headsets, Wired Headsets, and USB-C Mics via `AudioDeviceInfo` + `AudioRecord.setPreferredDevice()`.
   * **Audio Source Selector:** `AUTO (Glip Smart: DEFAULT on Phone Mic / VOICE_RECOGNITION on BT)`, `MIC (Raw)`, and `VOICE_RECOGNITION`.
   * **Voice Presets:** `RAW` (bypass), `CLEAN` (default: 85 Hz HPF + Breath Gate + Compressor + Master Limiter), and `STUDIO` (enables full 3-Band Podcast EQ + Dynamic De-Esser + Gate + Compressor).
   * **Individual DSP Toggles:**
     * **Wind/Grip High-Pass Filter:** `OFF / 75 Hz / 85 Hz / 100 Hz`
     * **Stationary Fan Noise Suppressor:** `OFF [Default] / LOW / MED`
     * **Hysteresis Breath Gate (`150 ms` hold) & `3:1` Leveling Compressor:** Independently toggleable
     * **3-Band Vocal EQ (`125 Hz / 350 Hz / 3.8 kHz`) & `6.5 kHz` De-Esser:** Independently toggleable
     * **Mic Volume Boost (`75%–220%`) & Game Stream Mix Level (`0%–120%`)**
     * **Speech-VAD Game Auto-Ducking:** `OFF [Default] / -20% / -35%`
     * **Mic Lip-Sync Delay Buffer:** `0 / +40 / +80 / +120 / +160 ms`
     * **Extra Safeguards:** `BRB Auto-Mute Mic (ON/OFF)` and Experimental `Wi-Fi Low-Latency Lock (OFF/ON)`
   * **5-Second Mic Test Studio:** Records 5 seconds in your actual CODM hand grip (microphone-only, zero screen-capture prompt), displays Peak dB, Noise Floor dB, and Clip count, plays back on tap, and deletes cleanly.
5. **Card 5 — Broadcast Launch, Automatic Session Notes, Glip Benchmark & Updater:**
   * **Automatic Plain-English Session Report (`DiagnosticsLogger.kt`):** Copyable/shareable report recording plain-English event explanations, encoder chip name, real FPS, live Mbps, send-queue KB, battery start/peak °C, Android thermal status & headroom %, app PSS RAM MB, A/V drift ms, and active Android recording states—**strictly redacting stream keys and never recording voice or notifications**.
   * **Passive Glip Comparison Benchmark:** Measures start/peak °C, thermal status, and RAM while you stream in Glip so you can compare Glip vs. SolStream side by side under identical conditions.
   * **1-Tap GitHub Release Update Checker:** Queries `PppP01/SolStream-Studio` Releases on demand (zero background polling during gameplay).

---

### B. In-Game Floating Live Studio Pill (`SolStreamService.kt`)
* **0-Hz Sleeping Ghost Mode:** Collapses to a draggable `38%`-opacity translucent badge with **zero timer/text redraws per second** when closed, eliminating `SurfaceFlinger` composition overhead during 120 FPS CODM.
* **Anti-Misclick Guard:** Ignores accidental single-tap grazes while aiming—requires a **Double-Tap (or `0.4s` Hold)** to expand.
* **14 Crash-Free Mid-Stream Live Controls (Never Detaches Capture):**
  1. Live Timer + Outgoing Mbps + Real Encoded FPS + Send-Queue KB Header
  2. Connection State + `MIC OK / MIC SILENCED` Live Peak dB + `GAME OK / MIC-ONLY` Status Row
  3. **1-Tap Stream Mic Mute** (mutes your voice on YouTube while keeping your CODM squad voice chat active)
  4. **1-Tap `GAME + SQUAD STREAM` Mute** (mutes captured game audio on stream while you keep hearing footsteps in your earphones)
  5. **1-Tap `BRB Privacy Blackout` Shield** (sends pure black 60 FPS OpenGL frames + optional mic mute without detaching `MediaProjection`)
  6. Live Voice Preset Cycler (`RAW ↔ CLEAN ↔ STUDIO`)
  7. Live Fan Noise Suppressor Cycler (`OFF ↔ LOW ↔ MED`)
  8. Live Wind/Grip High-Pass Cycler (`OFF ↔ 75 ↔ 85 ↔ 100 Hz`)
  9. Live Gate / EQ / Compressor / De-Esser Combo Toggle
  10. Live Mic Boost Cycler (`75%–220%`)
  11. Live Game Mix Level Cycler (`30%–120%`)
  12. Live VAD Auto-Ducking Cycler (`OFF ↔ -20% ↔ -35%`)
  13. Live Framing (`FIT ↔ SMART ↔ FILL`), `0°/180°` Flip & GPU Crisp Sharpen Toggle
  14. Live On-The-Fly Target Bitrate Nudge (`12M–24M`) + **2-Tap Confirm Stop Stream**

---

## 4. Recommended YouTube Studio & Samsung One UI Setup

1. **YouTube Studio (On Laptop/Desktop Browser):**
   * Create a **Custom Stream Key** → check **"Turn on manual settings" → `1440p` (`60 fps`)** so YouTube pre-allocates high-efficiency transcoding.
   * Set **Latency = Low-latency**, **Auto-start = ON**, **Auto-stop = OFF** (prevents YouTube from ending your stream during brief Wi-Fi reconnects), and enable YouTube's server-side **Auto Dual-Format** if dual horizontal + vertical Shorts streaming is desired.
2. **Samsung One UI / Android 13–17 Sideloaded APK Unlock:**
   * If Samsung **Auto Blocker** blocks installing the GitHub APK, toggle *Settings → Security and privacy → Auto Blocker → OFF*, install the APK, and turn Auto Blocker back ON.
   * Unlock Accessibility once: **Settings → Apps → SolStream Studio → tap `⋮` (top right) → Allow restricted settings**, then enable **SolStream Studio** in **Settings → Accessibility → Installed apps**.
3. **realme Neckband Stereo + 45ms Game Mode Tip:**
   * To prevent Bluetooth from dropping into mono Hands-Free (`HFP`) call audio when CODM's mic opens, open **Bluetooth Settings → Earphones Gear Icon → toggle Calls OFF (keep Audio ON)** so CODM and SolStream share your phone's 48 kHz built-in mic while your neckband stays locked in crisp Stereo + 45ms Game Mode. Lock *Call of Duty* to Bluetooth in Samsung **Separate App Sound** so match transitions never jump to the phone speaker.
4. **Notification Privacy:**
   * Select **"A single app" → Call of Duty** on Android's screen-capture prompt so WhatsApp messages, calls, and OTPs never appear on stream.

---

## 5. Known Android Platform Realities (Honest Boundaries)

* **CODM Squad Teammate Voice Capture:** Android's `AudioPlaybackCapture` captures `USAGE_GAME`, `USAGE_MEDIA`, and `USAGE_UNKNOWN`, while blocking `USAGE_VOICE_COMMUNICATION` for system privacy. Gunfire and footsteps capture cleanly; teammate voices appear on stream only when the game outputs squad audio on a capturable game/media track. Similarly, `GAME + SQUAD STREAM MUTE` mutes the combined internal playback mix rather than isolating teammates from gunfire.
* **Lock-Screen Capture Rule (Android 15 QPR1+):** Modern Android automatically stops `MediaProjection` if you physically lock the phone screen. Use the floating **BRB Privacy Blackout** button instead of locking the phone during breaks; if locked accidentally, `SolStream Health & Recovery` pauses safely and shows a 1-tap orange re-approval button.

---

## 6. Deliberate Architectural Rejections (Do Not Re-Introduce in PRs)

* **Rejected `GenericStream` & `extra-sources`:** Avoids bundling RTSP, SRT, UDP, CameraX, and UVC dependencies; `RtmpClient` + R8 shrinking keeps the APK ~4–8 MB.
* **Rejected Post-Mix `CustomAudioEffect`:** `RootEncoder`'s built-in `MixAudioSource` applies effects *after* mixing mic and game PCM16; keep mic DSP strictly upstream in `SolDualAudioEngine`.
* **Rejected `AudioManager.setCommunicationDevice()`:** Globally alters Android call routing and disrupts CODM audio output; keep input selection scoped to `AudioRecord.setPreferredDevice()`.
* **Rejected Mid-Stream `VirtualDisplay` Recreation / `NoVideoSource` Swapping:** Violates Android 14–17 single-use `MediaProjection` token rules (`SecurityException`).
* **Rejected `KEY_ALLOW_FRAME_DROP = 0`:** Prevents Surface input from discarding excess 120 Hz frames down to 60 FPS.
* **Rejected Software AV1, Cloud Voice AI, 64-Bit Sample Buffers & 45/48 FPS Modes:** Protects CPU thermals, avoids 200–600 ms cloud audio lag, avoids wasted RAM bandwidth, and matches YouTube Live's documented `30fps` / `60fps` ingest modes.

---

## 7. Developer & AI Technical Reference

### Source Architecture
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

### Code Hooks for Contributors
* **Native JNI Audio Hook (`480` samples = `10 ms` @ `48 kHz`):** `MicDspProcessor.processMonoFrameInPlace(buffer: FloatArray, count: Int)` processes normalized `[-1.0f, 1.0f]` mono float frames of exact `RNNoise` frame length (`480` samples) with zero heap allocations. Any native `.so` added in a PR must compile against **Android NDK r28+** (`-Wl,-z,max-page-size=16384`) for Android 15–17 16 KB page compatibility (`useLegacyPackaging = false` is enabled).
* **OpenGL Single-Pass Shader:** `GlShaderHelper.FRAGMENT_SHADER` in `SolVideoPipeline.kt` samples `samplerExternalOES` directly into the hardware encoder `EGLSurface`. Do not introduce multi-pass FBO chains unless gated OFF by default and thermally benchmarked.

### Build Commands & GitHub Actions Signing Secrets
```bash
gradle assembleDebug assembleRelease -x lint -x lintVitalRelease -Pandroid.suppressUnsupportedCompileSdk=36,37
```
To preserve update signatures across GitHub Actions builds without uninstalling:
```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias solstream
base64 -w 0 release.jks > release_base64.txt
```
Add `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` under **GitHub Settings → Secrets and variables → Actions** (automatically falls back to debug signing when unset so every build produces an installable APK).

---

## License

Released under the **Apache License 2.0**.
