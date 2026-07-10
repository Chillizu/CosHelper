# CosHelper UI/UX  overhaul Plan

> Priority: **UX > UI > Features**

## Reference design

User-supplied screenshots (`be1e...png`, `330c...webp`) show a clean, light-themed M3-style interface with these characteristics:

- **Light color scheme**: near-white background, subtle surface elevation, dark primary text.
- **Rounded rectangular tiles** (not circles): quick-settings-style buttons with an icon on top and a label below, grouped in a grid.
- **Top segmented toggle**: `智能 / 动漫` as a rounded pill toggle group (M3 `SingleChoiceSegmentedButtonRow`).
- **Top status chips**: small icon + text chips (e.g., link icon + `0.5`) for state/metadata.
- **Centered empty-state card**: large icon inside a rounded container with title and helper text.
- **Bottom action / input bar**: a rounded input field at the bottom, with small icon buttons on its trailing side.
- **Soft shadows and generous spacing**: no heavy borders, content breathes.

The user confirmed the design reference is **Material Design 3** (and Material You is acceptable). We will follow M3 component patterns and use the screenshots as the visual target.

## 1. Background & current problems

The current UI is built around oversized circular buttons (`BigRoundButton`, `SmallRoundButton`, `CircleShape` PTT button) and mixed information architecture. Specific user questions:

1. **Voice changer toggle is ambiguous** — “开启变声” does not tell the user what is happening. The feature is real-time microphone-through-RVC-to-speaker passthrough; the UI should say so.
2. **Audio I/O settings should live in the feature screens** — today they are hidden in a separate `SettingsScreen`.
3. **Intercom and STT should also have input settings** — and these settings should be configurable per-feature.
4. **Settings page is too cluttered** — model paths, accessibility, and system-level toggles should be in Settings; device routing should be in each feature.
5. **Visual style** — circular capsules are not consistent with Material Design / M3. Reference screenshots will be used once available.

## 2. UX / Information Architecture (Phase 1 — highest priority)

### 2.1 New screen map

```
Home
├── 对讲 (Chat / Intercom)
│   ├── Mode selector: Nearby / Hotspot
│   ├── Connection controls
│   ├── PTT toggle area
│   └── Input device card (per-feature)
├── 语音转文字 (STT)
│   ├── Transcript area
│   ├── Start/Stop control
│   └── Input device card (per-feature)
├── 变声器 (RVC)
│   ├── Model selector (load / unload)
│   ├── Real-time status: 未加载 / 已加载 / 变声中
│   ├── Input device card
│   ├── Output device card
│   └── 试听/暂停 toggle
└── 设置 (Settings)
    ├── 模型路径 (RVC ONNX, STT model, etc.)
    ├── 权限与无障碍 (PTT accessibility, permission checks)
    ├── 音频默认设置 (global default input/output)
    ├── 通信模式 (global default)
    └── 关于
```

### 2.2 Per-feature audio routing — decision

**Answer to the user’s question: Yes, each feature can have its own input/output setting.**

Current `AudioRouter` is a singleton with a single `preferredInputDeviceId` / `preferredOutputDeviceId`. This means all features share the same device. To support per-feature routing, we will:

- Keep the global default in `AudioRouter` (used when a feature has no override).
- Add `AudioRecorder.start(inputDeviceId: Int?)` and `AudioPlayer.setPreferredOutputDevice(deviceId: Int?)` so each feature can pass an explicit device ID.
- Persist per-feature overrides via `DataStore` or `SharedPreferences` under keys like `audio_input_chat`, `audio_input_stt`, `audio_input_rvc`, `audio_output_rvc`.
- Each feature screen reads its own override and shows a device picker; an option “使用全局默认” resets the override to `null`.

### 2.3 Voice Changer UX clarity

- **状态文案明确**:
  - `未加载模型` → “请选择并加载 RVC 模型”
  - `已加载` → “准备就绪，点击下方播放按钮开始实时变声”
  - `变声中` → “实时变声运行中”
- **Primary action**: a toggle labeled **“实时变声”** (or **“暂停变声”** when running), not just a switch.
- **Preview / monitoring**: when running, a small VU meter or “监听中” indicator is shown so the user knows audio is flowing.

## 3. UI / Design System (Phase 2)

Adopt **Material Design 3** components unless the user specifies a different “MT” guideline.

### 3.1 Components to use (informed by the reference screenshots)

- **Home**: a 2-column grid of **rounded rectangular tiles** (like the quick-settings grid in the second screenshot). Each tile has a centered icon above a label, 16dp corner radius, subtle tonal surface. No circles.
- **Top navigation**: `CenterAlignedTopAppBar` on inner screens, with a back arrow. Home can stay clean or use a small title at the top.
- **Mode switch**: `SingleChoiceSegmentedButtonRow` (or a pill toggle group) for Nearby / Hotspot, matching the `智能 / 动漫` switch in the first screenshot.
- **Status chips**: `AssistChip` with leading icon + text at the top of the screen (e.g., connection status, model status), matching the small chips in the first screenshot.
- **PTT**: a large rounded rectangle button (`RoundedCornerShape(24dp)`), not a circle, with a clear active/inactive tonal difference. Label: `按住说话` → `说话中`.
- **Bottom action bar**: a full-width rounded `FilledTonalButton` or `BottomAppBar` at the screen bottom for primary actions (start/stop STT, start/stop RVC, connection buttons).
- **Device picker**: `ExposedDropdownMenuBox` (or a bottom sheet) showing device type + name, with a “全局默认” option.
- **Settings**: `ListItem` / `SettingsTile` grouped by `Divider` sections, with trailing icons and switches.
- **Empty state**: a centered rounded container with a large icon, title, and helper text (first screenshot).

### 3.2 Theme

The reference screenshots use a **light M3 color scheme**. CosHelper currently uses a dark scheme. The plan targets a light theme by default, but we can keep dynamic theming or a dark option if desired.

- Use `MaterialTheme.colorScheme` tokens and `dynamicDarkColorScheme` / `dynamicLightColorScheme` if available (Android 12+).
- Use `MaterialTheme.typography` tokens (`titleLarge`, `headlineSmall`, `bodyLarge`, `labelLarge`) instead of hard-coded `fontSize`.
- If the user prefers to keep the dark theme, the same component shapes and layout still apply.

### 3.3 Layout constants

- Standard screen padding: 16dp.
- Tile / card corner radius: 16dp.
- Tile spacing: 12dp.
- Section gaps: 24dp.
- Bottom action bar height: 64dp with 16dp horizontal padding.

## 4. Feature Work (Phase 3 — after UX and UI are agreed)

### 4.1 Audio layer changes

- `AudioRouter`:
  - Add `getInputDevices()` / `getOutputDevices()` (already exists).
  - Add `resetInputDevice()` / `resetOutputDevice()` for “use default”.
- `AudioRecorder`:
  - Add `start(inputDeviceId: Int?)` overload.
  - `buildAudioRecord()` applies `audioRecord.setPreferredDevice(...)` when device ID is non-null and API >= 23.
- `AudioPlayer`:
  - Add `setPreferredOutputDevice(deviceId: Int?)` and apply it in `rebuildTrack()`.

### 4.2 Data store for per-feature preferences

- Add `AudioSettingsRepository` backed by `DataStore<Preferences>` (or `SharedPreferences` if project is below API 24 / avoiding DataStore dependencies).
- Keys:
  - `audio.input.default`
  - `audio.input.chat`
  - `audio.input.stt`
  - `audio.input.rvc`
  - `audio.output.default`
  - `audio.output.rvc`

### 4.3 Composable device picker

- Create `AudioDevicePicker` composable that:
  - Observes device list via `AudioDeviceCallback`.
  - Shows a dropdown with devices + “全局默认” + “系统默认”.
  - Calls `onDeviceSelected(deviceId)` and optionally `onResetToDefault()`.
- Reuse this picker in Chat, STT, RVC, and Settings.

### 4.4 Screen refactors

- **HomeScreen**: replace `BigRoundButton`/`SmallRoundButton` with M3 `Card`s in a grid.
- **ChatScreen**:
  - Move audio input picker to a collapsible card at the bottom or top.
  - Replace `useHotspot` boolean toggle with `SingleChoiceSegmentedButtonRow` (Nearby / Hotspot).
  - PTT area: rounded rectangle, not circle, with `clickable` toggle.
- **SttScreen**:
  - Add input device picker.
  - Transcript uses a `Card` with `scrollable` text.
  - Start/Stop uses a `FloatingActionButton` or a full-width `Button`.
- **RvcScreen**:
  - Add input + output device pickers.
  - Show model path as a `ListItem` with “修改” icon, path editable in Settings.
  - Replace the `Switch` with a full-width `Button` whose label changes (`开始实时变声` / `停止实时变声`).
  - Show a progress/spinner or VU meter while running.
- **SettingsScreen**:
  - Sections: 音频默认、模型路径、权限与无障碍、关于.
  - Model paths: `OutlinedTextField` with optional file picker button.
  - Accessibility: link to system settings, with a checkmark if enabled.
  - Remove device lists from here; keep only global default input/output pickers.

### 4.5 Remove dead patterns

- Delete `BigRoundButton` and `SmallRoundButton` from `MainActivity.kt`.
- Remove all `shape = CircleShape` from buttons, except where M3 explicitly calls for it (e.g., FAB small shape, but even then we prefer `RoundedCornerShape`).

## 5. Verification (Phase 4)

- [ ] Gradle build passes.
- [ ] Screenshots on emulator for Home, Chat, STT, RVC, Settings.
- [ ] Verify PTT toggle in Hotspot mode still works.
- [ ] Verify STT status flow unchanged.
- [ ] Verify RVC load/start/stop lifecycle unchanged.
- [ ] Verify device selection overrides are persisted.

## 6. Decisions (confirmed by user)

1. **Theme**: Follow the system light/dark setting. Use `isSystemInDarkTheme()` and provide both light and dark M3 color schemes, with dynamic colors on Android 12+ if available.
2. **Voice changer output device picker**: Always visible.
3. **STT feedback**: Add a recognition beep/tone on start and stop.

## 7. Open questions before starting implementation

None remaining. Proceed to implementation.

Answers to these determine the final tokens and component choices.

## 7. Proposed milestones

| Milestone | Scope | Priority |
|---|---|---|
| **M1: UX & IA** | Define new screen map, per-feature audio routing model, remove device settings from Settings | P0 |
| **M2: Design System** | Theme tokens, reusable card/picker/button components, remove circular buttons | P1 |
| **M3: Screen Refactors** | Home, Chat, STT, RVC, Settings refactored with new components | P1 |
| **M4: Audio Routing** | Per-feature device override persistence, `AudioRecorder`/`AudioPlayer` overloads | P2 |
| **M5: Polish & Verify** | Screenshots, build, regression tests | P2 |

---

Next action: confirm the open questions and start M1.
