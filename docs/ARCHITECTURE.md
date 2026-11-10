# Architecture

Mobile TrackPad turns an Android phone's touchscreen into a trackpad by presenting the phone as
a standard Bluetooth Classic HID mouse and keyboard. The computer needs no companion software.

This document describes the planned design. No code exists yet and nothing has been run on
hardware. Labels used for claims about platform behaviour:

- **[DOC]** — read in official Android documentation.
- **[AOSP]** — inferred from AOSP source (`packages/modules/Bluetooth`, branch `main`); older
  Android releases were not checked.
- **[UNVERIFIED]** — needs a physical phone and computer.

Report formats and HID request handling are in [HID.md](HID.md).

## 1. What this is and is not

- Standard mouse, keyboard and consumer-control input. Pinch, rotate and three-finger gestures
  are translated into shortcuts; they are not native multitouch trackpad events.
- The keyboard report is used only for gesture shortcuts. There is no typing feature.
- Bluetooth Classic HID only. A future USB or BLE transport would plug in behind `OutputSink`,
  but whether either can provide driverless HID must be researched separately.

## 2. Modules

| Module | Type | Contents |
|---|---|---|
| `:gesture` | Kotlin/JVM | Gesture recognizer. No Android dependency (enforced by module type). |
| `:hid` | Kotlin/JVM | Descriptor, report encoders, action model, host profiles, event-to-report mapping, ordered sender, `OutputSink`. Depends on `:gesture` for event types. |
| `:app` | Android application | Packages `bluetooth/`, `service/`, `data/`, `ui/`. |

applicationId and namespace: `com.mfouad.mobiletrackpad`. minSdk 28, compile and target SDK 37.

```
MotionEvent ─▶ ui/ TrackpadSurface ─▶ PointerFrame
                                          │  (engine thread)
                                          ▼
                              gesture/ GestureRecognizer ─▶ GestureEvent
                                          ▼
                              hid/ ActionMapper (host profile) ─▶ OutputCommand
                                          ▼
                              hid/ ReportSender (accumulate, order, tick)
                                          ▼
                              hid/ OutputSink  ◀── implemented by bluetooth/ HidDeviceTransport
                                          ▼
                              BluetoothHidDevice.sendReport(device, id, payload)
```

### `:gesture`

- Input: `PointerFrame(timeNanos, action, pointers)` where each pointer has a stable ID, x, y,
  and optional pressure and contact size.
- Output: `Move`, `ButtonDown`, `ButtonUp`, `Scroll`, `Pinch`, `Rotate`, `Swipe3`, `Cancel`.
- `Clock` and `Scheduler` are injected, so tap timeouts and inertia are deterministic in tests.
- All thresholds come from a `GestureConfig` value.

### `:hid`

- `HidDescriptor`: the descriptor bytes and report sizes.
- Encoders for report mode and boot mode.
- `Action`: `MouseButton`, `KeyChord(modifiers, keys)`, `ConsumerUsage`, `Sequence(actions)`.
- `HostProfile`: data mapping each gesture to an `Action`; built-in Windows, macOS, Linux/GNOME,
  Linux/KDE and Custom, all editable.
- `ReportSender`: the single ordered output path (section 6).
- `OutputSink`: `send(reportId, payload): Boolean` plus connection and protocol-mode state.
  Gesture recognition never references a transport.

### `:app`

- `bluetooth/` — thin wrapper over `BluetoothHidDevice`: proxy acquisition, registration,
  connection, callbacks, report delivery. Implements `OutputSink`.
- `service/` — the foreground service that owns the session.
- `data/` — DataStore for settings, per-host profiles, known hosts.
- `ui/` — Jetpack Compose, Material 3. Screens: Onboarding, Connection, Trackpad, Settings,
  Diagnostics.

## 3. Threading

One single-threaded **engine** executor owns:

- the gesture recognizer and its scheduler,
- the action mapper and report sender,
- the Bluetooth and session state machines,
- HID callbacks — it is the `Executor` passed to `registerApp`.

Because all of these run on one thread, gesture and HID code need no locks, and callback events
and touch input are processed in a single order.

Other threads:

- **Main thread** — receives `MotionEvent`s, converts each event (including its historical
  samples, in order) into `PointerFrame`s and posts them to the engine. Receives
  `ServiceListener` callbacks, which Android delivers on the main looper **[DOC]**, and forwards
  them to the engine.
- **DataStore** — its own IO dispatcher.

State flows to Compose as `StateFlow`s. The hop from main thread to engine adds a small,
unmeasured delay; no latency figure is claimed.

## 4. State machines

### 4.1 Bluetooth

```
Unavailable            no Bluetooth adapter
Disabled               adapter off
NoPermission           BLUETOOTH_CONNECT not granted (API 31+)
AcquiringProxy  ──▶ ProxyUnavailable       getProfileProxy false / service never connects
Registering     ──▶ RegistrationRejected   registerApp false, or no callback within timeout
Registered
Connecting      ──▶ ConnectFailed          timeout or back to DISCONNECTED
Connected(mode)        mode = Report | Boot
Disconnecting
Reconnecting(n)        bounded attempts
```

Rules:

- State changes come only from callbacks and broadcasts: `onServiceConnected` /
  `onServiceDisconnected`, `onAppStatusChanged`, `onConnectionStateChanged`, `onSetProtocol`,
  `onVirtualCableUnplug`, and `BluetoothAdapter.ACTION_STATE_CHANGED`.
- Return values of `registerApp`, `connect` and friends mean only "command sent" **[DOC]**. A
  `false` return produces a diagnostic; a `true` return changes nothing until the callback.
- `onAppStatusChanged(registered = false)` can arrive unsolicited, for example when Bluetooth
  is turned off **[DOC]** or the app loses foreground importance **[AOSP]**. It is handled as
  loss of registration from any state.

Diagnostic categories shown to the user:

| Category | Detected by |
|---|---|
| Bluetooth disabled | adapter state |
| Missing permissions | runtime permission check |
| Profile unavailable | `getProfileProxy` false or no `onServiceConnected` |
| Registration rejected | `registerApp` false or `registered = false`; possible causes listed: another app holds the HID device role, app not in foreground |
| Connection failure or timeout | no `STATE_CONNECTED` in time |
| Host bonded before HID registration | section 5.3 heuristic |
| Possible manufacturer incompatibility | proxy never available, or registration repeatedly rejected with the other causes excluded — worded as a possibility, never as a verdict |

Each category has its own message and retry action.

### 4.2 Session

```
Idle ─▶ Armed ─▶ Controlling ⇄ Paused ─▶ Idle
```

- **Idle** — no foreground service, not registered.
- **Armed** — service running, HID app registered; may be connected.
- **Controlling** — connected and the trackpad surface is visible and focused.
- **Paused** — session alive but the trackpad surface is not active.

### 4.3 Gesture

```
Idle
 ├─ 1 down ─▶ OnePending ─┬─ moved past slop ─▶ Moving
 │                        └─ up in time ─▶ TapWait ─┬─ timeout ─▶ click
 │                                                  └─ down again ─▶ DragArmed ─┬─ up ─▶ double click
 │                                                                              └─ move/hold ─▶ Dragging ─▶ (DragLocked)
 ├─ 2 down ─▶ TwoPending ─┬─ both up in time, little motion ─▶ right click
 │                        ├─ parallel travel ─▶ Scrolling ─▶ Inertia
 │                        ├─ distance change ─▶ Pinching
 │                        └─ angle change ─▶ Rotating
 └─ 3 down ─▶ ThreePending ─┬─ up in time ─▶ middle click
                            └─ travel past threshold ─▶ Swipe3(direction)
```

- In `TwoPending` the first of scroll, pinch or rotate to cross its threshold wins. Leaving the
  chosen gesture requires crossing a larger hysteresis threshold, so gestures do not flicker.
- Pointer-count changes pass through a short settle window. Lifting one of two fingers does not
  produce a tap, and movement is suppressed until the remaining pointer has a fresh baseline.
- A new touch, a cancel, or a disconnect stops inertia immediately.
- Edge-touch and large-contact rejection use `MotionEvent` size data where the device reports
  it. This is best effort, not hardware palm rejection.

## 5. Lifecycle

### 5.1 Foreground service owns the Bluetooth session

The stack refuses `registerApp`, and later unregisters the app, when the process importance is
worse than `IMPORTANCE_VISIBLE` **[AOSP]**. The reference states: "The app will be
automatically unregistered if it is not foreground" **[DOC]**. A process running a foreground
service ranks above that cutoff, so the service should keep registration alive with the screen
off **[AOSP]** **[UNVERIFIED]**. A foreground service is not treated as a guarantee on every
phone; loss of registration is always handled.

Sequence when the user taps **Enable** on the Connection screen:

1. Check `BLUETOOTH_CONNECT` (API 31+). It must already be granted: starting a
   `connectedDevice` service without a qualifying permission throws `SecurityException` on
   API 34+ **[DOC]**.
2. `startForegroundService`, then `ServiceCompat.startForeground` with
   `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`. Starting from a visible activity on a user tap
   is permitted **[DOC]**. `connectedDevice` services have no system timeout **[DOC]**.
3. `getProfileProxy(HID_DEVICE)` → `onServiceConnected`.
4. `registerApp` → wait for `onAppStatusChanged(registered = true)`.

Stop sequence (user taps **Stop**, or the service is otherwise ending while connected):

1. Cancel gestures and inertia; discard pending movement.
2. Send neutral keyboard, mouse and consumer reports.
3. `disconnect` → `unregisterApp` → `closeProfileProxy` → `stopForeground` / `stopSelf`.

The notification has a Stop action. If `POST_NOTIFICATIONS` is denied on API 33+, the service
still runs and appears only in the system Task Manager **[DOC]**.

### 5.2 UI visibility versus session lifetime

| Event | Effect |
|---|---|
| Leave the trackpad screen (navigate, Home, app switch) | Control **pauses**: held buttons, keys and consumer controls are released, inertia cancelled. Session, registration and connection stay. |
| Screen off | Same as leaving the trackpad screen. |
| Return to the trackpad screen | Control resumes. |
| Stop (in-app or notification) | Session **ends** (stop sequence above). |
| User stops the app from the system Task Manager (API 33+) | The process is killed with no callback **[DOC]**. No release reports can be sent. On next launch `ApplicationExitInfo` is checked and the user is told why the session ended *(Milestone 5)*. |
| Bluetooth turned off | Registration lost; session returns to Idle with the "Bluetooth disabled" diagnostic. |

The screen is kept awake only while the trackpad surface is visible and connected.

### 5.3 Pairing order

The host reads the phone's SDP record during pairing. If the HID service is not registered at
that moment, the host may cache the phone without it.

- Discoverability (`ACTION_REQUEST_DISCOVERABLE`) and pairing guidance are offered only in the
  `Registered` state.
- The app records hosts that bonded or connected while it was registered. Bonded devices whose
  Bluetooth class is "computer" and that are not in that record are flagged "paired before
  Mobile TrackPad was enabled — may need re-pairing".
- The fix offered is always the same: remove the pairing on both the phone and the computer,
  enable the trackpad, then pair again.

This is a heuristic. The phone cannot inspect what the host cached. The host's operating system
is never inferred from its name; the user picks a profile per computer.

### 5.4 Connection and reconnection

- The user chooses a paired computer; the app calls `connect` and waits for the callback.
  A host may also connect on its own while the app is registered; that is accepted.
- After an unexpected disconnect during an active session, the app retries the last host a
  bounded number of times with increasing delay. The user can cancel. *(Planned for
  Milestone 5. Until then an unexpected disconnect returns to "Ready" with a "Connection lost"
  message and the user reconnects manually.)*
- No automatic reconnect after an explicit disconnect.
- No automatic reconnect after `onVirtualCableUnplug`: the session ends for that host and the
  user is prompted to re-pair.

## 6. Output path and stuck-input safety

`ReportSender` is the only component that calls `OutputSink.send`.

- **Tick** — default every 8 ms (125 Hz), configurable down to about 90 Hz. The achievable rate
  on real links is **[UNVERIFIED]**.
- **No idle polling** — the tick timer runs only while there is something to send. It is started
  by the first pending movement or transition and stops as soon as the accumulator and queue
  are empty. A report is sent only when input changed: no repeated neutral or zero-delta
  reports, in any mode. An idle session causes no timer wake-ups and no radio traffic from the
  app.
- **Accumulation** — X, Y, wheel and pan are summed as floating-point values between ticks. Each
  tick emits the whole-number part and keeps the fraction, so total displacement is preserved.
- **Splitting** — a delta beyond a field's range is spread over consecutive reports (int16 in
  report mode, int8 in boot mode).
- **Ordering** — button, key and consumer transitions go through one FIFO. Before a transition
  is sent, pending movement is flushed, so a click lands where the pointer is.
- **Bounded work** — movement coalesces into the accumulator and cannot build a backlog.
  Transitions are never dropped; queued shortcut sequences have a small cap and new sequences
  are refused beyond it.
- **Shortcuts and scrolling** — before a modifier goes down for a shortcut, inertia is cancelled
  and pending wheel/pan is discarded, so leftover scrolling cannot become, say, Ctrl + wheel.
- **Submission is not delivery** — `sendReport` returning `true` means only that the stack took
  the report. Congested reports are dropped silently **[AOSP]**.

Held-state rules:

| Situation | Action |
|---|---|
| Intentional disconnect, unregister or shutdown | Send neutral reports for all three IDs while still connected, then proceed. |
| Unexpected disconnect | Clear local held buttons, modifiers, keys and consumer usage; cancel gestures, inertia, queued shortcuts and pending movement. Nothing is replayed later. |
| New connection established | First reports sent are neutral ones for all three IDs. |
| Protocol mode change | Discard pending movement; send neutral reports in the new format. |
| Touch cancel, or trackpad surface loses focus | Release held input while connected. |
| Drag lock engaged | A visible on-screen control releases it; it is also released by all of the above. |

Release reports cannot be delivered once the connection is gone, and the app never claims they
were. Neutral-on-connect covers the phone's side; a host that still believes a key is held
after a link drop is expected to clear it on disconnect, which is **[UNVERIFIED]** per host.

## 7. Battery-saving mode

Always, regardless of this mode: reports are sent only when input changes (section 6), and the
screen is kept awake only during an active control session — trackpad surface visible and a
host connected (section 5.2). The wake flag is cleared on pause, disconnect and stop.

Battery-saving mode is a setting (off by default) that additionally:

| Change | Detail |
|---|---|
| Dims the trackpad surface | Lowers the window brightness for the trackpad screen only, via the window's `screenBrightness` attribute; the system brightness setting is not touched. The surface is already true black. Indicators and on-screen buttons are drawn at reduced contrast but stay legible. |
| Disables decorative animations | Touch ripples, transitions and indicator animations on the trackpad screen are off. State changes that carry meaning (connection lost, drag lock on) are still shown, without animation. |
| Disables optional haptics | Click haptics are suppressed while the mode is on, whatever the haptics setting says. |
| Does not request high refresh rates | The app never asks for a high display refresh rate in this mode and, where the platform allows, states a preference for a standard-rate display mode on the trackpad screen. The system makes the final choice. |
| Reduces report frequency when appropriate | The send interval rises from 8 ms to about 11 ms (90 Hz) for movement and scrolling. Inertia runs at a lower rate still. Button, key and consumer transitions are never delayed, and total displacement is preserved because movement keeps accumulating between sends. |

Limits and unknowns:

- Touch sampling is driven by the system; the app does not control the digitizer rate.
- Actual battery effect of each item is **[UNVERIFIED]** and no saving is claimed until measured
  on a phone.
- Whether a lower display rate noticeably affects touch latency on a given phone is
  **[UNVERIFIED]**.
- The Bluetooth link's own power behaviour (sniff mode) is managed by the two Bluetooth stacks,
  not by the app; sending nothing while idle is what lets the link go quiet.

Milestones: the no-idle sender is part of Milestone 1 (with a test that an idle sender schedules
no work); the wake-lock rule arrives with the trackpad surface in Milestone 3; the
battery-saving setting and its effects are part of Milestone 5.

## 8. Permissions and manifest

| Permission | Purpose |
|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) | Bluetooth on API 28–30, install-time **[DOC]** |
| `BLUETOOTH_CONNECT` | API 31+ runtime; required by every `BluetoothHidDevice` method, `getBondedDevices`, `getName` **[DOC]** |
| `BLUETOOTH_ADVERTISE` | API 31+ runtime; required for `ACTION_REQUEST_DISCOVERABLE` **[DOC]** |
| `FOREGROUND_SERVICE` | API 28+ **[DOC]** |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34+ **[DOC]** |
| `POST_NOTIFICATIONS` | API 33+ runtime; optional |
| `VIBRATE` | Haptic feedback |

Not requested: `BLUETOOTH_SCAN`, location, `INTERNET`. The app never scans; it works only with
devices the user paired through system settings.

Whether `BLUETOOTH_ADMIN` is strictly needed for the discoverable request on API 28–30 was not
confirmed; it is declared for those versions regardless.

## 9. Data

DataStore holds:

- Global settings: battery-saving mode, sensitivity, acceleration, scroll speed/direction/inertia, tap-to-click, drag
  lock, haptics, left-handed layout, on-screen buttons, send interval, gesture thresholds.
- Known hosts: address, display name, selected profile, whether it bonded or connected while
  registered, whether it needs re-pairing.
- Profiles: built-in defaults plus user edits, stored as data.
- Diagnostics enabled flag (off by default). The log itself stays in memory and leaves the app
  only when the user copies or shares it.

## 10. Diagnostics

Delivered in Milestone 2 together with Bluetooth, since it is the main tool for hardware
testing.

- Timestamped live log of recognized gestures, reports sent (report ID and payload in hex) and
  Bluetooth callback events.
- Current Bluetooth state and protocol mode (Report or Boot).
- Copy and share as plain text.
- Bounded ring buffer; off by default.

## 11. Toolchain

Versions were chosen as one supported combination, preferring what was already installed and
cached on the development machine, rather than the newest release of each tool.

| Component | Version | Why |
|---|---|---|
| JDK (Gradle daemon and compilation) | 17 | Minimum and default JDK for AGP 9.x; pinned via `gradle/gradle-daemon-jvm.properties` |
| Gradle (wrapper) | 9.4.1 | Already cached; the version listed for AGP 9.2 in the AGP compatibility table |
| Android Gradle Plugin | 9.2.1 | Cached. AGP 9.3 requires Gradle ≥ 9.5.0, so it does not pair with 9.4.1. API 37 needs AGP ≥ 9.1.1 |
| Kotlin | 2.2.10 | The Kotlin Gradle Plugin version AGP 9.2.1 itself depends on (AGP 9 built-in Kotlin) |
| Compose compiler plugin | 2.2.10 | Must equal the Kotlin version |
| Compose UI / foundation | 1.12.1 | Requires compileSdk 37 and AGP 9. Compose 1.13 is the first line requiring kotlinc 2.2+ |
| Material 3 | 1.4.0 | Latest stable (1.5.0 is still a release candidate) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 28 | Android 17 is the latest stable platform |
| ktlint | 1.8.0 | Run through Spotless 8.4.0 (cached; includes its Gradle 9 fix) |
| detekt | 1.23.8 | Latest stable; run as the standalone CLI in an isolated configuration |

detekt runs as its own CLI rather than through the detekt Gradle plugin. The plugin's
compatibility with Gradle 9 is undocumented, and the CLI keeps detekt's bundled Kotlin 2.0.21
compiler off the build classpath. It runs without type resolution, so type-aware rules are
inactive.

Verified locally (2026-11-11): the full check (`spotlessCheck detekt test :app:lintDebug
:app:assembleDebug`) passes on this combination. CI uses the same commands; its first run on
GitHub has not happened yet.

Upgrade path when needed: Gradle 9.5+ allows AGP 9.3; a newer Kotlin needs a matching Compose
compiler plugin; detekt 2.0 (still alpha) would add Kotlin 2.4 support.

## 12. Testing and evidence

Results are always reported in three separate categories. A Bluetooth behaviour is never marked
as working on emulator or log evidence alone.

| Category | Covers | How |
|---|---|---|
| Automated tests | Gesture recognition, encoders, descriptor sizes, output ordering, accumulation, disconnect cleanup, action mapping | JVM unit tests in `:gesture` and `:hid` |
| Emulator | Build, install, navigation, onboarding, permission flows, settings, mapping editor, screenshots of each screen, and the full gesture-to-report path | Android emulator; instrumented Compose UI tests |
| Real phone, confirmed by the user | Registration, pairing, connection, pointer movement, clicks, scrolling, gestures, reconnect, stuck-input checks | Phone over wireless `adb`; the user performs each step and reports what the computer did |

- **Fake output sink** — a debug-only `RecordingOutputSink` (in the `debug` source set, absent
  from release builds) implements `OutputSink` and records reports instead of sending them. On
  the emulator it replaces the Bluetooth transport, so touch input runs through the recognizer,
  mapper and sender unchanged.
- **Simulated computer** — in debug builds the Connection screen offers "Start simulated
  computer". It drives the same session state machine and report sender, with the recorder as
  the transport, and needs no Bluetooth or foreground service.
- **Instrumented tests** — `./gradlew :app:connectedDebugAndroidTest` on an emulator. They
  drive the real UI against the simulated computer and assert on the exact recorded reports
  (Milestone 2: test-input buttons, neutral-on-connect, release-on-leave, stop). From
  Milestone 3 they also inject multi-touch input on the trackpad surface. CI runs only the JVM
  checks; instrumented tests are run locally.
- **Real-device tests** — one test at a time: install the build, capture logcat, give numbered
  steps, ask a specific question about what happened, then compare the answer with logcat and
  the Diagnostics log before drawing a conclusion. A phone/computer/OS combination enters the
  README's tested table only after the user confirms the result.

## 13. Milestones

1. Docs, scaffold, descriptor, encoders (report and boot mode), unit tests, CI.
2. Bluetooth registration, pairing guidance and pairing-order handling, connection, test
   movement, Diagnostics screen, stuck-input safety.
3. Trackpad surface, movement, taps, dragging, two-finger scrolling.
4. Pinch, rotate and three-finger mappings, host profiles, mapping editor.
5. Complete settings, onboarding, reconnection, accessibility, polish.
6. Research only: native multitouch HID feasibility. Microsoft lists USB, I²C and SPI as
   Precision Touchpad transports, not Bluetooth; changing the descriptor is not assumed to
   enable native gestures.

## 14. Unresolved questions

All need a physical Android phone and a computer.

1. Does a foreground service keep HID registration alive with the screen off, on stock and
   manufacturer Android builds from 9 to 17?
2. Is the composite descriptor accepted by Windows, macOS and Linux? (Details in HID.md §9.)
3. Do hosts clear held keys and buttons when the link drops?
4. What report rate is sustainable before the stack drops reports?
5. Do hosts reconnect to the phone on their own after it re-registers, and does that interact
   badly with the app's bounded reconnect?
6. Does the bonded-before-registration heuristic produce false warnings in practice?

## 15. Observed platform issues

### Bluetooth turned off while registered blocks re-registration (Samsung)

Observed 2026-11-11 on a Samsung Galaxy S25 Ultra (SM-S938B), Android 17 (API 37), build
`S938BXXSDDZJ1`, and confirmed by the user on the phone. Other phones are untested.

On this build, registering an HID device app first disables the phone's HID Host role, and
unregistering re-enables it. If Bluetooth is turned off **while the app is registered**
(connected to a computer or not):

1. At shutdown, HID Host logs `cleanup_in_jni_thread: HH disabling or disabled already` and skips
   its normal cleanup.
2. After Bluetooth is turned back on, the HID Host profile starts but never re-enables (no
   `bta_hh_api_enable` / `BTA_HH_ENABLE_EVT`).
3. The next `registerApp` returns `true`, the stack asks HID Host to disable, HID Host reports a
   Samsung-specific `BTA_HH_REG_HD_STATE: BTHH_HS_HID_NOT_READY`, and because HID Host was already
   disabled no `BTA_HH_DISABLE_EVT` follows. Registration waits forever; `onAppStatusChanged`
   never arrives.

Turning Bluetooth off and on again does not clear it (the Bluetooth process keeps running). A
phone restart does. While stuck, the phone's own Bluetooth mice and keyboards are presumably
affected too, since HID Host is down (not tested).

Stopping the session first (which unregisters the app) and then turning Bluetooth off is safe:
HID Host re-enables on unregister and again after Bluetooth returns.

The app cannot prevent this: it learns of the adapter turning off (`ACTION_STATE_CHANGED`) only
after the stack has started disabling. What the app does:

- Bluetooth turning off during a session is reported as its own problem
  (`BLUETOOTH_TURNED_OFF_WHILE_ACTIVE`) with advice to tap Stop first next time and to restart
  the phone if enabling fails.
- A `registerApp` that is accepted but never answered within 5 s is reported as
  `REGISTRATION_STALLED`, with advice to restart the phone, instead of blaming another app.
