# Privacy Policy for SolStream Studio

**Effective Date:** November 17, 2026  
**Last Updated:** November 24, 2026  
**Application Name:** SolStream Studio (`com.solgaming.solstreamstudio`)  
**Developer / Studio:** Sol Studio (Pranshu Veer Singh / @Sol_CODM)  
**Primary Support Email:** pranshuveersingh@gmail.com  
**Creator & Channel Contact:** pranshu123451234@gmail.com  
**Official Repository & Issue Tracker:** https://github.com/PppP01/SolStream-Studio  

---

## 1. Overview & Zero Developer Data Collection Architecture
**SolStream Studio** ("the App") is an ultra-lightweight, hardware-accelerated live-streaming and screen-broadcasting studio for Android developed by **Sol Studio**.

We built SolStream Studio on a strict **Zero Developer Data Collection** architecture:
- **No Personal Data Collected by Sol Studio:** SolStream Studio does **not** collect, store, log, sell, rent, or transmit any personal information, user identities, contact lists, location data, photos, microphone recordings, screenshots, or screen broadcasts to developer servers or third-party data brokers.
- **No Developer Servers or Cloud Accounts:** The App does not require user registration, login credentials, or cloud accounts. Sol Studio operates zero backend collection servers.
- **Zero Advertising & Zero Analytics SDKs:** The App is 100% free of third-party advertising networks, behavioral trackers, and analytics SDKs.
- **Voluntary User-Initiated External Connections:** The App only contacts external networks when you explicitly start a live stream to your own chosen RTMP/RTMPS platform or manually tap optional tools (such as the Cloudflare Speed Test or GitHub Release Update Check described in Section 6).

---

## 2. How Permissions, Audio, Video, Overlays & Stream Keys Are Processed

### A. Screen Capture & Audio Recording (`RECORD_AUDIO`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `FOREGROUND_SERVICE_MICROPHONE`)
- **100% Real-Time On-Device Processing:** Screen video (`MediaProjection`) and microphone/internal game audio (`AudioRecord` / `AudioPlaybackCapture`) are captured and processed in real time inside your device's memory (RAM) **only** when you explicitly tap **"GO LIVE — START STREAM"** or run the optional **"10s Studio Voice Test"**.
- **Per-Game Internal Audio Policy:** Internal game audio capture uses Android's `AudioPlaybackCapture` API for eligible `GAME`, `MEDIA`, and `UNKNOWN` audio usages only when permitted by the individual game's Android capture policy (`USAGE_VOICE_COMMUNICATION` streams are excluded by Android). If a game restricts internal capture, the App falls back cleanly to microphone audio.
- **Direct User-Configured Transmission (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`):** During a live broadcast, encoded video (HEVC / H.264 / AV1) and audio (AAC-LC) packets are streamed directly from your phone to the RTMP/RTMPS ingest server URL that **you** explicitly configure (such as YouTube Live, Twitch, Kick, or a custom RTMP server). Your broadcast streams never pass through any server owned or operated by Sol Studio.
- **Local 10-Second Voice Calibration File:** When you run the optional 10-second microphone calibration test, a temporary PCM audio sample (`sol_mic_test_48k.pcm`) is written strictly to your device's private app cache directory (`context.cacheDir`) so you can listen to your microphone settings. It is never uploaded anywhere and can be deleted in one tap at any time.

### B. Stream Key Security, Studio Profiles, Backups & Hardware Encryption (`AndroidKeyStore`)
- Your RTMP/RTMPS stream keys (Primary, YouTube Shorts Slot #2, Twitch, and Kick/Custom Simulcast) are encrypted locally on your device using the **Android Hardware Keystore (`AES-256-GCM` backed by StrongBox or Trusted Execution Environment)** before being stored in private device preferences.
- **Backup & Transfer Exclusions:** SolStream Studio explicitly sets `android:allowBackup="false"` and includes an explicit Android `data_extraction_rules.xml` configuration excluding private preferences, encrypted keys, cached images, and diagnostic logs from cloud backups and device-to-device transfers.
- Stream keys are **never** included in the 4 saved **Studio Profiles** or when you use the *"Copy Preset"* feature, and any stream key or token appearing in a network error string is automatically scrubbed and replaced with `[REDACTED_KEY]` in local diagnostic reports.

### C. Custom Scene Graphics, PNGTuber Avatars, Offline QR & Clean Screenshots
- **Custom Images (Zero Broad Storage Permissions & Zero Camera Permission):** When you select a custom *Starting Soon*, *BRB*, *9:16 Poster*, *.PNG Corner Logo*, or *2-PNG PNGTuber Avatar (Idle / Talking)* image using Android's system photo picker (`ACTION_GET_CONTENT`), the selected image is resized and cached locally inside your device's private internal app storage (`context.filesDir`). SolStream Studio never requests `CAMERA`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_IMAGES`.
- **100% Offline Corner QR Code Generation:** Any optional text or link (such as a UPI link, YouTube channel URL, or Discord invite) entered for the Corner QR overlay is converted into a QR image completely offline on your device in local code. It is never sent to any external QR API or developer server.
- **1-Tap Clean Thumbnails (`MediaStore`):** Only when you manually tap **"📸 Shot"** during a live broadcast does SolStream capture a single clean OpenGL frame and save it locally as a JPEG file in your device's `Pictures/SolStream/` folder via Android's scoped `MediaStore` API (requiring zero broad storage permissions). Screenshots remain strictly on your device and are never uploaded by SolStream Studio.

---

## 3. Prominent Disclosure: Android AccessibilityService API

SolStream Studio **optionally** uses the Android `AccessibilityService` API (`com.solgaming.solstreamstudio.SolStreamService`) solely to display an **assistive floating in-game broadcast control dock (`TYPE_ACCESSIBILITY_OVERLAY`)** over full-screen games. This compact, draggable pill allows users to mute/unmute their microphone, toggle the keyboard/minimap privacy blur shield, switch scenes, toggle HUD/QR/Avatar overlays, capture clean thumbnails, or mark highlight clip timestamps without leaving their game.

**Separate Audio Permission Note:** Microphone capture is performed separately via Android's standard `RECORD_AUDIO` permission (`AudioRecord`), and internal game audio uses Android's `MediaProjection` playback capture when permitted by the game. On supported Android versions, keeping the optional floating dock active also allows Android's concurrent audio-input rules to operate while broadcasting over games.

### Strict Accessibility Privacy & Security Guarantees:
- **Not a Disability Accessibility Tool (`android:isAccessibilityTool="false"`):** The App accurately declares `isAccessibilityTool="false"` in its Accessibility XML configuration.
- **Zero Event Subscriptions & Cannot Read Screen Content (`android:canRetrieveWindowContent="false"`):** SolStream Studio is strictly configured in its XML manifest with `canRetrieveWindowContent="false"` and subscribes to zero accessibility event types. The App **cannot** inspect, read, scrape, or capture UI hierarchies, screen text, notifications, passwords, banking details, or private messages from any app.
- **Cannot Perform Automated Gestures (`android:canPerformGestures="false"`):** The App **cannot** simulate taps, swipes, clicks, or automated gameplay actions.
- **Zero Data Transmission:** No information from the `AccessibilityService` is ever logged, stored, or transmitted off your device.
- **100% Optional & User-Controlled:** Before navigating to Android Accessibility Settings, SolStream Studio displays a prominent in-app disclosure dialog allowing you to **Accept** or **Decline**. You may decline or turn off the Accessibility Service at any time in Android Settings and continue broadcasting without the floating dock.

---

## 4. Local Session Reports, 60-Second Checkpoints & Diagnostic Logs
- SolStream Studio generates a local, plain-English **Session Report** (containing frame rate, bitrate, battery temperature, Android thermal status/headroom, process CPU/RAM telemetry, active overlay labels, and saved highlight timestamps) and periodically saves a single local 60-second recovery checkpoint strictly inside private on-device preferences.
- Diagnostic reports and checkpoints are **never** sent automatically to Sol Studio. They are only copied to your clipboard if you manually tap **"Copy Report"**.

---

## 5. Data Retention & User Deletion Controls
Because Sol Studio collects **zero** user data on any external server, all settings, keys, graphics, and diagnostic history exist strictly on your own device and remain under your full control until you delete them:
- **Stream Keys:** Delete immediately inside Card 2 via **"Clear Key"**.
- **Custom Scenes, Logos & PNGTuber Avatars:** Delete immediately inside Card 3 via **"Clear Saved Graphics"**.
- **10-Second Voice Calibration Sample:** Delete immediately inside Card 4 via **"Delete"**.
- **Saved Clip Markers & Session Report History:** Delete immediately inside Card 5 via **"Clear Clips"** and **"Delete History"** (or **"Clear 60s"** for a recovered checkpoint).
- **Saved Thumbnails (`Pictures/SolStream/`):** Delete anytime from your phone's Gallery or Files app.
- **Complete Erasure:** Clearing App Storage in Android Settings or uninstalling SolStream Studio permanently removes all locally stored settings, encrypted keys, cached images, and logs. If you have any privacy or deletion inquiries, contact us using the emails below.

---

## 6. Third-Party Services & Open-Source Software
- **Streaming Platforms:** When you stream to YouTube Live, Twitch, Kick, or another RTMP/RTMPS provider, your broadcast stream is governed by the privacy policy and terms of service of that respective platform.
- **Optional User-Initiated Network Tools (Ordinary Request Metadata Notice):**
  - **Internet Speed & Bufferbloat Test:** Only when you manually tap *"Test Internet"* does the App exchange synthetic test bytes with **Cloudflare Speed** (`speed.cloudflare.com`) to measure download/upload speed and router bufferbloat. No personal account data or stream keys are sent, though Cloudflare naturally receives ordinary network request metadata (such as your IP address and standard HTTP headers) governed by Cloudflare's privacy policy.
  - **Release Update Check:** Only when you manually tap *"Check GitHub Update"* does the App query the public **GitHub Releases API** (`api.github.com`) to compare your installed version against the latest public release. GitHub naturally receives ordinary HTTPS request metadata (such as your IP address and app User-Agent header) governed by GitHub's privacy statement.
- **Open-Source Libraries:** SolStream Studio incorporates the following open-source components, which run locally on your device and collect zero user data:
  - **RootEncoder** (`com.github.pedroSG94.RootEncoder`) — Copyright (c) Pedro Sánchez (`pedroSG94`), licensed under the Apache License, Version 2.0.
  - **Xiph RNNoise** (`xiph/rnnoise` v0.1.1) — Copyright (c) Xiph.Org Foundation and Jean-Marc Valin, licensed under the BSD 3-Clause License.
  - **AndroidX (Core, AppCompat, ExifInterface), Material Components & Kotlin Coroutines** — Apache License, Version 2.0.

---

## 7. Target Audience & Children's Privacy
SolStream Studio is intended for general audiences aged **13 and older** (live-streaming creators and gamers). The App is not directed at children under the age of 13, and we do not knowingly collect any personal information from children under 13 (or under the applicable age of digital consent in your jurisdiction).

---

## 8. Changes to This Privacy Policy & Contact Us
We may update this Privacy Policy from time to time to reflect new app features or regulatory requirements. Any updates will be published at this URL with an updated "Last Updated" date.

If you have any questions or concerns regarding this Privacy Policy or SolStream Studio's privacy practices, please contact us:
- **Developer / Studio:** Sol Studio (Pranshu Veer Singh / @Sol_CODM)
- **Support Email:** pranshuveersingh@gmail.com / pranshu123451234@gmail.com
- **GitHub Repository:** https://github.com/PppP01/SolStream-Studio
