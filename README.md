# PelotonSolveIt

An Android app that turns your Peloton treadmill into a hands-free AI workstation. Browse [SolveIt](https://solveit.fast.ai) while running, speak questions hands-free, and get AI responses — all without touching the screen.

## What It Does

- 📱 Loads SolveIt in a full-screen WebView (scrollable while running)
- 🎤 Tap the mic button → speak → transcript sent to SolveIt as a prompt
- 🤖 SolveIt responds live in the WebView
- 📊 Sidebar for live Peloton stats (coming soon)

## Architecture

Two independent flows:

**Voice → SolveIt:**
Mic button → Android AudioRecord → Vosk STT → OkHttp POST → SolveIt API → WebView

**Live Stats (coming soon):**
Android app → FastHTML `/live-stats` → Peloton API → sidebar overlay

## Setup

### Prerequisites
- Android Studio (Panda 1+)
- ADB installed (`brew install android-platform-tools` on Mac)
- A [SolveIt](https://solveit.fast.ai) account

### 1. Download Vosk Model
Download [`vosk-model-small-en-us-0.15`](https://alphacephei.com/vosk/models) and place the folder at:
```
app/src/main/assets/vosk-model-small-en-us-0.15/
```
> ⚠️ This folder is gitignored (40MB) — must be downloaded manually.

### 2. Configure Secrets
Create/edit `local.properties` in the project root:
```
SOLVEIT_TOKEN=your-token-here
SOLVEIT_URL=https://your-instance.solve.it.com
SOLVEIT_DIALOG=your-dialog-name
```
To find your token: open SolveIt in Chrome → DevTools → Application → Cookies → `_solveit`

### 3. Build & Sideload
```bash
# Enable wireless ADB on Peloton (first time, requires USB)
adb -d tcpip 5555
adb connect 192.168.x.x:5555

# Build APK in Android Studio: Build → Generate APKs
# Then install:
adb -s 192.168.x.x:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack
- **Kotlin + Jetpack Compose** — Android UI
- **Vosk** — offline speech recognition (no Google services needed)
- **OkHttp** — HTTP client for SolveIt API calls
- **WebView** — embedded SolveIt browser
- **BuildConfig** — secure secrets management from `local.properties`

## Next Steps
- [ ] Move `startListening` to background thread
- [ ] Add live stats polling from FastHTML server
- [ ] Clean up sidebar UI
