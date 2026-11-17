# SolStream Studio • ULTRA (`v1.3.0`)

**SolStream Studio Ultra** (`com.solgaming.solstreamstudio`) is a standalone, ultra-lightweight (`~3.7 MB` Signed Release APK & Google Play `.aab` / `~6.1 MB` Debug APK), **100% NO-ROOT**, zero-ad Android live-streaming broadcast studio powered by the proprietary **`SolApex™ HyperStream Engine`** (**`SolCore Ultra™ Video Engine`** • **`SolLuxe™ 2.0 Audio Engine`** • **`SolRelay Omega™ Broadcast Engine`** • **`SolCodec™ Hardware Silicon Layer`**). It is purpose-built for **90–185 Hz competitive mobile gaming** (*Call of Duty: Mobile, BGMI, Valorant Mobile*, etc.) while streaming crisp **`360p` to `4K60` (`0.5–100 Mbps`)** hardware-encoded video, **isolated 13-stage 32-bit float / 64-bit double vocal commentary with a 12-Mode Hardware Acoustic Matrix, Zero-Phase Hard-Breath Plosive Guard, Cubic Analog Sub-Warmth & Pure `2f` Aphex Silk Air Exciter**, and **stackable 3D stereo game soundstage** to **YouTube Live (Landscape `16:9` & Vertical `9:16` Shorts Feed), Twitch, Kick, and Custom RTMP/RTMPS servers**.

> **100% Unrooted Stock Android (Android 10–18 Ready & Google Play Compliant):** Works on normal, unrooted Android 10–18+ phones and tablets/foldables (`minSdk 29`, `compileSdk 37`) right out of the box—from budget `Lite` phones on 4G/5G mobile data to `Ultra` flagship gaming phones on 100+ Mbps Fiber. Root access is **never** required or used. See our [**Official Privacy Policy (`PRIVACY_POLICY.md`)**](./PRIVACY_POLICY.md).

---

## Polite & Honest Note From the Repository Owner (Please Read First!)

> **I am a competitive mobile gamer and streamer—NOT a coder or software developer.**
>
> I (`PppP01` / **Sol Gaming CODM** — [`@Sol_CODM`](https://youtube.com/@Sol_CODM)) know **nothing about programming, Kotlin, C, OpenGL, or Android Studio**. My role was defining real-world competitive gaming requirements, refusing compromises on 120+ FPS gameplay, phone thermals, touch response, and voice clarity, questioning every edge case, and testing marathon builds live on real hardware.
>
> **Original Custom Architecture (`SolApex™ HyperStream Engine v1.3.0`):** SolStream Studio Ultra is **not** a reskin or wrapper around a generic camera app. Its zero-copy OpenGL ES 3.0 (`Fast-FP16`) / ES 2.0 Direct-TBDR frame pacer (`SolCore Ultra™`), Hardware TMU-assisted 5-tap Bicubic/Lanczos upscaler with gated cardinal neighbor texture fetches (`14.74 million` fewer texture reads/frame at `1440p60`), Android 11–18 `Surface.setFrameRate()` hardware capture cooler, Vulkan `gralloc` PSS memory guard (`~205–229 MB` flat PSS across 3+ hour streams), `40%` cooler `glScissor` `9:16 BLUR` backdrop, `STRETCH (Full 16:9 Zero-Bars)` aspect scaler, 5-position aspect-preserving `.PNG` corner logo compositor, Dual-Layer Privacy Blur Shield (8 HUD zones + automatic `Auto-Blur Keyboard`), `AI Adaptive Dark-Corner Optimizer`, isolated `SolLuxe™ 2.0` **13-stage vocal & game soundstage DSP**, automatic **4-Hardware × 3-Gender (`12-Mode`) Acoustic Matrix** (`Phone Studio`, `Wired/USB-C Close-Talk`, `Bluetooth SCO`, `Speakerphone AEC`), **`50 / 65 / 85 Hz` Gender-Matched 64-Bit HPF**, **Zero-Phase-Peak 2-Stage `<135 Hz` Hard-Breath Plosive Guard**, **`+4.4 dB @ 95 Hz` Tight Cardioid Proximity Contour**, **Pure Analog Cubic Soft-Sine + Asymmetric Triode `2f` Sub-Warmth**, **Deeper `-3.5 dB @ 315–365 Hz` Boxy Mud Scoop + `920 Hz` "Sweet Velvet" Anti-Nasal Dip**, **Pure `2f` Upper-Harmonic "Aphex Silk Air" Exciter (`5.2–10.0 kHz` shimmer restoration during `16 kHz` in-game match locks)**, **High-Pass Sidechain `3:1` Compressor**, Stage 13 True Post-DSP Mic Boost (`50%–250%`), **Low-End CPU Budget Auto-Bypass Watchdog**, **Phase-Safe `62 Hz` Theater Sub-Punch & Single-Pass Zero-Phase `250 Hz + 2,450 Hz` Footstep Focus**, `16 KB`-aligned Native C `48 kHz RNNoise` engine (with ARM64 `FPCR` / ARMv7 `FPSCR` / `x86_64` `MXCSR` hardware Flush-to-Zero `FZ` register guards), drift-locked stereo game circular buffer (`±12 ms`), 4x cubic inter-sample look-ahead limiter, async hardware encoder capability scanner with global SoC/Vulkan-GPU mapper & cross-brand Bypass Charging (`0..45 mA` `Pause USB PD`) detector (`SolCodec™`), smart thermal bitrate governor, AES-256 Keystore non-destructive multi-slot vault with Pedro `RootEncoder` $\mu\text{s} \rightarrow \text{ms}$ RTT conversion, lock-free `PacketSlot` simulcast pool, Instant `Wi-Fi ↔ 5G` Multipath Failover, `8.5s` Handshake Timeout Watchdog & 5-Metric Network Analyzer (`SolRelay Omega™`), and 0-Hz sleeping 3-tab Accessibility Ghost Pill with live **App vs. Game Heat/CPU & RAM Split** are custom-engineered specifically for mobile esports.
>
> **How to Suggest Fixes or Improvements:**
> * Please do **NOT** open GitHub Issues asking me coding questions—if you explain code to me, I have no idea what it means and will literally copy-paste your message straight back to Gemini, ChatGPT, and Claude!
> * **Instead, submit a Pull Request (PR) directly:** Fork the repo, write the fix, verify that GitHub Actions (`testDebugUnitTest --rerun-tasks`, `lintDebug`, `assembleRelease`, and `bundleRelease`) passes green, and open a PR. After I run an AI safety and thermal check on the diff, I will gladly approve and merge your work!

---

## Table of Contents

1. [Download & Install Signed Release APK or Play Store Bundle (`v1.3.0`)](#1-download--install-signed-release-apk-or-play-store-bundle-v130)
2. [How Human + Multi-AI Built This App](#2-how-human--multi-ai-built-this-app)
3. [For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?](#3-for-casual-readers--gamers-what-does-solstream-studio-ultra-actually-do)
4. [The 10 Custom `SolApex™ v1.3.0` Engineering Innovations (Found in No Other Android App)](#4-the-10-custom-solapex-v130-engineering-innovations-found-in-no-other-android-app)
5. [Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained](#5-complete-plain-english-user-manual-every-card-drawer--floating-tab-explained)
6. [Global Broadcast Inspirations (Windows, macOS & iOS -> Android)](#6-global-broadcast-inspirations-windows-macos--ios---android)
7. [For Android Developers & Engineers: Architecture, 13-Stage Audio & 6-Stage Video](#7-for-android-developers--engineers-architecture-13-stage-audio--6-stage-video)
8. [For AI Coding Assistants, Security Auditors & Forkers](#8-for-ai-coding-assistants-security-auditors--forkers)
9. [Privacy Policy, Google Play Compliance & Open-Source Licenses](#9-privacy-policy-google-play-compliance--open-source-licenses)

---

## 1. Download & Install Signed Release APK or Play Store Bundle (`v1.3.0`)

1. Open the **[Releases](https://github.com/PppP01/SolStream-Studio/releases)** page on this repository.
2. Tap **`SolStream-Studio-v1.3.0.apk`** under **Assets** to download the full **Ultra Master Edition** (`~3.7 MB` signed release APK) directly onto your Android phone (or download **`SolStream-Studio-v1.3.0-PlayStore.aab`** for Google Play Console distribution). All earlier versions (`v1.0.2` – `v1.2.1`) remain permanently available in Releases.
3. **Seamless 1-Tap Updates:** All official Releases are signed with the same permanent V3 cryptographic key—you can install **`v1.3.0`** (`versionCode = 10`) straight over any earlier version (`v1.2.1` or older) as an **Update** without uninstalling, re-entering your Stream Key, or redoing Restricted Accessibility!

---

## 2. How Human + Multi-AI Built This App

This repository was engineered through a rigorous verification loop between a competitive mobile gamer and frontier AI models checking each other's blind spots:

- **Product Vision, Requirements & Live Marathon Testing — `Sol Gaming CODM` ([`@Sol_CODM`](https://youtube.com/@Sol_CODM)):**
  * Defined real-world competitive constraints and validated builds across **multiple 3-to-5.5 hour (`173m–330m`) continuous `1440p60 HEVC` (`18–20 Mbps`) Multiplayer and Battle Royale live streams** on an unrooted **Samsung Galaxy S24 FE** (`Exynos 2400e • Samsung Xclipse 940 (Vulkan)`, `2340×1080` 120 Hz display, 8 GB RAM), **realme Buds Wireless 5 ANC** neckband earphones, and 5 GHz Fiber Wi-Fi (`0` sender frame drops, `0 ms` send queue).
  * Identified real-world vocal & hardware edge cases from live stream telemetry: the `16,000 Hz` Samsung HAL match lock during CODM team voice chat, hard-breath/blowing-air plosive ducking, deeper `315–365 Hz` boxy room/hand-grip mud scooping, upper-harmonic `5.2–10.0 kHz` air shimmer restoration, live App vs. Game CPU/RAM resource split transparency, unified `SolApex™` `ON/OFF` UI visual hierarchy, and 100% Google Play Store compliance.
- **System Architecture, Native C/OpenGL/DSP Engineering & Code Generation — `Google Gemini`:**
  * Engineered the pure-Kotlin zero-XML Android architecture, `16 KB`-aligned Native C `48 kHz RNNoise` JNI engine (`sol_rnnoise_jni.c` with hardware `FPCR`/`FPSCR`/`MXCSR` Flush-to-Zero `FZ` register guards), `SolLuxe™ 2.0` **13-stage vocal & game soundstage DSP** (`MicDspProcessor` + `SolDualAudioEngine`), `SolCore Ultra™` OpenGL ES 3.0 `Fast-FP16` visual pipeline (`SolVideoPipeline`), `SolCodec™` global SoC/GPU scanner & cross-brand Bypass Charging detector (`CodecCapabilityProbe`), `SolRelay Omega™` non-destructive hardware Keystore vault, Pedro `RootEncoder` $\mu\text{s} \rightarrow \text{ms}$ RTT bridge, lock-free `PacketSlot` pool, `8.5s` Handshake Timeout Watchdog, Instant `Wi-Fi ↔ 5G` Failover & 5-Metric Network Analyzer (`KeystoreVault` + `SolStreamService`), and GitHub Actions NDK APK + Play Store `.aab` CI/CD pipeline.
- **Independent Spec Audit & Edge-Case Verification — `OpenAI ChatGPT` & `Anthropic Claude`:**
  * Audited code paths across release tags against Android 10–18 AOSP rules and Pedro's `RootEncoder` internals, verifying `getRtt()` microsecond scaling, `setCheckServerAlive(false)`, lock-free auxiliary buffer pooling, final worker `isAlive` teardown guards, non-destructive Keystore exception handling, and a **30-test fresh JUnit verification suite**.

---

## 3. For Casual Readers & Gamers: What Does SolStream Studio Ultra Actually Do?

Most Android streaming apps hurt competitive gaming in at least one way: they bundle `45–180 MB` of camera masks, AdMob ads, GPS/Contact trackers, and coin timers; lock `1440p/HEVC` behind expensive subscriptions; let Android drop your microphone to muffled `16 kHz` call mode when your game opens; or cut your stream bitrate whenever the phone gets slightly warm.

**SolStream Studio Ultra (`SolApex™ HyperStream Engine v1.3.0`) solves every one of those problems in a `~3.7 MB` (Release), unrooted, zero-ad studio:**

1. **Branded `SolApex™` Studio Header, 3-Tile Hero Summary Bar & Live Pre-Stream Hardware Telemetry:**
   * Features the `S•` Energy Signal Crest header, Engine Identity Pill Badges on every card (`[ SOLAPEX™ TIER ]`, `[ PRE-FLIGHT ]`, `[ SOLRELAY OMEGA™ ]`, `[ SOLCORE ULTRA™ ]`, `[ SOLLUXE™ 2.0 ]`, `[ LAUNCH DECK ]`), and a 3-Tile Hero Summary Bar (`[ 🎥 1440p (2K) ] [ ⚡ 60 FPS ] [ 📡 20 Mbps ]`) alongside live pre-stream hardware health (`READY • Idle Temp: 33.1°C (Cool) • SoC Load: 58% • Free RAM: 4.2/8GB • ⚡ AC / Bypass PD (Est.)`).
2. **Plays at 90–185 FPS on Your Screen, Streams Smoothly at 60/30 FPS (`360p` to `4K60` • `0.5` to `100 Mbps`):**
   * Your phone display stays at its native `120 / 144 / 165 / 185 Hz` refresh rate while Android 11–18 `Surface.setFrameRate()` hardware pacing and dedicated video silicon (`SolCodec™`) encode `360p`, `480p`, `720p`, `1080p`, `1440p`, or `4K (2160p)` from `0.5 Mbps` up to `100 Mbps` with **1-tap direct segmented pills** and a **3-Attempt Bare-Metal Hardware Encoder Fallback Ladder**.
3. **`SolLuxe™ 2.0` 13-Stage "Clean, Intimate & Sexy" Vocal Studio + Automatic 12-Mode Hardware Matrix:**
   * Automatically detects whether you are using your **`48 kHz` Phone Studio Mic (with Bluetooth Neckband/AirPods kept in 100% Stereo Game Mode)**, a **Wired `3.5mm` / `USB-C` Close-Talk Headset/Lapel Mic**, or a **Bluetooth Collar/Earbud Mic**.
   * Combines **`50 / 65 / 85 Hz` Gender HPF**, **Stage 5 Zero-Phase `<135 Hz` Hard-Breath Plosive Guard**, **Stage 6 Tight Cardioid Proximity Contour (`95 / 130 / 175 Hz`)**, **Stage 7 Pure Analog Cubic Soft-Sine + Triode `2f` Sub-Warmth**, **Stage 8 Deeper `-3.5 dB` Boxy Mud Scoop (`315–365 Hz`) + `920 Hz` Sweet Velvet Anti-Nasal Dip**, and **Stage 9 Pure `2f` Upper-Harmonic "Aphex Silk Air" Exciter (`5.2–10.0 kHz` shimmer restoration)** so Male, Female, and Neutral voices sound like a luxury podcast studio even when a game locks the hardware mic to `16 kHz`!
4. **Live App vs. Game Resource Split (`🔥 Heat Split` & `💾 RAM Split`) + Dual-Layer Privacy Blur Shield:**
   * Tab 3 of the Floating Pill (and the post-stream Session Report) shows live **`🔥 Heat: App ~4% CPU | Game+OS ~64% SoC`** and **`💾 RAM: App 229MB | Game+OS 5.8GB`**, proving in real time that SolStream uses only `~4%` CPU and `~229 MB` RAM while your game and Android OS use the rest.

---

## 4. The 10 Custom `SolApex™ v1.3.0` Engineering Innovations (Found in No Other Android App)

| # | Custom `SolApex™ v1.3.0` Innovation | The Real-World Gaming Problem It Solves | How Our Custom Math & Silicon Engineering Works |
|---|---|---|---|
| **1** | **Pure `2f` Upper-Harmonic "Aphex Silk Air" Exciter (`5.2–10.0 kHz`) + Seamless `16 kHz` Match-Lock Continuity** | When CODM's in-game squad mic is active during a match, Android's audio HAL locks the physical built-in mic to `16,000 Hz` (rolling off natural highs above `7.5 kHz`), where a static EQ shelf has nothing left to boost—and periodic mic reopening causes 60-second audio hiccups. | `SolDualAudioEngine` keeps your `AudioRecord` session open with zero 60-second reopen loops, while Stage 9 of `MicDspProcessor` extracts your clean `2.4–3.6 kHz` vocal core and passes it through a normalized square-law frequency doubler ($\sin^2(\omega t) \rightarrow \cos(2\omega t)$) to synthesize brand-new `5.2–10.0 kHz` silky air shimmer! |
| **2** | **Zero-Phase-Peak 2-Stage `<135 Hz` Hard-Breath Plosive Guard + Pure Cubic Analog Sub-Warmth** | Blowing air out or sighing into a phone mic creates `<115 Hz` wind blasts before proximity EQ, while raw `+1/-1` square-wave sub-octave dividers leak odd-harmonic buzz into `180–350 Hz`. | Stage 5 uses a 2-stage 1st-order `<135 Hz` RC split vs. a `250 Hz` (`Q=1.15`) vocal-cord detector to duck non-vocal wind blasts down to `-10 dB` with zero biquad phase lag, while Stage 7 shapes the isolated fundamental (`92/128/170 Hz`) with continuous cubic soft-sine ($u - u^3/3$) + asymmetric triode `2f` warmth. |
| **3** | **Automatic 4-Hardware × 3-Gender (`12-Mode`) Acoustic Matrix + Deeper `-3.5 dB` Boxy Mud Scoop** | A phone mic held 14 inches away in two cupped hands resonates at `315–365 Hz` (boxy room mud) and `920–1,050 Hz` (plastic port glare), whereas a close-talk wired/USB-C mic 2 inches away booms if over-boosted. | Automatically detects `PHONE_STUDIO`, `WIRED_USB_CLOSE`, `BLUETOOTH_SCO`, or `SPEAKER_AEC` and applies 12 distinct 64-bit filter curves across `Male (50Hz)`, `Neutral (65Hz)`, and `Female (85Hz)`—carving out `-3.5/-3.2/-3.0 dB` at `315/340/365 Hz` (`Q=1.65`) and `-2.2/-2.0/-1.8 dB` at `920/980/1,050 Hz`. |
| **4** | **Hardware Flush-to-Zero (`FZ`) C Neural Denoiser + Low-End CPU Budget Auto-Bypass** | Subnormal floating-point decay inside recurrent GRU weights in `RNNoise` can cause 10–30x CPU spikes on ARM cores during mic mute, and heavy thermal throttling on 3–4 GB budget phones can starve audio threads. | `sol_rnnoise_jni.c` sets hardware `FPCR` (ARM64 bit 24), `FPSCR` (ARMv7), and `MXCSR` (`x86_64`) Flush-to-Zero bits plus a `-230 dBFS` (`1e-15f`) anti-denormal offset; `MicDspProcessor` monitors per-frame execution time and auto-bypasses middle biquad EQ stages if a throttled budget phone ever exceeds `4.5 ms` per `10 ms` frame. |
| **5** | **Slider-Independent `3D Game Soundstage` (`62 Hz` Sub-Punch + Single-Pass `250 Hz + 2.45 kHz` Footsteps)** | Measuring game blast envelopes after the `Game Mix %` slider caused `150–200%` Game Mix to disable `Sonar Footstep Lift`, while cascading two `2,450 Hz` filters caused phase smear. | Measures `rawGameBlastEnv` on un-scaled raw game audio before `effectiveGameGain` and combines base crunch (`+2.2 dB`) + dynamic `Sonar Lift` (`+1.6 dB`) into a single zero-center-phase `2,450 Hz` parallel band-pass paired with `250 Hz` boot thump and phase-safe `62 Hz` voice ducking. |
| **6** | **Pedro `RootEncoder` $\mu\text{s} \rightarrow \text{ms}$ RTT Bridge + `setCheckServerAlive(false)` + Lock-Free `PacketSlot` Pool** | Pedro's `RtmpClient.getRtt()` returns microseconds (`25,000 µs` for `25 ms`), which falsely triggered `>280 ms` ABR bitrate drops; `InetAddress.isReachable()` caused false disconnects when ISPs blocked ICMP; and ring-index reuse could overwrite queued simulcast frames. | `RtmpTelemetryBridge.readRttMs()` converts $\mu\text{s} \rightarrow \text{ms}$ via `convertRttMicrosToMs()`; sets `setCheckServerAlive(false)` so only real TCP/TLS socket health governs connection state; and uses a lock-free `ArrayBlockingQueue<PacketSlot>` pool in `AuxRtmpTarget` where leased buffers are never overwritten while queued. |
| **7** | **Dual-Layer Privacy Blur Shield (`Auto-Blur Keyboard` + 8 Screen Zones)** | Streamers have personal photos on their keyboard or need to type room passwords mid-stream without turning off their top-right Minimap Blur Shield. | Combines `WindowInsets.Type.ime()` + a `1px` non-focusable height-watcher overlay to trigger an independent `keyboardBlurActive` GPU scissor-blur over the bottom `54%` of the stream while keeping `liveMinimapShieldCorner` (`8` selectable zones) intact. |
| **8** | **Gated Neighbor Shader (`14.74M` Fewer Reads/Frame) + `Surface.setFrameRate()` + Vulkan `~229 MB` PSS Guard** | Unconditional neighbor texel reads waste GPU power when Sharpen/Shadow Boost are OFF; 120 Hz screen composition wastes capture bandwidth; and portrait `VirtualDisplay.resize()` on minimizing a game spikes Vulkan `gralloc` RAM by `+187 MB`. | Gates the 4 cardinal neighbor fetches strictly inside `if (uSharpen > 0.01 \|\| uShadowBoost > 0.5)`, paces `SurfaceFlinger` capture in hardware via `Surface.setFrameRate()`, and ignores false portrait resize callbacks on `16:9` landscape streams. |
| **9** | **Live App vs. Game+OS CPU & RAM Split Telemetry + Smart `60 FPS` / `30 FPS` Thermal Governor** | Gamers often blame the streaming app when a 120 FPS game heats the SoC to `43°C` or uses `5.8 GB` of system RAM. | Samples `Process.getElapsedCpuTime()` and `Debug.getPss()` every 2s to display live **`🔥 Heat: App ~4% CPU \| Game+OS ~64% SoC`** and **`💾 RAM: App 229MB \| Game+OS 5.8GB`** in Tab 3 of the Floating Pill and post-stream Session Reports. |
| **10** | **100% Google Play Store & Sideloaded APK Dual Readiness (`.aab` + `SYMBOL_TABLE` + Prominent Disclosure)** | Google Play requires `.aab` bundles, native debug symbol tables, R8 keep rules, a public Privacy Policy URL, and an in-app Prominent Disclosure popup before opening Accessibility Settings. | GitHub Actions automatically builds both `SolStream-Studio-v1.3.0.apk` and `SolStream-Studio-v1.3.0-PlayStore.aab` (with `ndk.debugSymbolLevel = "SYMBOL_TABLE"`), while Card 1 & Card 5 display Google Play's compliant Prominent Disclosure popup. |

---

## 5. Complete Plain-English User Manual: Every Card, Drawer & Floating Tab Explained

### Step A: One-Time Android 13–18 & Earphone Setup (2 Minutes)

1. **Enable Assistive Broadcast Dock (Google Play Store & Sideloaded APK):**
   * **If installed from Google Play Store:** Tap **`3. Tap Accessibility 1st`** in Card 1 → tap **Accept & Open Settings** on the disclosure popup → switch **SolStream Studio** **ON** in 1 tap!
   * **If sideloaded via GitHub APK on Android 13–18:**
     1. In Card 1, tap **`3. Tap Accessibility 1st`** → tap **Accept & Open Settings** → *Installed apps* → tap greyed-out **SolStream Studio** once and tap **OK**.
     2. Return to SolStream Studio and tap **`4. Unlock Restricted (⋮)`** → tap **`⋮`** at the top-right (or **scroll to the very bottom of the App Info screen on Xiaomi / Redmi / POCO HyperOS and Vivo / iQOO**) → tap **Allow restricted settings**.
     3. Tap **`3. Tap Accessibility 1st`** again and toggle **SolStream Studio** **ON**! Once all 4 checks are `READY`, Card 1 automatically collapses into a single clean summary bar.
2. **Earphones & External Mics (`Auto` 12-Mode Hardware Matrix + `2.4 / 5 GHz` Radio Check):**
   * **Bluetooth Neckbands & AirPods (`realme Buds Wireless 5 ANC`, etc.):** Keep `Mic Input` on **`Auto (Phone Studio Mic Locked)`**. Your Bluetooth earphones stay in `100%` Stereo Game Mode (`A2DP`) while our engine locks onto your phone's `48 kHz` studio mic (`PHONE_STUDIO` mode). Card 1 also checks if you are on **`5 GHz Wi-Fi`** (`0%` Bluetooth radio interference) vs. `2.4 GHz Wi-Fi`.
   * **Wired `3.5mm` Earphones & `USB-C` Mics:** Plugging in a wired or USB-C mic automatically switches `SolLuxe™ 2.0` to **`WIRED_USB_CLOSE`** mode (scaling proximity boost to `+2.0 dB`, cubic sub-warmth drive to `9%`, and adding `+5 Hz` plosive protection so close-talk mics never sound boomy).

---

### Step B: Pre-Stream Dashboard (Top-to-Bottom Guide)

#### Fixed Top Header Bar & 3-Tile Hero Summary Bar
- **Pinned Header:** `S•` Energy Signal Crest + `SOLSTREAM STUDIO • ULTRA [v1.3.0 • SolApex™]` + `📖 Guide, Privacy & Licenses`, `Copy Preset`, and `Import Preset` (with **Smart Cross-Phone Hardware Clamping** and legacy zoom snapping).
- **Live Idle Telemetry Banner:** Shows real-time pre-stream `Idle Temp °C (Cool/Warm)`, `SoC Load %`, `Free RAM GB`, and `⚡ AC / Bypass PD (Est.)` or `AC Fast Power` status.
- **3-Tile Hero Summary Bar:** Tap **`[ 🎥 1440p (2K) | RESOLUTION ]`**, **`[ ⚡ 60 FPS | FRAME RATE ]`**, or **`[ 📡 20 Mbps | BITRATE ]`** directly at the top anytime.

#### Card 0 — Smart Device Tier Presets (`[ SOLAPEX™ TIER ]` — `Lite` | `Balanced` | `High` | `Flagship` | `Ultra`)
- Tapping any tier loads the optimal verified resolution, FPS, bitrate, and full `SOL_LUXE` 13-stage voice chain for your phone, while automatically respecting your selected Platform in Card 2 (`YouTube` vs `Twitch 6M` vs `Kick 8M`), matching your Voice Gender HPF (`50/65/85 Hz`), and resetting advanced silicon controls (`B-Frames = 0`, `IDR = 2s`, `QP = Auto`, `CBR`).

#### Card 1 — Pre-Flight Readiness & Setup (`[ PRE-FLIGHT ]`)
- Auto-collapses once all 4 checks (`Mic`, `Notifications`, `Battery Unrestricted`, `Accessibility`) are `READY`, displays the live 5th Audio Route + Media Volume + `5GHz Wi-Fi + BT` coexistence line, and includes the **All-Brand Phone Setup Guide** (auto-detecting Samsung, Xiaomi/POCO, Vivo/iQOO Q1/Q2 frame interpolation tips, OnePlus/Realme, Motorola, or Pixel).

#### Card 2 — Stream Key Vault & Simulcast (`[ SOLRELAY OMEGA™ ]`)
- **4-Pill Platform Bar (`[ YouTube ] [ Twitch ] [ Kick ] [ Custom ]`):** 1 tap configures official ingest URLs and platform bitrate/codec compliance (`1080p60 H.264 6M 160k` for Twitch; `8M` for Kick; and automatically restores your active Device Tier's full `1440p/4K HEVC` resolution & bitrate when switching back to `YouTube`).
- **Non-Destructive Hardware Keystore Vault & Smart Paste Guard:** Shows `🔒 Key Saved (••••9x2a)` vs `⚠️ Empty`, auto-saves typed edits in `onPause()`, strips invisible spaces/newlines, and only regenerates the master AES-256 key on permanent OS key invalidation so saved keys are never lost on temporary OS glitches.
- **5-Metric Pro Network Analyzer:** Tests Cloudflare **Download Mbps**, **Sustained Upload Mbps**, **Ingest TCP Handshake RTT & Jitter**, **Loaded Router Bufferbloat**, and **Platform-Aware Safe Bitrate Recommendations**.

#### Card 3 — Video Engine (`[ SOLCORE ULTRA™ ]` — `360p–4K60` • `0.5–100M`)
- **Direct-Tap Segmented Pill Rows:** Tap `[ 360p ] [ 480p ] [ 720p ] [ 1080p ] [ 1440p ] [ 4K ]`, `[ HEVC ] [ H.264 ] [ AV1 ] • [ 30 FPS ] [ 60 FPS ]`, and `[ FIT ] [ STRETCH (16:9) ] [ SMART ] [ FILL ]` in 1 click.
- **Unified `SolApex™` `ON/OFF` Styling:** Every active feature button glows **Electric Cyan / Deep Royal Blue (`ON`)** while inactive buttons rest in **Midnight Slate (`OFF`)**.
- **Stackable Visual Filters, `.PNG` Corner Logos & Dual Privacy Blur:** `Cinematic Filmic`, `AMOLED Vivid Pop`, `AI Dark-Corner Boost`, `Color Temp`, `GPU Crisp Sharpen`, 5-position `.PNG` overlays, live GPU text banner, `Auto-Blur Keyboard`, and 8 HUD blur zones.

#### Card 4 — 13-Stage Voice Studio & 3D Game Audio (`[ SOLLUXE™ 2.0 ]`)
- **1-Tap Gender Profile (`Neutral 65Hz` | `Male Voice 50Hz` | `Female Voice 85Hz`):** Automatically sets all 13 internal stages, Hard-Breath Plosive Guard, Cubic Analog Sub-Warmth, `-3.5 dB` Boxy Mud Scoop, `920 Hz` Velvet Dip, and `2f` Aphex Silk Air Exciter across Card 0, Card 4, and the Floating Pill.
- **Glowing Cyan `SeekBar` Volume Sliders + `−10% / +10%` Buttons:** Smoothly adjust **Stage 13 Post-DSP Mic Boost (`50%–250%`)** and **Internal Stereo Game Mix (`0%–250%`)**.
- **Stackable Stereo Game Soundstage:** `Cinema 3D Wide`, `Theater Sub-Punch (62Hz Voice Duck)`, `Footstep Focus (250Hz+2.45kHz+Sonar)`, and `Blast Tamer (35ms Smooth)`.

#### Card 5 — Launch Broadcast, Clips & Reports (`[ LAUNCH DECK ]`)
- **`GO LIVE — START STREAM` ↔ `⏹ STOP LIVE STREAM`**, **`★ Saved Clips & AI Highlights`**, **`Session Reports & History`**, **`Benchmark Other App`**, **`Refresh Mics / HW Scan`**, **`Check GitHub Update`**, and **`❤️ Subscribe to @Sol_CODM`**.

---

### Step C: Redesigned In-Game 3-Tab Floating Pill

- **Short Closed Pill (Never Cuts Off on Screen):**
  * Displays **`● 05:36 | 17.6M | 60fps | Mic:ON | 38.4°C`** in a compact 1-line badge that sleeps at `38%` opacity with `0 Hz` idle redraws and `FLAG_SPLIT_TOUCH`.
- **Redesigned `SolApex™` Open Pill (Grouped Micro-Headers + Live Resource Split):**
  * **Top Quick Row:** `[ 🎙️ Mic:ON ] [ 🎮 Game:ON ] [ 🎬 LIVE ] [ ★ Clip ] [ ✕ ]`
  * **Tab 1 (`🎙️ Audio`):** Grouped into `SOLLUXE™ 2.0 VOICE & DENOISER`, `STUDIO POLISH & VOLUME MIX`, and `3D GAME SOUNDSTAGE & MIC ROUTE`.
  * **Tab 2 (`🎬 Video`):** Grouped into `SOLCORE™ FRAMING & UPSCALE`, `STACKABLE GPU COLOR GRADING`, and `OVERLAY & DUAL PRIVACY SHIELD`.
  * **Tab 3 (`📡 Bitrate/Net`):** Grouped into `SOLRELAY™ BITRATE & THERMAL COOLER` and `LIVE STREAM & APP vs. GAME SPLIT`—featuring live **`🔥 Heat: App ~4% CPU | Game+OS ~64% SoC (41.2°C)`** and **`💾 RAM: App 229MB | Game+OS 5.8GB (of 8GB)`** readouts!

---

## 6. Global Broadcast Inspirations (Windows, macOS & iOS -> Android)

| SolStream Studio Ultra `v1.3.0` Feature | Original Inspiration | How We Engineered It Safely for Unrooted Android |
|---|---|---|
| **13-Stage `SolLuxe™ 2.0` Vocal Chain (Hard-Breath Guard + Cubic Sub-Warmth + `2f` Aphex Silk Air)** | Shure SM7B, Aphex Aural Exciter, dbx 120A & FabFilter Pro-C2 / Pro-DS | Ducks `<135 Hz` non-vocal breath blasts before proximity boost, shapes `92–170 Hz` fundamentals with cubic soft-sine + triode `2f` warmth, scoops `315–365 Hz` room mud (`-3.5 dB`), dips `920 Hz` nasality, and synthesizes pure `2f` `5.2–10.0 kHz` air shimmer! |
| **Phase-Safe `62 Hz` Sub-Punch + Single-Pass `250 Hz + 2.45 kHz` Footsteps + `4x` Limiter** | Dolby Atmos Gaming & FabFilter Pro-L2 | Boosts `62 Hz` game sub-bass (`+3.0 dB`) when silent and ducks to `0.0 dB` neutral during speech, lifts `250 Hz` indoor thuds + single-pass zero-phase `2,450 Hz` crunch, and protects `250%` game mix through a 64-sample look-ahead `4x` Cubic True-Peak Limiter (`-1.16 dBTP`). |
| **`SolCore Ultra™` ES 3.0 `Fast-FP16` + Gated Neighbor Shader + `Surface.setFrameRate()`** | Desktop OBS Studio + AMD CAS + Android 11–18 SurfaceFlinger | Uses `mediump` FP16 registers, gates cardinal neighbor fetches (`14.74M` fewer reads/frame when Sharpen/Shadow Boost are OFF), paces capture in hardware via `Surface.setFrameRate()`, and guards Vulkan `gralloc` memory (`~229 MB` PSS). |
| **Fresh-Client `8.5s` Handshake Watchdog + $\mu\text{s} \rightarrow \text{ms}$ RTT Bridge + Instant `Wi-Fi ↔ 5G` Failover** | IRL Pro / Speedify Bonding Concepts + Cloudflare Speed | Converts Pedro `getRtt()` microseconds to true milliseconds, disables false ICMP disconnects (`setCheckServerAlive(false)`), uses a lock-free `PacketSlot` pool for simulcast, and switches `Wi-Fi ↔ 5G` in `0.3s`. |

---

## 7. For Android Developers & Engineers: Architecture, 13-Stage Audio & 6-Stage Video

### Validated Cloud Build Toolchain (`build.yml` + `app/build.gradle.kts`)
- **JDK, Gradle & NDK:** Temurin `21` (`JavaVersion.VERSION_17` target) + Gradle `8.14` + Android Gradle Plugin (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`, `versionCode = 10`, `versionName = "1.3.0"`) + Android NDK r28+ (`CMake 3.22.1`, `-DANDROID_STL=none`, `-O3 -std=gnu11 -Wno-null-dereference`, `ndk.debugSymbolLevel = "SYMBOL_TABLE"`).
- **16 KB Page Alignment (All `*.so` Libraries), Fresh 30-Test JUnit Suite & Dual APK/AAB Output:** Enforces `-Wl,-z,max-page-size=16384` in `CMakeLists.txt` and `packaging.jniLibs.useLegacyPackaging = false`, verified on every CI run via `readelf -lW` across all `arm64-v8a` and `x86_64` `.so` libraries alongside `apksigner` V3 verification, pinned SHA-256 Xiph `RNNoise` v0.1.1 verification, strict Android Lint (`abortOnError = true`), fresh un-cached execution (`--rerun-tasks`) of all **30 JUnit 4 unit tests** (`SolStreamUnitTests.kt`), and automatic upload of both `SolStream-Studio-v1.3.0.apk` and `SolStream-Studio-v1.3.0-PlayStore.aab`.

### The 13-Stage `SolLuxe™ 2.0` Vocal Architecture (`MicDspProcessor.kt`)

| Stage | Module Name | Technical Specification |
|---|---|---|
| **Stage 0** | **Hardware AGC/AEC/NS Sanitizer & Seamless Match-Lock Continuity** | Forces OEM `AutomaticGainControl`, `NoiseSuppressor`, and `AcousticEchoCanceler` OFF on earphones; maintains continuous `AudioRecord` capture without 60-second reopen loops when games lock the hardware HAL to `16 kHz`. |
| **Stage 1** | **Gender-Matched 64-Bit Butterworth HPF** | **`50 Hz` (`MALE`)**, **`65 Hz` (`NEUTRAL`)**, **`85 Hz` (`FEMALE`)** on Phone Studio Mic (`+5 Hz` pop shield on Wired/USB-C; `65/75/95 Hz` collar shield on Bluetooth; `70/80/95 Hz` on Speakerphone). |
| **Stage 2** | **Independent `250 Hz` Vocal-Cord Tracker + Spark/Grip Blanker** | Unconditionally tracks `250 Hz` (`Q=1.15`) vocal-cord harmonic energy (even when `transientTamerEnabled == false`) and clamps multi-strike ultrasonic sparks (`216-sample` / `4.5 ms` hold) without dulling `"P/T/K"` consonants. |
| **Stage 3** | **Phase-Aligned Native C `48 kHz RNNoise` (`1.0x` Unity + `FZ` Guard)** | Stack-allocated `GetFloatArrayRegion` JNI transfer (`0` heap pinning) with hardware ARM64/ARMv7/x86_64 Flush-to-Zero (`FZ`) register guards, `-230 dBFS` anti-denormal offset, and `480-sample` (`10 ms`) phase-aligned dry delay line (`65% / 88% / 98%` wet). |
| **Stage 4** | **`0-ms` Instant Vocal Gate & `2.2:1` Breath Expander** | Opens on the very first speech frame (`0 ms` attack), holds `180 ms`, and expands idle room air to `0.28x` (`0.12x` under speaker bleed). |
| **Stage 5** | **Zero-Phase-Peak 2-Stage `<135 Hz` Hard-Breath Plosive Guard** | Placed **before** Stage 6 Proximity Boost; compares `<135 Hz` 1st-order air turbulence (`breathLowEnv`) against `250 Hz` vocal-cord vibration (`vocalBodyEnv`) and attenuates non-vocal wind blasts down to `-10 dB` with zero biquad phase lag. |
| **Stage 6** | **Tight Cardioid Proximity Chest Contour (Band 1)** | **`95 Hz @ +4.4 dB, Q=0.82` (`MALE`)**, **`130 Hz @ +3.5 dB` (`NEUTRAL`)**, **`175 Hz @ +2.8 dB` (`FEMALE`)** (scaled to `+2.0/+1.5/+1.2 dB` on close-talk Wired/USB-C mics). |
| **Stage 7** | **Pure Analog Cubic Soft-Sine + Asymmetric Triode `2f` Sub-Warmth** | Extracts `92/128/170 Hz` vocal fundamental and shapes it via continuous cubic soft-saturation ($y = u - u^3/3$) smoothed through a `115/145/185 Hz` Butterworth LPF + DC-blocked asymmetric triode `2f` warmth (zero square-wave buzz). |
| **Stage 8** | **Deeper `Q=1.65` Mud Scoop + `920 Hz` Velvet Dip + Presence** | Band 2 scoops room/hand-grip boxiness at **`315/340/365 Hz` (`-3.5/-3.2/-3.0 dB, Q=1.65`)**; Band 3 dips plastic port nasality at **`920/980/1,050 Hz` (`-2.2/-2.0/-1.8 dB`)**; Band 4 adds **`2.85/3.15/3.40 kHz` (`+1.4/+1.5/+1.6 dB`)** articulation. |
| **Stage 9** | **Pure `2f` "Aphex Silk Air" Exciter + Intimate Silk Air Shelf** | Extracts `2.65/2.85/3.10 kHz` vocal harmonics and applies envelope-normalized square-law frequency doubling (`vMid * vMid`) + `5.0 kHz` HPF to synthesize `5.2–10.0 kHz` air shimmer before the **`6.5/6.7/7.0 kHz` (`+2.5/+2.6 dB`)** Silk Air shelf. |
| **Stage 10** | **AI Dynamic Vocal Tone Balancer** | Gently reinforces warm chest (`+0%..+9%`) on quiet speech (`RMS < 0.050`) and dynamically trims `2.95–3.45 kHz` bite (`-0%..-30%`) on loud gunfight shouts (`RMS > 0.105`). |
| **Stage 11** | **High-Pass Sidechain 3-Zone Dynamics** | Weights detector with `abs(s - 0.55f * subChestBand)` so deep bass never pumps the `12ms`-punch `3:1` compressor, paired with Whisper Lift (`1.0x..1.48x`) and Speech-Gated Slow AGC (`0.84x..1.38x`). |
| **Stage 12** | **Post-Compressor Split-Band De-Esser** | Placed **after** Aphex Silk Air & Compression (`5.8/6.25/6.7 kHz`, `4.8–5.2 kHz` on BT) to tame `"S/Sh"` sibilance (`keepRatio 0.35..1.0`) without dulling vocal air. |
| **Stage 13** | **True Post-DSP Mic Boost (`50%–250%`), Safety Ceiling & Eco Watchdog** | Placed **after** Slow AGC & Compressor so manual volume boosts take `100%` effect, protected by a `0.92` soft knee ceiling, `4x` Catmull-Rom Cubic True-Peak Limiter (`-1.16 dBTP`), and a `4.5 ms` Low-End CPU Auto-Bypass watchdog. |

### Source Tree

~~~text
.
├── PRIVACY_POLICY.md                        # Public Google Play Console Privacy Policy & Data Safety Disclosure
├── README.md                                # Complete v1.3.0 User Manual & Engineering Reference
└── app/
    ├── build.gradle.kts                     # v1.3.0 (versionCode 10) + SYMBOL_TABLE + 16 KB JNI packaging
    ├── proguard-rules.pro                   # Explicit R8 @Keep rules for JNI, RNNoise, RootEncoder & Services
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          # Minimal unrooted permissions + foldable/tablet configChanges
        │   ├── cpp/
        │   │   ├── CMakeLists.txt           # 16 KB page-aligned C build for Xiph RNNoise v0.1.1 + Bark-MMSE fallback
        │   │   └── sol_rnnoise_jni.c        # Zero-allocation stack JNI bridge + ARM/x86 FZ register guards + dry delay
        │   ├── res/
        │   │   ├── drawable/ic_solstream_logo.xml # Safe-Zone 'SolStream: Energy Signal Core' Adaptive Icon
        │   │   ├── drawable/ic_stat_stream.xml    # Notification status icon
        │   │   ├── values/strings.xml & styles.xml # Play Store-compliant Accessibility disclosure & Dark Mode
        │   │   └── xml/accessibility_service_config.xml # canRetrieveWindowContent="false" & canPerformGestures="false"
        │   └── java/com/solgaming/solstreamstudio/
        │       ├── MainActivity.kt          # SolApex™ UI, Signal Crest Header, Prominent Disclosure, 640dp Responsive Layout
        │       ├── SolStreamService.kt      # Redesigned 3-Tab Pill, Heat/RAM Split, µs->ms RTT Bridge, Lock-Free Aux Pool, onTimeout
        │       ├── SolVideoPipeline.kt      # OpenGL ES 3.0 Fast-FP16, Gated Fetches, Bare-Metal Attempt #3 Fallback, Dual Blur
        │       ├── SolSceneAssetStore.kt    # Atomic staging-file Starting Soon, BRB, .PNG Overlay & auto-fit GPU Text Banner
        │       ├── SolDualAudioEngine.kt    # 12-Mode Detector, Zero Reopen Loop, Universal AAC Fallback, Slider-Independent 3D Game Bus
        │       ├── MicDspProcessor.kt       # SolLuxe™ 2.0 13-Stage Vocal Engine (Hard-Breath Guard, Cubic Sub, Aphex Silk Air, Eco Guard)
        │       ├── SolNativeRnnoise.kt      # Safe Kotlin JNI wrapper for libsol_rnnoise.so
        │       ├── CodecCapabilityProbe.kt  # 5000ms Startup Scan Gate, Global SoC/GPU Mapper, 0..45mA Bypass PD Detector
        │       ├── KeystoreVault.kt         # Non-Destructive AES-256-GCM Vault, Legacy Zoom Snapper, Cross-Phone Clamping
        │       └── DiagnosticsLogger.kt     # Privacy-redacted v1.3.0 Session Reports, Resource Split Log & ★ Saved Clips
        └── test/java/com/solgaming/solstreamstudio/
            └── SolStreamUnitTests.kt        # 30-test JUnit 4 verification suite
~~~

### Build & Release Verification Command

~~~bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --rerun-tasks -Pandroid.suppressUnsupportedCompileSdk=36,37,37.0,38,38.0 --no-daemon --stacktrace
~~~

---

## 8. For AI Coding Assistants, Security Auditors & Forkers

### Strict Architectural Invariants for AI Assistants / PR Contributors
1. **NEVER** promote `SolStreamService` to `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before `Activity.RESULT_OK` from `MediaProjectionManager.createScreenCaptureIntent()`, **ALWAYS** call `startForeground()` before any early return in `startLiveBroadcast()`, **ALWAYS** await `activeTeardownLatch` after `startForeground()`, and **ALWAYS** call `DiagnosticsLogger.lockFinalMicStatusBeforeStop()` + `videoPipeline.prepareForImmediateStop()` synchronously before async teardown.
2. **NEVER** open test `MediaCodec` encoder instances inside `CodecCapabilityProbe.scanDevice()` while `SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive`, and **NEVER** poll `PowerManager.getThermalHeadroom()` faster than once every `10.5 seconds`.
3. **NEVER** set `pendingMicRouteRefresh = true` on `16,000 Hz` HAL rate changes during in-game voice chat (let Stage 9's `Aphex Silk Air` Exciter restore upper harmonics without interrupting `AudioRecord`), and **NEVER** pass Pedro's `RtmpClient.getRtt()` value into ABR without dividing by `1000L` (`convertRttMicrosToMs`).
4. **NEVER** resize `VirtualDisplay` to portrait dimensions (`width < height`) during a `16:9` landscape stream when the game is minimized to the home screen, and **NEVER** allocate heap objects inside `MicDspProcessor.processMonoFrameInPlace`, `SolNativeRnnoise.processMono480InPlace`, or per-frame audio/OpenGL loops.
5. **NEVER** introduce `CAMERA`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `ACCESS_FINE_LOCATION`, `SYSTEM_ALERT_WINDOW`, `AudioManager.setCommunicationDevice()`, `-ffast-math` in `CMakeLists.txt` (blocked by Xiph `arch.h`), or CPU software video encoders (`c2.android.av1.encoder`).

---

## 9. Privacy Policy, Google Play Compliance & Open-Source Licenses

- **Privacy Policy & Zero Data Collection:** See [**`PRIVACY_POLICY.md`**](./PRIVACY_POLICY.md) for our full Google Play Store Privacy Policy and `AccessibilityService` disclosure (`android:canRetrieveWindowContent="false"`, `android:canPerformGestures="false"`, zero personal data collection, zero analytics/ad SDKs).
- **Created & Maintained By:** **Sol Studio (`@Sol_CODM`)** — [YouTube Channel](https://youtube.com/@Sol_CODM)
- **Dual Licensed:**
  * **Free for Gamers, Streamers & Personal/Academic Use:** Licensed under the **[PolyForm Noncommercial License 1.0.0](./LICENSE)**.
  * **Commercial Redistribution & App-Store Monetization:** Selling, bundling ads/subscriptions into, or publishing commercial copies of SolStream Studio on app stores requires a written commercial revenue-share license under **[COMMERCIAL-LICENSE.md](./COMMERCIAL-LICENSE.md)**.
- **Open-Source Credits:** Encrypted RTMPS socket packet transport uses `RtmpClient` from [`pedroSG94/RootEncoder`](https://github.com/pedroSG94/RootEncoder) (`Apache-2.0`, zero root access required) and neural voice denoising uses [`xiph/rnnoise`](https://github.com/pedroSG94/RootEncoder) (`BSD-3-Clause`).
