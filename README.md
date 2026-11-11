# Mobile TrackPad

An Android app that turns a phone's touchscreen into a trackpad for a computer. The phone
presents itself as a standard Bluetooth mouse and keyboard, so the computer needs no extra
software: you pair the phone in the computer's Bluetooth settings.

> **Status: Milestones 1–5 built.** Bluetooth sessions, the trackpad, shortcut gestures, host
> profiles, settings and onboarding exist. They have been confirmed on one phone and one Mac
> (see [Tested combinations](#tested-combinations)). Windows and Linux computers, and other
> phones, have not been tested yet.

## What it is and is not

- It is a standard Bluetooth **mouse and keyboard**. Pointer movement, clicks, scrolling and
  media keys use ordinary HID reports that Windows, macOS and Linux understand without drivers.
- It is **not** a native multitouch trackpad. Pinch, rotate and three-finger gestures are
  recognised on the phone and sent as shortcuts (for example Ctrl + wheel for zoom). Whether a
  shortcut does what you expect depends on the computer's OS, the application, and your own
  shortcut settings.
- There is no typing feature. The keyboard report only carries gesture shortcuts.

## Gestures

| Gesture | Sends |
|---|---|
| Move one finger | Pointer movement |
| Tap | Left click |
| Tap, then touch again and move | Drag with the left button (optional drag lock keeps it held until the next tap) |
| Two-finger tap | Right click |
| Three-finger tap | Middle click |
| Two-finger move | Vertical and horizontal scrolling, with optional momentum |
| Pinch, rotate, three-finger swipe | The shortcut chosen in the computer's profile (below) |

Optional on-screen left and right buttons can be turned on in Settings (swapped in the
left-handed layout).

## Gesture mappings by profile

Choose a profile for each paired computer on the main screen ("Profile: …" under the computer).
Every mapping can be changed in **Settings → Gesture shortcuts**, including to a mouse button, a
keyboard shortcut, a media key, modifier + wheel, or a short sequence. Pinch and rotate repeat
their shortcut for every step of movement (25% spread, 30°).

| Gesture | Windows | macOS | Linux (GNOME) | Linux (KDE Plasma) | Custom |
|---|---|---|---|---|---|
| Pinch out | Ctrl + wheel up | Cmd + = | Ctrl + wheel up | Ctrl + wheel up | Ctrl + wheel up |
| Pinch in | Ctrl + wheel down | Cmd + - | Ctrl + wheel down | Ctrl + wheel down | Ctrl + wheel down |
| Rotate | — | — | — | — | — |
| Three-finger swipe up | Win + Tab (Task View) | Ctrl + Up (Mission Control) | Super + S (Activities) | Meta + W (Overview) | — |
| Three-finger swipe down | Win + D (desktop) | Ctrl + Down (App Exposé) | — | Meta + D (peek at desktop) | — |
| Three-finger swipe left | Win + Ctrl + Right (next desktop) | Ctrl + Right (next space) | Super + Page Down (next workspace) | Meta + Ctrl + Right (next desktop) | Alt + Right (forward) |
| Three-finger swipe right | Win + Ctrl + Left | Ctrl + Left | Super + Page Up | Meta + Ctrl + Left | Alt + Left (back) |

Assumptions and conflicts:

- Swipe directions follow "content moves with your fingers": swiping left reveals what is to
  the right.
- These are the hosts' **default** shortcuts. If you changed them, or an application uses the
  same keys for something else, the gesture does whatever the computer maps those keys to.
- **macOS zoom uses Cmd + = / Cmd + -** because Cmd + scroll wheel is not application zoom on
  macOS (it is unassigned by default, or Accessibility screen zoom if you enabled it). Screen
  zoom and application zoom are different features.
- Ctrl + wheel zooms in most browsers and many editors on Windows and Linux, but not everywhere.
- Mission Control and spaces shortcuts on macOS, and workspace shortcuts on GNOME and KDE, can be
  changed or disabled in system settings; the defaults above assume they are on.
- None of these defaults has been verified on every OS version. Results from real computers are
  recorded in [Tested combinations](#tested-combinations) only after confirmation.

### Keyboard layouts

Shortcuts are sent as **physical key positions** (HID usages), and the computer turns them into
characters with its own keyboard layout. Arrows, Tab, Page Up/Down, function keys and modifier +
wheel behave the same on every layout. Letters and punctuation do not: on a French AZERTY
layout, for example, the key sent for "=" produces a different character, so Cmd + = may not
zoom. Prefer layout-independent shortcuts when you edit a profile.

## Settings

Pointer speed, extra speed for fast moves (off by default, because computers accelerate the
pointer themselves), scroll speed, reverse scroll direction, scroll momentum, tap to click, drag
lock, vibrate on click, on-screen buttons, left-handed layout, and battery saving. **Reset
settings to defaults** restores all of them; gesture shortcuts and paired computers are kept.

Scroll direction follows the computer's own setting by default: fingers moving up send "wheel
up", and the computer applies its natural-scrolling preference on top. Turn on **Reverse scroll
direction** only if scrolling feels backwards.

**Battery saving** dims the trackpad screen, turns off click vibration, asks Android for a
standard refresh rate on the trackpad screen, and sends about 90 instead of 125 reports per
second while you move. Independently of this setting, the app sends reports only when input
changes and keeps the screen on only while the trackpad screen is showing and connected. No
battery saving has been measured yet.

## Build

Requirements: JDK 17 and the Android SDK with platform 37 and build-tools 37.0.0. Android
Studio users already have the SDK. Gradle is provided by the wrapper.

```sh
./gradlew test               # unit tests
./gradlew spotlessCheck      # ktlint formatting
./gradlew detekt             # static analysis
./gradlew :app:lintDebug     # Android lint
./gradlew :app:assembleDebug # APK in app/build/outputs/apk/debug/
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest  # instrumented tests
```

Instrumented tests run against a debug-only simulated computer and need an emulator or phone.
They run on **every attached device** and uninstall the app afterwards, so set
`ANDROID_SERIAL` when a real phone is also connected.

The Gradle daemon runs on JDK 17 (see `gradle/gradle-daemon-jvm.properties`). If Gradle reports
that it cannot find a JDK 17, install one or point `org.gradle.java.installations.paths` at it in
`~/.gradle/gradle.properties`.

Install on a connected phone or emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pairing

**Order matters.** Computers remember what a Bluetooth device offered when they paired. If the
phone was paired before Mobile TrackPad was enabled, the computer may not know it is a mouse.

On the phone:

1. Open Mobile TrackPad and tap **Enable trackpad**. Wait for "Ready. Waiting for a computer."
2. Tap **Make phone visible for pairing** and allow it (the phone stays visible for 2 minutes).

On the computer:

- **macOS:** System Settings → Bluetooth. Under Nearby Devices, click **Connect** next to the
  phone and confirm the code on both devices. macOS may open the Keyboard Setup Assistant;
  close it (the phone has no physical keys to identify).
- **Windows 11:** Settings → Bluetooth & devices → **Add device** → Bluetooth. Choose the phone
  and confirm the code on both devices.
- **Linux (GNOME):** Settings → Bluetooth. Choose the phone in the list and confirm the code.
  **KDE Plasma:** System Settings → Bluetooth → **Pair Device**.

Then, back on the phone, choose a **profile** for the computer and tap **Connect**. Next time,
tap **Enable trackpad** and **Connect**; the computer does not reconnect on its own (observed
on macOS). If the connection drops while you are using it, the app tries to reconnect three
times (after 2, 5 and 10 seconds); you can stop that from the main screen.

The Windows and Linux steps follow those systems' standard Bluetooth settings and have not yet
been tried with this app.

## Tested combinations

| Phone | Android | Computer | OS | Result |
|---|---|---|---|---|
| Samsung Galaxy S25 Ultra (SM-S938B) | 17 (API 37), build S938BXXSDDZJ1 | MacBook Pro (M4 Max) | macOS 27.2 (26B123) | **Milestone 2:** pairing after enabling, connect from the phone, test movement, left and right click, vertical scroll, volume up and down, held button released on leaving the screen, on Stop, and when the link dropped. **Milestones 3–5 (macOS profile):** pointer movement (smooth, speed about right, no touches wrongly ignored), tap, double-tap, tap-and-drag (title bars and icons), resting finger does not click, two-finger tap right click, three-finger tap middle click, two-finger scroll vertical and horizontal with momentum, pinch zoom in Safari (Cmd + = / Cmd + -), three-finger swipes (Mission Control, App Exposé, next and previous space), pointer speed, reverse scroll and tap to click settings applied and kept after restart, drag lock, on-screen buttons, automatic reconnect after the Mac's Bluetooth was turned off and on, battery saving (dim, no vibration), and button released when leaving the app mid-drag. Not tested on this pair: rotate, other settings (scroll speed, left-handed layout, extra speed), other profiles. Known issue: turning Bluetooth off on the phone while enabled needs a phone restart (see below). |

Entries are added only after a person has confirmed the result on real hardware.

## Troubleshooting

| Symptom | What to do |
|---|---|
| The computer pairs but nothing moves, or it shows the phone only as a phone | It was probably paired before the trackpad was enabled. Remove the pairing on **both** the phone and the computer, tap Enable trackpad, then pair again. |
| "Registration did not complete" | Restart the phone. On some phones (seen on a Galaxy S25 Ultra) turning Bluetooth off while the trackpad is enabled leaves Bluetooth unable to register it again until a restart. Tap **Stop** before turning Bluetooth off. |
| "Registration rejected" | Another app may be acting as a Bluetooth keyboard or mouse; close it and try again. |
| "Bluetooth HID device mode unavailable" | Android did not provide its HID device service. Turn Bluetooth off and on, then try again; some manufacturers disable this feature. |
| "Computer removed the trackpad" | The computer ended the pairing. Remove it on the phone too, then pair again. |
| A button or key seems stuck on the computer | Click once with the computer's own mouse or trackpad. The app releases everything when the trackpad screen closes, on Stop and on disconnect, but cannot send anything after Android's Task Manager stops it. |
| Pinch zoom or swipes do nothing | Check the computer's profile on the main screen and its shortcuts in Settings → Gesture shortcuts. In boot mode (shown on the Test input and Diagnostics screens) scrolling, media keys and buttons 4–5 are unavailable. |
| After an app update that changes the HID descriptor | Remove and redo the pairing; computers cache the descriptor. |

**Diagnostics:** Diagnostics → Record diagnostics, reproduce the problem, then **Copy** or
**Share**. The log contains Bluetooth events, recognised gestures and every report sent (in
hex). It never leaves the phone unless you share it.

## Known limitations

- Shortcuts depend on the computer's keyboard layout (see [Keyboard layouts](#keyboard-layouts)).
- The descriptor is fixed once released because computers cache it when pairing. After an
  update that changes it, remove and redo the pairing.
- **Tap Stop before turning Bluetooth off.** Details in
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#15-observed-platform-issues).
- If Android's Task Manager stops the app while the trackpad is on, the app gets no warning and
  cannot release held input; on Android 11 and later it tells you about it the next time it
  opens.
- Palm rejection is best effort: touches Android marks as accidental are dropped, and a contact
  the phone reports as very large when it lands is ignored. What "large" means varies by phone.
  There is no edge rejection yet. TalkBack's
  explore-by-touch takes over touches on the trackpad surface, so the trackpad itself is not
  usable with TalkBack running; the other screens are labelled for it.
- See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/HID.md](docs/HID.md) for design
  details and open questions.

## Privacy

No internet permission, no ads, no analytics. The diagnostics log stays on the phone unless you
share it.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
