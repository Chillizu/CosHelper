# CosHelper STT / Hotspot / Peer Verification Summary

Generated: 2026-07-10

## Verified claims

| Claim | Evidence |
|---|---|
| Whisper `ggml-base-q5_1.bin` bundled at build time | `DownloadWhisperModelTask` in `app/build.gradle.kts` downloads into `src/main/assets/models/`. Verified asset size ~59.7 MB in APK. |
| STT loads bundled model and reports status | `SttManager.loadModelFromAssetsOrDefault()` copies asset to `filesDir` and loads. `SttScreen` shows `模型加载中…` / `就绪` / `正在听…` / `已停止`. |
| Hotspot state exposed | `HotspotChatManager` exposes `state` and `status` StateFlows. `ChatScreen` displays status and provides `连接测试对端` for emulator host testing. |
| Virtual peer works | `tools/chat_peer.py` implements 10-byte transport frame and Opus encoding/decoding via `ctypes`. Server/client echo and canned modes verified. |
| AudioRecorder fixes | `ensureScope()` recreates cancelled scope. Frame buffer fills to 320 samples before callback; partial reads padded with silence. |
| Opus JNI loaded | `OpusCodec` loads `libopus.so` and `libopus_jni.so`. |
| Git history clean | Single commit excludes `.idea/`, `app/build/`, `*.bin`, `*.pyc`, `__pycache__/`. |
| Debug-only tone fallback | `AudioRecorder` tone injection is guarded by `if (BuildConfig.DEBUG)`. Release builds pad partial frames with silence. |

## Functional verification results

| Check | Result | Notes |
|---|---|---|
| STT | [OK] | Status transitions correct; no native crash. |
| Hotspot server + peer client | [OK] | Peer received ~2200+ frames. |
| Hotspot client + peer server | [OK] | App connected to `10.0.2.2`; peer received ~1800+ frames; no Opus errors. |
| Nearby single-emulator | [OK] | Discovery started, 30s timeout returned to `待机`, no crash. |
| Nearby two-emulator | [N/A] | Only one emulator available; cannot bridge mDNS/BT across emulator NAT. |

## Known defects and risks

### Medium

1. **Dual PTT activation race** (`ChatScreen.kt`)
   - Both the on-screen clickable toggle and `PTTAccessibilityService` (volume key) can call `hotspotManager.startPtt()` / `stopPtt()`.
   - `AudioRecorder.start()` has no synchronized guard between `stop()` and `record.startRecording()`, so rapid concurrent calls could race.
   - Recommended: add an `AtomicBoolean` or `synchronized` gate around `AudioRecorder.start()`/`stop()`.

2. **Missing WiFi/NSD permissions in `ChatScreen`**
   - `ChatScreen` requests `RECORD_AUDIO`, Bluetooth, and `NEARBY_WIFI_DEVICES`, but not `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE`.
   - `HotspotChatManager.hasPermissions()` checks those, so on a fresh install the hotspot buttons may show but fail with a permissions error.
   - Recommended: add `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` to the permission launcher.

### Low

3. **Missing `NEARBY_WIFI_DEVICES` check in `hasPermissions()`**
   - `HotspotChatManager.hasPermissions()` does not enforce `NEARBY_WIFI_DEVICES` on Android 13+, although it is declared in `AndroidManifest.xml`.

4. **HotspotState race on multi-client disconnect**
   - `handleSocket()` reads `_state.value` and then writes `Idle` if it sees `ClientConnected`. This is not atomic; on the server side, disconnect of one client while another is connected would incorrectly reset to `Idle`.
   - (Server currently tracks only one concurrent client; real multi-client support would need a counter.)

5. **Dead code in `chat_peer.py`**
   - `run_client()` has an `if mode == 'canned': get_canned_payload()` / `else: get_canned_payload()` branch that is redundant.

## Unverified items

The following require real hardware or a different test environment to fully validate:

1. **NSD discovery on real devices** — single-emulator test only confirms timeout/no-crash; mDNS/BT bridging across devices is not validated.
2. **Whisper STT transcription quality** — only UI state machine and native loading were verified; actual transcription needs real audio input.
3. **AudioRecorder with real microphone** — emulator returned zero or partial audio bytes; the real input path depends on a physical device.
4. **Volume-key PTT accessibility service on real hardware** — emulator accessibility stack differs from real devices; other services may consume volume key events.
5. **Hotspot TCP throughput / latency under real WiFi** — frame counts verified, but audio playback quality and latency on a real WiFi Direct/hotspot network were not measured.

## Emulator limitations

- **Microphone**: The emulator does not provide usable audio input. A debug-only 1 kHz sine tone was added to exercise the send path.
- **Network**: The emulator uses NAT, so host-to-device communication relies on `adb forward/reverse`. Direct mDNS across two emulators is not bridged.
- **Bluetooth / NFC**: Nearby over Bluetooth and NFC cannot be tested; only WiFi LAN discovery was exercised, and it only found the local device.
- **Accessibility**: The volume-key PTT accessibility service did not intercept key events in the emulator; this may work on real devices with proper accessibility service precedence.

## Git state

- Branches: `main` and `dev` pushed to `Chillizu/CosHelper`.
- `dev` is the current working branch.
- Build artifacts, IDE files, and `__pycache__` are excluded.
