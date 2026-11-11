# HID design

This document defines the HID identity the phone presents over Bluetooth Classic: the report
descriptor, the report layouts, and how the app answers HID requests.

Nothing here has been run on hardware yet. Every claim carries one of these labels:

| Label | Meaning |
|---|---|
| **[SPEC]** | Read in a specification: USB-IF *Device Class Definition for HID 1.11* ("HID 1.11"), USB-IF *HID Usage Tables 1.7* ("HUT 1.7"), Bluetooth SIG *HID Profile v1.1.2* ("BT HID"). |
| **[DOC]** | Read in the official Android reference or guides. |
| **[AOSP]** | Inferred from AOSP source (`packages/modules/Bluetooth`, branch `main`). Older Android releases were not checked and may differ. |
| **[COMPUTED]** | Derived by us from spec rules (for example, an item prefix byte built from the spec's bit pattern). |
| **[UNVERIFIED]** | Not confirmed by any source or test. Needs a physical phone and computer. |

Sources not read: Bluetooth HID Profile v1.1.1 (login-gated; v1.1.2 was used instead).

## 1. Scope

- Standard mouse, keyboard and consumer-control reports only. Pinch, rotate and three-finger
  gestures are delivered as shortcuts built from these reports. They are **not** native
  multitouch trackpad events.
- The keyboard report exists only to deliver gesture shortcuts. The app has no typing feature
  and no keyboard input UI.
- Transport: Bluetooth Classic HID via `android.bluetooth.BluetoothHidDevice` (API 28+) **[DOC]**.

## 2. SDP registration settings

Passed to `BluetoothHidDeviceAppSdpSettings(name, description, provider, subclass, descriptors)`.

| Field | Value | Basis |
|---|---|---|
| `name` | `PocketPad` | max 50 bytes **[DOC]** |
| `description` | `Bluetooth mouse` | max 50 bytes **[DOC]** |
| `provider` | `PocketPad` | max 50 bytes **[DOC]** |
| `subclass` | `SUBCLASS1_COMBO` (0xC0) | "combo keyboard and mouse subclass" **[DOC]**; bits 7–6 = 11 is Combo keyboard/pointing in BT HID Table 5.4/5.5 **[SPEC]** |
| `descriptors` | the 166 bytes in section 3 | max 2048 bytes **[DOC]** |

These strings are the HID service's name, not the name computers list during pairing; that is
the phone's own Bluetooth name, which the app never changes. Hosts store the SDP record when they
pair, so a computer paired before the rename from "Mobile TrackPad" to "PocketPad" (0.1.0
development builds) may keep showing the old service name. The descriptor and report IDs did not
change, so such a computer keeps working and does not need to pair again.
| QoS in/out | `null` | optional **[DOC]** |

The Android stack, not the app, sets the remaining SDP attributes. On AOSP `main` they are
fixed: `HIDVirtualCable = true`, `HIDReconnectInitiate = true`, `HIDNormallyConnectable = true`,
`HIDBootDevice = true` **[AOSP]**. The app cannot change them. See section 6 for what
`HIDBootDevice = true` obliges us to do.

## 3. Report descriptor

Three top-level Application collections, one input report ID each. Total **166 bytes**
(keyboard 66, mouse 75, consumer 25) **[COMPUTED]**.

Item prefix bytes follow HID 1.11 §6.2.2.2–6.2.2.8: `bTag` in bits 7–4, `bType` in bits 3–2,
`bSize` in bits 1–0 **[SPEC]**. The prefix values were computed from those bit patterns and
match the bytes used in the spec's own Appendix E examples **[COMPUTED]**. Multi-byte item data
is written least-significant byte first, as in the spec's examples.

Global items (Usage Page, Logical Min/Max, Report Size, Report Count, Report ID) stay in force
until changed; the annotations note where a field relies on a value set earlier.

### 3.1 Keyboard — Report ID 1

Same items as the HID 1.11 Appendix B.1 boot keyboard, plus a Report ID **[SPEC]**.

| Bytes | Item | Notes |
|---|---|---|
| `05 01` | Usage Page (Generic Desktop) | page 0x01 |
| `09 06` | Usage (Keyboard) | 0x06, CA |
| `A1 01` | Collection (Application) | |
| `85 01` | Report ID (1) | |
| `05 07` | Usage Page (Keyboard/Keypad) | page 0x07 |
| `19 E0` | Usage Minimum (0xE0 LeftControl) | |
| `29 E7` | Usage Maximum (0xE7 Right GUI) | |
| `15 00` | Logical Minimum (0) | |
| `25 01` | Logical Maximum (1) | |
| `75 01` | Report Size (1) | |
| `95 08` | Report Count (8) | |
| `81 02` | Input (Data, Variable, Absolute) | modifier byte, 8 × 1 bit |
| `95 01` | Report Count (1) | |
| `75 08` | Report Size (8) | |
| `81 01` | Input (Constant) | reserved byte |
| `95 05` | Report Count (5) | |
| `75 01` | Report Size (1) | |
| `05 08` | Usage Page (LEDs) | page 0x08 |
| `19 01` | Usage Minimum (1 Num Lock) | |
| `29 05` | Usage Maximum (5 Kana) | |
| `91 02` | Output (Data, Variable, Absolute) | 5 LED bits |
| `95 01` | Report Count (1) | |
| `75 03` | Report Size (3) | |
| `91 01` | Output (Constant) | 3 pad bits |
| `95 06` | Report Count (6) | |
| `75 08` | Report Size (8) | |
| `15 00` | Logical Minimum (0) | |
| `26 FF 00` | Logical Maximum (255) | 2-byte form: a 1-byte `FF` would read as −1 |
| `05 07` | Usage Page (Keyboard/Keypad) | |
| `19 00` | Usage Minimum (0) | |
| `29 FF` | Usage Maximum (255) | local items are unsigned **[SPEC]** |
| `81 00` | Input (Data, Array) | six key slots |
| `C0` | End Collection | |

### 3.2 Mouse — Report ID 2

Structure follows the HID 1.11 Appendix B.2 mouse, extended to five buttons, 16-bit X/Y, a
wheel, and AC Pan as in Microsoft's wheel white paper sample (without its Resolution Multiplier
feature report).

| Bytes | Item | Notes |
|---|---|---|
| `05 01` | Usage Page (Generic Desktop) | |
| `09 02` | Usage (Mouse) | 0x02, CA |
| `A1 01` | Collection (Application) | |
| `85 02` | Report ID (2) | |
| `09 01` | Usage (Pointer) | 0x01, CP |
| `A1 00` | Collection (Physical) | |
| `05 09` | Usage Page (Button) | page 0x09 |
| `19 01` | Usage Minimum (Button 1) | primary |
| `29 05` | Usage Maximum (Button 5) | |
| `15 00` | Logical Minimum (0) | |
| `25 01` | Logical Maximum (1) | |
| `95 05` | Report Count (5) | |
| `75 01` | Report Size (1) | |
| `81 02` | Input (Data, Variable, Absolute) | 5 button bits |
| `95 01` | Report Count (1) | |
| `75 03` | Report Size (3) | |
| `81 01` | Input (Constant) | 3 pad bits |
| `05 01` | Usage Page (Generic Desktop) | |
| `09 30` | Usage (X) | |
| `09 31` | Usage (Y) | |
| `16 01 80` | Logical Minimum (−32767) | 0x8001 |
| `26 FF 7F` | Logical Maximum (32767) | 0x7FFF |
| `75 10` | Report Size (16) | |
| `95 02` | Report Count (2) | |
| `81 06` | Input (Data, Variable, Relative) | X then Y, int16 |
| `09 38` | Usage (Wheel) | |
| `15 81` | Logical Minimum (−127) | |
| `25 7F` | Logical Maximum (127) | |
| `75 08` | Report Size (8) | |
| `95 01` | Report Count (1) | |
| `81 06` | Input (Data, Variable, Relative) | vertical wheel, int8 |
| `05 0C` | Usage Page (Consumer) | page 0x0C |
| `0A 38 02` | Usage (AC Pan 0x0238) | 2-byte usage |
| `95 01` | Report Count (1) | size 8 and range −127..127 carry over |
| `81 06` | Input (Data, Variable, Relative) | horizontal pan, int8 |
| `C0` | End Collection | Physical |
| `C0` | End Collection | Application |

### 3.3 Consumer control — Report ID 3

| Bytes | Item | Notes |
|---|---|---|
| `05 0C` | Usage Page (Consumer) | |
| `09 01` | Usage (Consumer Control) | 0x01, CA |
| `A1 01` | Collection (Application) | |
| `85 03` | Report ID (3) | |
| `15 00` | Logical Minimum (0) | |
| `26 FF 03` | Logical Maximum (1023) | |
| `19 00` | Usage Minimum (0) | |
| `2A FF 03` | Usage Maximum (0x03FF) | |
| `75 10` | Report Size (16) | |
| `95 01` | Report Count (1) | |
| `81 00` | Input (Data, Array) | one usage slot |
| `C0` | End Collection | |

The report carries one 16-bit Consumer usage ID rather than one bit per control. Because
Logical Minimum and Usage Minimum are both 0, the value sent equals the usage ID. This lets
mappings use any Consumer usage up to 0x03FF later without changing the descriptor, which
matters because hosts cache descriptors (section 8).

Caveat: HUT 1.7 §3.4 describes one-shot, on/off and re-trigger controls as Variable fields;
only selector usages are described as array members **[SPEC]**. Sending those controls through
an array is widespread in shipping keyboards but is not what the usage-type table prescribes.
Host acceptance is **[UNVERIFIED]**.

## 4. Report layouts

Payloads are what the app passes as `data` to `sendReport(device, id, data)`. They **exclude**
the report ID: the reference says `data` is "Report data, not including Report Id" **[DOC]**,
and the stack prepends the ID on the wire **[AOSP]**. Multi-byte values are little-endian,
least-significant byte at the lowest address (HID 1.11 §5.8) **[SPEC]**.

### 4.1 Report 1 — keyboard input (8 bytes)

| Byte | Content |
|---|---|
| 0 | Modifiers: bit0 LeftControl, bit1 LeftShift, bit2 LeftAlt, bit3 Left GUI, bit4 RightControl, bit5 RightShift, bit6 RightAlt, bit7 Right GUI |
| 1 | Reserved, always 0x00 |
| 2–7 | Up to six key usage IDs from page 0x07; unused slots are 0x00 |

Neutral report: eight zero bytes. More than six simultaneous keys is not supported by the
action model, so ErrorRollOver (0x01) is never sent.

### 4.2 Report 1 — keyboard output (1 byte, host → phone)

| Bits | Content |
|---|---|
| 0–4 | Num Lock, Caps Lock, Scroll Lock, Compose, Kana |
| 5–7 | Padding |

Accepted, logged in diagnostics, otherwise ignored.

### 4.3 Report 2 — mouse (7 bytes)

| Byte | Content | Type | Range |
|---|---|---|---|
| 0 | Buttons: bit0 Button 1 (primary), bit1 Button 2 (secondary), bit2 Button 3 (tertiary), bit3 Button 4, bit4 Button 5; bits 5–7 padding = 0 | bitfield | |
| 1–2 | X | int16 LE, relative | −32767..32767 |
| 3–4 | Y | int16 LE, relative | −32767..32767 |
| 5 | Wheel (vertical) | int8, relative | −127..127 |
| 6 | AC Pan (horizontal) | int8, relative | −127..127 |

- Signed values are two's complement. HID 1.11 §5.8 says values are signed when the logical
  range includes negatives **[SPEC]**; the words "2's complement" did not extract from our copy
  of the PDF and are treated as **[UNVERIFIED]** wording, though universal in practice.
- −32768 and −128 are never sent. No spec rule forbids them; the ranges are kept symmetric
  because the spec uses the most negative value as the out-of-range null value **[SPEC]**.
- Deltas larger than a field's range are split across consecutive reports so total displacement
  is preserved.
- Neutral report: seven zero bytes. With the ID the report is 8 bytes on the wire, inside the
  12-byte single-packet recommendation of BT HID §5.1.7 **[SPEC]**.

Sign conventions (positive wheel = scroll up / away from user; positive pan = right; positive Y
= down) follow common mouse behaviour.

- **macOS 27.2, confirmed by the user (2026-11-11):** +X moved the pointer right and +Y down. A
  wheel value of −3 moved the page up with macOS natural scrolling on, which is what a physical
  wheel rolled towards the user does under natural scrolling. So the wheel sign is right, and
  macOS applies its own natural-scrolling setting to this device. The app's direction setting
  must therefore default to "same as a physical wheel", or the two inversions cancel out.
- Pan sign on macOS, and all signs on Windows and Linux: **[UNVERIFIED]**.

### 4.4 Report 3 — consumer control (2 bytes)

| Byte | Content |
|---|---|
| 0–1 | Consumer usage ID, uint16 LE; `00 00` = nothing pressed |

A press is the usage ID followed later by `00 00`. Usages used by default mappings (HUT 1.7
§15) **[SPEC]**:

| Control | Usage | Bytes |
|---|---|---|
| Scan Next Track | 0x00B5 | `B5 00` |
| Scan Previous Track | 0x00B6 | `B6 00` |
| Play/Pause | 0x00CD | `CD 00` |
| Mute | 0x00E2 | `E2 00` |
| Volume Increment | 0x00E9 | `E9 00` |
| Volume Decrement | 0x00EA | `EA 00` |

### 4.5 Wheel resolution

One wheel count is one detent. HUT 1.7 §4.3.1 says the Resolution Multiplier defaults to 1 when
none is defined **[SPEC]**. The phone accumulates fractional scroll and emits whole counts.

Resolution Multiplier (high-resolution scrolling) is deferred. It needs a Feature report the
host writes with SET_REPORT, and its behaviour over Bluetooth on each host has not been
researched or tested. Adding it later changes the descriptor, so hosts must be re-paired.

## 5. Key usages used by shortcuts

Shortcuts are sent as usage IDs, which identify physical key positions. The host turns them
into characters through its own keyboard layout. Default mappings therefore prefer keys whose
meaning does not depend on layout. From HUT 1.7 §10 **[SPEC]**:

| Key | Usage | Key | Usage |
|---|---|---|---|
| a–z | 0x04–0x1D | F1–F12 | 0x3A–0x45 |
| 1–9, 0 | 0x1E–0x26, 0x27 | Home / End | 0x4A / 0x4D |
| Return | 0x28 | PageUp / PageDown | 0x4B / 0x4E |
| Escape | 0x29 | Right / Left | 0x4F / 0x50 |
| Tab | 0x2B | Down / Up | 0x51 / 0x52 |
| Spacebar | 0x2C | Keypad − / + | 0x56 / 0x57 |
| − and _ | 0x2D | = and + | 0x2E |

Letter and punctuation usages (including `−` and `=` for keyboard zoom) land on different
characters under non-US layouts. This is a documented limitation.

## 6. Protocol modes and boot protocol

A HID device starts in Report protocol; the host may switch it to Boot protocol **[SPEC]**.

Why boot mode is implemented rather than declined:

- BT HID §3.3.2 and §5.3.4.3 require boot protocol for keyboards and pointing devices **[SPEC]**.
- AOSP sets `HIDBootDevice = true` regardless of the subclass the app passes, accepts
  SET_PROTOCOL itself with a SUCCESS handshake, and only then calls `onSetProtocol` **[AOSP]**.
  The app cannot refuse the request or stop the capability being advertised.

Boot-mode reports use fixed IDs: keyboard = 1, mouse = 2 (BT HID Table 3.15) **[SPEC]**. The
report-mode IDs in section 3 were chosen to match, so an ID always means the same device.

| Boot report | ID | Payload |
|---|---|---|
| Keyboard | 1 | 8 bytes, identical to section 4.1 |
| Mouse | 2 | 3 bytes: `[buttons bit0–bit2][X int8][Y int8]` |

### Unavailable in boot mode

- Vertical wheel
- Horizontal pan
- Buttons 4 and 5
- All consumer controls (Report 3 is not sent)
- 16-bit deltas: X and Y are int8 (−127..127), so large movements are split across more reports

Gestures mapped to those outputs do nothing in boot mode. Keyboard shortcuts, movement and
buttons 1–3 keep working. The active protocol mode is shown on the Diagnostics screen and
logged on every `onSetProtocol`.

Whether Windows, macOS or Linux ever select boot mode for a combo device in normal use, and
whether the stack's boot-mode ID prefixing behaves the same on Android 9–14, is **[UNVERIFIED]**.

## 7. Callback behaviour

Callbacks arrive on the executor given to `registerApp`. The stack handles SET_PROTOCOL,
GET_PROTOCOL, SET_IDLE and GET_IDLE itself; GET_REPORT and SET_REPORT are forwarded and the app
must answer **[AOSP]**. The stack sends no handshake for SET_REPORT, so the app replies with
`reportError(device, ERROR_RSP_SUCCESS)` for valid data **[AOSP]** (the reference only mentions
replying to invalid data **[DOC]**).

| Callback | Condition | Response |
|---|---|---|
| `onGetReport` | Input, ID 1 | `replyReport` with current keyboard state (8 bytes) |
| | Input, ID 2 | `replyReport` with held buttons and zero deltas (7 bytes; 3 in boot mode) |
| | Input, ID 3 | `replyReport` with current consumer usage (2 bytes) |
| | Output, ID 1 | `replyReport` with the last LED byte received |
| | Feature, any ID | `reportError(ERROR_RSP_UNSUPPORTED_REQ)` — no feature reports are defined |
| | Unknown ID | `reportError(ERROR_RSP_INVALID_RPT_ID)` |
| `onSetReport` | Output, ID 1, 1 byte | store LED byte, `reportError(ERROR_RSP_SUCCESS)` |
| | Output, ID 1, other length | `reportError(ERROR_RSP_INVALID_PARAM)` |
| | Feature, any ID | `reportError(ERROR_RSP_UNSUPPORTED_REQ)` |
| | Input, or unknown ID | `reportError(ERROR_RSP_INVALID_RPT_ID)` |
| `onSetProtocol` | Boot or Report | Switch encoders; discard pending movement; send neutral reports in the new format; log the mode |
| `onInterruptData` | ID 1, 1 byte | Store LED byte; no reply (interrupt channel) |
| | Anything else | Log and ignore |
| `onVirtualCableUnplug` | | Clear held state and queued output; end the session for that host; **no automatic reconnect**; mark the host as needing re-pairing and prompt the user to remove the pairing on both sides and pair again |
| `onConnectionStateChanged` | | Drives the connection state machine (see ARCHITECTURE.md) |
| `onAppStatusChanged` | | Sole source of truth for registration state |

GET_REPORT for an input report returns "the instantaneous state of the fields" (BT HID
§3.1.2.3) **[SPEC]**; relative fields have no held state, so they are reported as zero.

`onGetReport`'s `bufferSize` is documented as "application shall respond with at least given
number of bytes" **[DOC]**; AOSP passes 0 unless the host set the size-follows bit **[AOSP]**.
The app always replies with the report's exact defined length and logs `bufferSize` in
diagnostics. It does not pad: BT HID says a report whose length does not match the descriptor
is ignored **[SPEC]**, so padding would make the reply invalid. How hosts react when they ask
for a different size is **[UNVERIFIED]**.

Implementation: `HidRequestHandler` (pure, unit-tested) decides each reply;
`HidSessionController` performs it with `replyReport` or `reportError`.

### What `sendReport` does and does not tell us

- A `true` return means the report was handed to the stack, nothing more **[DOC]**.
- If the channel is congested the report is dropped with no signal to the app **[AOSP]**.
- If the host is known but disconnected, the stack holds only the latest report and starts a
  reconnect **[AOSP]**. The app does not rely on this: it stops sending when not connected.

The app never claims a report reached or was processed by the host.

## 8. Descriptor stability

Hosts cache the SDP record and descriptor when pairing. Once a version is released, the
descriptor is treated as frozen. Any change (for example adding Resolution Multiplier) requires
users to remove the pairing on both the phone and the computer and pair again; this is listed
in troubleshooting.

A malformed descriptor is rejected by the native layer, but our reading of `btif_hd.cc` is that
the app may still be told it is registered while no SDP record exists **[AOSP]**
**[UNVERIFIED]**. For that reason the descriptor is validated by unit tests (Milestone 1): a
parser walks the items, checks the length lands exactly on the end, derives each report's size,
and compares it with the encoders.

## 9. Open questions for hardware testing

Answered for macOS 27.2 (MacBook Pro M4 Max) with a Galaxy S25 Ultra on Android 17, confirmed
by the user on 2026-11-11: the descriptor is accepted (macOS lists the device with HID service
and starts its Keyboard Setup Assistant); 16-bit X/Y, buttons 1–3 and the vertical wheel work;
Volume Increment/Decrement through the 16-bit consumer array work, each press changing one step
with no repeat; on connect macOS sends SET_PROTOCOL(Report) and never selected boot mode;
GET_REPORT and SET_REPORT were not observed from macOS.

Still open:

1. Do Windows and Linux (BlueZ) accept this composite descriptor over Bluetooth?
2. AC Pan horizontal scrolling on any host (not yet exercised), and buttons 4–5.
3. Consumer usages other than volume (mute, play/pause, tracks) on any host.
4. Does Windows or Linux switch the device to boot mode, and when?
5. Pan sign on every host; wheel and Y signs on Windows and Linux.
6. Does the "registered but no SDP record" case occur on shipped Android builds?
