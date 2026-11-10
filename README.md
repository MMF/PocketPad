# Mobile TrackPad

An Android app that turns a phone's touchscreen into a trackpad for a computer. The phone
presents itself as a standard Bluetooth mouse and keyboard, so the computer needs no extra
software: you pair the phone in the computer's Bluetooth settings.

> **Status: early development (Milestone 1 of 6).** The HID descriptor, report encoders and
> output logic exist and are unit-tested. Bluetooth, the trackpad surface and settings are not
> built yet. Nothing has been tested with a real phone and computer.

## What it is and is not

- It is a standard Bluetooth **mouse and keyboard**. Pointer movement, clicks, scrolling and
  media keys use ordinary HID reports that Windows, macOS and Linux understand without drivers.
- It is **not** a native multitouch trackpad. Pinch, rotate and three-finger gestures are
  recognised on the phone and sent as shortcuts (for example Ctrl + wheel for zoom). Whether a
  shortcut does what you expect depends on the computer's OS, the application, and your own
  shortcut settings.
- There is no typing feature. The keyboard report only carries gesture shortcuts.

## Build

Requirements: JDK 17 and the Android SDK with platform 37 and build-tools 37.0.0. Android
Studio users already have the SDK. Gradle is provided by the wrapper.

```sh
./gradlew test               # unit tests
./gradlew spotlessCheck      # ktlint formatting
./gradlew detekt             # static analysis
./gradlew :app:lintDebug     # Android lint
./gradlew :app:assembleDebug # APK in app/build/outputs/apk/debug/
```

The Gradle daemon runs on JDK 17 (see `gradle/gradle-daemon-jvm.properties`). If Gradle reports
that it cannot find a JDK 17, install one or point `org.gradle.java.installations.paths` at it in
`~/.gradle/gradle.properties`.

Install on a connected phone or emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pairing

Pairing instructions for Windows, macOS and Linux will be added with the Bluetooth milestone.
One rule matters already: **enable the trackpad in the app before pairing.** If the computer was
paired with the phone before that, remove the pairing on both the phone and the computer, then
pair again.

## Tested combinations

| Phone | Android | Computer | OS | Result |
|---|---|---|---|---|
| Samsung Galaxy S25 Ultra (SM-S938B) | 17 (API 37), build S938BXXSDDZJ1 | MacBook Pro (M4 Max) | macOS 27.2 (26B123) | Milestone 2 checks passed: pairing after enabling, connect from the phone, pointer movement, left and right click, vertical scroll, volume up and down, held button released on leaving the screen, on Stop, and when the link dropped. Known issue: turning Bluetooth off while enabled needs a phone restart (see below). |

Entries are added only after a person has confirmed the result on real hardware.

## Known limitations

- Shortcuts are sent as physical key positions. The computer applies its own keyboard layout,
  so shortcuts that use letter or punctuation keys can differ on non-US layouts. Defaults favour
  layout-independent keys (modifier + wheel, arrows, Tab, function keys).
- The descriptor is fixed once released because computers cache it when pairing. After an
  update that changes it, remove and redo the pairing.
- **Tap Stop before turning Bluetooth off.** On a Samsung Galaxy S25 Ultra (Android 17),
  turning Bluetooth off while the trackpad is enabled left the phone unable to register the
  trackpad again until it was restarted. If "Enable trackpad" fails with "Registration did not
  complete", restart the phone. Details in
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#15-observed-platform-issues).
- See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/HID.md](docs/HID.md) for design
  details and open questions.

## Privacy

No internet permission, no ads, no analytics. The diagnostics log stays on the phone unless you
share it.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
