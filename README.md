# PelotonSolveIt

An Android app that turns your Peloton Tread into a hands-free AI workstation. Run [SolveIt](https://solveit.fast.ai) while training — speak prompts via voice, get AI responses live in the app, and track your workout stats in real time. No Google services required.

> **⚠️ Disclaimer:** This is a personal project. It uses a library reverse-engineered from Peloton's system software. Use at your own risk. Not affiliated with or endorsed by Peloton. Intended for personal use only.

---

## What It Does

- 📱 **Full-screen SolveIt WebView** — browse and interact with any SolveIt dialog while running
- 🎤 **Hands-free voice input** — tap mic, speak, transcript is sent to SolveIt as a prompt
- 🤖 **Live AI responses** — SolveIt replies stream into the WebView automatically
- 📊 **Live treadmill stats** — speed, incline, pace, distance, and elapsed time from Peloton hardware sensors
- 🏃 **Workout tracking** — start/pause/resume/stop with distance and timer
- 🌙 **Dark mode** — injected into SolveIt WebView automatically on load
- 🔀 **Dual STT engines** — toggle between on-device Vosk (offline) and OpenAI Whisper (cloud, better noise handling)
- 📌 **Auto-greeting** — sends a pinned context message to SolveIt when you open a new dialog

---

## Architecture

Four components work together:

**WebView**
Renders SolveIt's full web UI. A JS bridge (`SolveItJSBridge`) polls the active dialog name and selected message ID every 500ms via `evaluateJavascript`, so voice transcripts land in the right place. CSS is injected on page load to fix Android WebView layout.

**Voice → SolveIt**
```
Mic button → AudioRecord (16kHz PCM) → STT engine → OkHttp POST → SolveIt API → WebView
```
Two interchangeable engines via `SpeechEngine` interface:
- **Vosk** — offline, on-device (Kaldi-based). No internet needed.
- **Whisper** — cloud (OpenAI). On-device Silero VAD (ONNX) detects end of speech before sending audio.

**Live Treadmill Sensors**
Peloton's own hardware sensor data accessed via a reverse-engineered system library (`peloton-sensor.jar`, extracted from `com.onepeloton.systempluginui`). Provides `getCurrentSpeed()`, `getCurrentIncline()`, and more — no active Peloton workout session required.

**SolveIt API**
`solveItPost()` sends authenticated form POSTs to your SolveIt instance. Used to add prompt messages, run them, pin the greeting, and check for existing messages.

---

## Project Structure

```
app/
├── libs/
│   ├── android-vad-silero-v2.0.10-release.aar   # Silero VAD (downloaded manually)
│   └── peloton-sensor.jar                        # Peloton sensor library (extracted manually)
└── src/main/
    ├── assets/
    │   └── vosk-model-small-en-us-0.15/          # Vosk model (downloaded manually, gitignored)
    └── java/com/stewart/pelotonsolveit/
        ├── MainActivity.kt                        # App entry point, Compose root
        ├── PelotonTreadObserver.kt                # Sensor callbacks, workout state machine
        ├── SolveItJSBridge.kt                     # JS ↔ Kotlin bridge for dialog/message detection
        ├── network/
        │   └── SolveItApi.kt                      # solveItPost, sendToSolveIt, greetingExists
        ├── speech/
        │   ├── SpeechEngine.kt                    # Interface
        │   ├── VoskSpeech.kt                      # Offline STT
        │   └── WhisperSpeech.kt                   # Cloud STT + Silero VAD + WAV encoding
        └── ui/
            ├── theme/                             # Color, Type, Theme
            ├── Bars.kt                            # TopBar, BottomBar composables
            └── Components.kt                      # StatItem, MicButton, WorkoutButtons, etc.
```

---

## Setup

### Prerequisites
- Android Studio
- ADB (`brew install android-platform-tools` on Mac)
- A [SolveIt](https://solveit.fast.ai) account and running instance

### 1. Download the Vosk Model
Download [`vosk-model-small-en-us-0.15`](https://alphacephei.com/vosk/models) and place the unpacked folder at:
```
app/src/main/assets/vosk-model-small-en-us-0.15/
```
> ⚠️ Gitignored (~40MB) — must be downloaded manually.

### 2. Add the Silero VAD AAR
Download [`android-vad-silero-v2.0.10-release.aar`](https://github.com/gkonovalov/android-vad/releases) and place it at:
```
app/libs/android-vad-silero-v2.0.10-release.aar
```
> ⚠️ Gitignored — must be downloaded manually.

### 3. Extract the Peloton Sensor Library
Pull `systempluginui.apk` from your Peloton via ADB and convert to JAR using `dex2jar`. Trim the JAR to remove duplicate classes (Compose, OkHttp, Kotlin stdlib) before adding to `app/libs/peloton-sensor.jar`.
> ⚠️ Gitignored — must be extracted manually. See project notes for the full trimming process.

### 4. Configure Secrets
Create `local.properties` in the project root (gitignored):
```properties
SOLVEIT_TOKEN=your-solveit-cookie-token
SOLVEIT_URL=https://your-instance.solve.it.com
OPENAI_API_KEY=sk-...
```
To find your SolveIt token: Chrome → DevTools → Application → Cookies → `_solveit`.

### 5. Enable ADB on your Peloton
```bash
# First time (USB required): enable wireless ADB
adb -d tcpip 5555
adb connect 192.168.x.x:5555

# Verify connection
adb devices
```

### 6. Build & Sideload
```bash
# In Android Studio: Build → Generate APK(s) → debug
# Then install:
adb -s 192.168.x.x:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech Stack

| Component | Technology |
|---|---|
| UI | Kotlin + Jetpack Compose |
| WebView | Android WebView + `evaluateJavascript` JS bridge |
| Offline STT | Vosk (Kaldi-based, `vosk-model-small-en-us-0.15`) |
| Cloud STT | OpenAI Whisper API (`whisper-1`) |
| Voice Activity Detection | Silero VAD (ONNX Runtime, on-device) |
| HTTP | OkHttp (form + multipart) |
| Treadmill sensors | Peloton system library (`TreadSensorManager`) |
| Secrets | `BuildConfig` fields from `local.properties` |
| Dark mode | Tailwind `dark` class injected via JS |

---

## Notable Engineering Notes

- **WebView layout fix** — Android WebView doesn't set `height: 100%` on `<html>`/`<body>` automatically. SolveIt's `#dialog-container` uses Tailwind `flex-grow`, which requires an explicit parent height. Fixed with two lines of CSS injected in `onPageFinished`.
- **JAR trimming** — `dex2jar` bundles everything from the APK, including Compose and Kotlin stdlib, causing duplicate class crashes. The `peloton-sensor.jar` must be manually trimmed to only Peloton's own classes.
- **HTMX navigation** — SolveIt uses `history.pushState` for navigation, so `onPageFinished` doesn't fire on dialog changes. A `Handler` polling loop (500ms) outside `onPageFinished` handles dialog detection and greeting logic.
- **WAV encoding** — `Integer.reverseBytes()` must not be used on shorts; `java.lang.Short.reverseBytes()` is required for correct WAV headers.
- **Dual STT toggle** — both engine instances are held in `remember {}` so switching engines doesn't reload models.

---

## Acknowledgements

Built entirely using the [SolveIt](https://solveit.fast.ai) small-steps methodology — one verified piece at a time, across 7 dialogs, learning Kotlin, Android, ADB, Vosk, ONNX, OkHttp, reverse engineering, and WebView internals from scratch.
