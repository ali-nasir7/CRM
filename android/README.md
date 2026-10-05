# CRM Phone Agent (Android)

Replaces the legacy PC-side adb bridge (`bridge/`) as the production calling path. A sales rep
installs this app, pairs it once from the CRM, grants two permissions, and the CRM's existing
Call button then dials through this phone's own SIM - no cable, no USB debugging, no per-phone
backend configuration.

```
CRM Frontend -> Spring Boot -> WebSocket (wss://.../ws/agent) -> CRM Phone Agent -> ACTION_CALL -> SIM
```

## Requirements

- Android Studio (Ladybug+) or a JDK 17 + the Android SDK on PATH.
- `compileSdk`/`targetSdk` 37, `minSdk` 26 (Android 8.0+).
- No signing key is checked in - `assembleRelease` produces `app-release-unsigned.apk`; sign it
  with your own key before distributing outside internal testing.

## Build

```bash
cd android
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew :app:testDebugUnitTest # plain-JVM unit tests (reconnect backoff schedule)
```

First run downloads the Gradle 9.4.1 distribution and, if missing, Android SDK Platform 37
(auto-accepts the license the same way Android Studio would). `local.properties` (gitignored)
must point `sdk.dir` at your SDK.

## Server environment

No per-phone `.env` and no rebuild-per-phone. The API/WebSocket base URL defaults come from
`BuildConfig.DEFAULT_API_BASE_URL` / `DEFAULT_WS_BASE_URL` (set per build type in
`app/build.gradle.kts` - debug defaults to `10.0.2.2` for the emulator), but the Welcome screen's
"Server settings" lets a rep override both before pairing; the choice is stored on-device
(`SecureDeviceStore`), not compiled in.

## Project layout

```
data/        SecureDeviceStore - EncryptedSharedPreferences (installation id, device token)
network/     ApiClient (REST pairing), AgentWebSocketClient (WS + backoff), WsMessages (protocol)
telephony/   CallExecutor (ACTION_CALL), CallStateReporter (PhoneStateListener), idempotency store
service/     PhoneAgentConnectionService (foreground service owning the socket), AgentStatus
ui/          Compose screens: Welcome, PairDevice, Permissions, Connected
```

## What this app does NOT do

- No adb, no USB debugging, no accessibility-service UI automation.
- No database credentials of any kind - the only server contact is the CRM's own REST/WS API.
- No fake call states: CONNECTED/ENDED/NO_ANSWER/FAILED are derived from real
  `TelephonyManager`/`PhoneStateListener` transitions, never assumed from the dial intent alone.

## Known Android limitations (see the project's final report for the full list)

- `PhoneStateListener` cannot distinguish "ringing out" from "answered" for an OUTGOING call -
  both are `CALL_STATE_OFFHOOK`. This app reports CONNECTED on first off-hook, matching exactly
  the same limitation `bridge/README.md` already documents for the legacy bridge; reps still
  confirm the true outcome in the CRM's outcome picker.
- The persistent WebSocket needs a foreground service (visible notification) to survive Android's
  background network restrictions - this is unavoidable for any app that must receive a remote
  "place a call now" signal while backgrounded, not a workaround.
- Some OEM battery managers (certain Xiaomi/Huawei/Samsung builds) kill background connections
  regardless of the standard "ignore battery optimizations" exemption; there is no universal API
  fix for this, only OEM-specific manual whitelisting.
