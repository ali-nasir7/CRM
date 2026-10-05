# Android Phone Bridge (LEGACY / DEVELOPMENT FALLBACK)

**This is no longer the production calling path.** The CRM Phone Agent app (`android/`) replaces
it: a rep installs the app, pairs it from the CRM (Calls > Pair Phone Agent), and the CRM's Call
button dials over a WebSocket the app holds open - no PC, no adb, no USB debugging, no per-phone
backend configuration. See `android/README.md` and the project's phone-agent report for the full
architecture and API contract.

This bridge is kept only as a rollback/dev-machine option: `CallingDevice.connectionMode` is
`BRIDGE` for anything registered the old way and `AGENT` for anything paired through the new app,
and `CallRoutingTelephonyService` on the backend dispatches a call through exactly one of the two
paths based on that field - a device can never be dialed through both. New devices should be
paired through CRM Phone Agent; treat this bridge as legacy unless you have a specific reason
(no phone available yet, debugging the old path) to still run it.

---

Below is the original bridge documentation, unchanged, for anyone still running it.

---

This is the (legacy) piece that makes the CRM "Call" button dial via a PC-tethered phone. It is a
single Node.js script with **zero npm dependencies** - it uses `adb` to place the
call on YOUR Android phone with YOUR SIM, exactly like the architecture doc says:

```
CRM (Call button) -> Spring backend -> TelephonyService -> THIS BRIDGE -> adb -> Android phone -> SIM call
```

Call audio plays on the phone (hold it, or use speaker). If you also want to hear
the call on the PC, open it in the Phone Link app as usual - Phone Link remains the
audio UX only; it is not part of this integration.

The bridge exposes `GET /status` (same `X-Bridge-Token` header) with phone presence
and SIM state; the CRM's **Call health** panel (Calls page) and
`docs/CALL_AUDIO_TUNING.md` are built on it. Audio quality itself is decided by your
carrier/Bluetooth path - the bridge never touches call audio.

## One-time setup

1. **Node.js** must be installed (you already have it - the frontend builds with it).

2. **adb (Android platform-tools)** on the PC:
   - Download "SDK Platform Tools" from developer.android.com/tools/releases/platform-tools
   - Unzip anywhere, e.g. `C:\platform-tools`. Either add that folder to PATH, or set
     `ADB_PATH=C:\platform-tools\adb.exe` in `bridge/.env`.

3. **On the phone**: Settings > About phone > tap "Build number" 7 times to enable
   Developer options > enable **USB debugging**. Connect the USB cable and accept the
   "Allow USB debugging?" prompt ("Always allow").
   Verify on the PC: `adb devices` must list your phone with the word `device`.
   - Wireless (Android 11+, optional): Developer options > Wireless debugging > Pair
     device with pairing code, then `adb pair IP:PORT` once and set `ADB_CONNECT=IP:PORT`
     in `bridge/.env`.

4. **Get your Device ID from the CRM**: log in > Calls page > "My calling devices" >
   register your phone if you have not > copy the **Device ID** shown under its name.

## Configure and run

```bash
cd bridge
cp .env.example .env        # then edit .env:
```

Fill in at minimum:

| Variable         | Value                                                              |
|------------------|--------------------------------------------------------------------|
| BRIDGE_TOKEN     | same string as CRM_BRIDGE_TOKEN in the backend .env                |
| BRIDGE_DEVICE_ID | the Device ID you copied from the CRM                              |
| CRM_API_URL      | http://localhost:8080/api/v1 (adjust if your backend runs elsewhere) |
| BRIDGE_PUBLIC_URL| how the BACKEND reaches this PC: http://host.docker.internal:9090 if the backend runs in Docker, otherwise http://127.0.0.1:9090 |

Then start it:

```bash
node bridge/android-bridge.js
```

You should see `Phone connected via adb` and, within a minute,
`CRM heartbeat OK - device ... is ONLINE`. The device badge in the CRM turns
ONLINE by itself while the bridge can actually reach the phone, and goes OFFLINE
when you unplug it. That is exactly the "verify online before dial" rule.

## Place a call

Open any lead in the CRM > **Call** > pick your device > Call now. The phone starts
dialing immediately. The modal shows live state (RINGING -> CONNECTED with a timer;
ENDED / NO_ANSWER / FAILED are detected from the phone's telephony state), then you
record the outcome, notes, and optionally create a Call Back follow-up task.

## What the bridge cannot know (honest limits)

- **BUSY vs NO_ANSWER**: public Android APIs cannot reliably distinguish these for
  outgoing calls. The bridge reports NO_ANSWER; set the correct outcome in the modal
  afterwards if it was actually busy.
- One active call at a time (the phone could not dial two customers at once anyway).
- If you restart the bridge mid-call, the CRM call row may stay in RINGING - just
  record the outcome manually in the modal (that always works).

## Security

- Every bridge endpoint requires `X-Bridge-Token`, compared constant-time.
- Dial strings are sanitized to digits and `+` only - no shell injection is possible.
- Treat `BRIDGE_TOKEN` / `CRM_BRIDGE_TOKEN` as secrets: whoever holds it can mark
  devices online and push call states. Keep the bridge off the public internet;
  it only ever needs to be reachable by your CRM backend.
- No SIM identifiers (IMSI/ICCID) are read or stored anywhere; the CRM stores only
  the device name and public SIM number you type in yourself.
