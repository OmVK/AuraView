# 🔮 AuraView 
> **Next-Gen AI Floating Assistant, Study Copilot & Power-User Suite for Android**

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-blue.svg)](https://developer.android.com/jetpack/compose)
[![AI](https://img.shields.io/badge/AI-Gemini%201.5%20%7C%20Groq%20Whisper-orange.svg)](https://aistudio.google.com)
[![Release](https://img.shields.io/github/v/release/OmVK/Arora-x?include_prereleases&label=Latest%20Release)](https://github.com/OmVK/Arora-x/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

AuraView is a state-of-the-art Android floating overlay assistant inspired by **fooView (FV 悬浮球)**, enhanced with **Multimodal AI Vision (Google Gemini 1.5)**, **Real-Time Live Speech Transcription (Groq Whisper Large-v3 LPU)**, **Brave-Shielded Ad-Free Floating YouTube Player with Background Audio Playback**, and **100% On-Device Neural Translation & OCR**.

---

## ✨ Features & Capabilities

### 🎥 1. YouTube Mini (Background Audio Engine)
* **Brave browser**: Multi-tier network request filtering and 0ms instant.
* **Seamless Background Audio Playback**: Lock visibility and keep music/podcasts streaming continuously even when minimized into a small floating bubble.
* **Minimized Pill Mode**: Shrinks down to an interactive floating bubble with instant single-tap maximize and drag anywhere on screen.
* **Cosmetic Shield**: Hides banner ads, sponsored cards, mastheads, and bottom navigation clutter.
* **Slim Navigation Capsule**: Built-in Back, Forward, Home, and Reload controls with real-time "0 Ads" shield status badge.

### 🔍 2. Circle to Search & LaTeX Math Solver
* **Freeform Gesture Canvas**: Loop, circle, or lasso any equation, physics diagram, code snippet, or UI element on screen.
* **Step-by-Step AI Proofs**: Formatted LaTeX equations, conceptual explanations, and bug fixes.
* **Embedded Google Search**: Live web results right inside the floating sheet without switching apps.
* **On-Device OCR & Multilingual Translation**: Instant translation across 8 languages (Hindi, Arabic, Korean, Spanish, French, German, Japanese, English).

### 🎙️ 3. WhisperFlow Live Transcriber
* **Dual-Mode Streaming**:
  * **Mode A (📴 Offline Neural)**: Zero internet needed, 100% private on-device speech-to-text.
  * **Mode B (⚡ Groq Whisper LPU)**: Ultra-fast cloud transcription powered by Whisper Large-v3.
* **Smart Punctuation & Formatting**: Automatic sentence punctuation, numbering, and paragraphing.
* **Built-in Key Tester & 1-Click Free Key Link**: Test your API key directly inside the overlay.

### 🗂️ 4. PIN-Protected Wi-Fi Dropzone
* **Cableless PC ↔ Phone File Sharing**: High-speed local network transfer.
* **Full Storage Hierarchy**: Browse `/storage/emulated/0`, drill into folders, download, delete, and upload.
* **Dynamic 6-Digit PIN**: Authenticates clients with dynamic PIN verification and path traversal sandboxing.

### 🪟 5. Floating Mini-App Suite (Jetpack Compose Overlays)
* **📺 YouTube Mini**: Floating ad-free web player with background audio keep-alive and minimize bubble.
* **🔊 Global Volume Booster**: Hardware audio amplifier boosting speaker gain up to **+200% (+20 dB)**.
* **📊 Speedometer & Latency Pill**: Floating live network speed counter and ping monitor.
* **🛡️ Privacy Shield Overlay**: Adjustable 0–100% black frosted window filter to prevent shoulder-surfing.
* **📈 2D Cartesian Graph Plotter**: Interactive function graphing engine (trig, polynomials, zoom & pan).
* **🌐 Floating Browser**: Minimalist floating web browser with desktop mode toggle.
* **📜 Floating Teleprompter**: Variable auto-scrolling speed with script mirroring.
* **📋 Smart Clipboard Stack**: Auto-categorized history (URL, Code, Math, Text) with sensitive data filtering.
* **📁 Floating File Explorer**: Full storage explorer with MIME opening via FileProvider.
* **📝 Notes & Anki Exporter**: Markdown note editor with TTS speech and 1-tap Anki flashcard TSV export.

### 🛡️ 6. Multi-Layer Restriction Bypass Engine
* **Layer 1: Android AccessibilityService**: Intercepts system gestures (Back, Home, Recents) and extracts text trees.
* **Layer 2: MediaProjection API**: Hardware-accelerated screen capture.
* **Layer 3: Shizuku (Wireless ADB)**: Privileged shell capture bypassing `FLAG_SECURE` app restrictions without root.
* **Layer 4: Root Fallback**: Kernel-level fallback for rooted systems.

---

## 🔒 Security & Privacy Architecture

* **Zero Hardcoded Secrets**: All API keys are user-supplied at runtime and stored locally in app-private storage.
* **Deterministic AI Safety Gate**: Hardcoded `SecurityPolicyEngine` prevents autonomous AI actions on sensitive apps (banking, settings, authenticators).
* **Sensitive Clipboard Discard**: Android 13+ `EXTRA_IS_SENSITIVE` detection drops copied passwords from password managers automatically.
* **Sandboxed Storage**: FileProvider paths are scoped to explicit subdirectories, and unencrypted backups are disabled (`android:allowBackup="false"`).

---

## 🏗️ Tech Stack

* **Language**: Kotlin 2.0.20
* **UI & Animations**: Jetpack Compose (Material 3), 120Hz Spring Physics
* **Networking**: OkHttp 4.12.0, Gson 2.11.0
* **On-Device ML**: Google ML Kit (Vision Text Recognition, Translation, Language ID)
* **Cloud AI**: Google Gemini 1.5 Flash Vision REST API, Groq Whisper Large-v3 LPU
* **Privilege Engine**: Rikka Shizuku API v13+
* **Persistence**: Jetpack DataStore Preferences

---

## 🚀 Getting Started & Building

1. Clone the repository:
   ```bash
   git clone https://github.com/OmVK/Arora-x.git
   cd Arora-x
   ```
2. Open the project in **Android Studio (Ladybug / Iguana or newer)**.
3. Build & Run on an Android device running **Android 8.0 (API 26) or higher** (Android 14/15 fully supported):
   ```bash
   ./gradlew assembleRelease
   ```
4. In the app:
   * Grant **Floating Overlay Permission**.
   * Enable **Accessibility Service** (for gesture navigation & screen OCR).
   * Enter your free Gemini API key ([Google AI Studio](https://aistudio.google.com/app/apikey)) or Groq key ([Groq Console](https://console.groq.com/keys)).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
