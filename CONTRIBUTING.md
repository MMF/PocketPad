# Contributing

Thanks for helping. Please read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and
[docs/HID.md](docs/HID.md) first.

## Before opening a pull request

Run the same checks as CI:

```sh
./gradlew spotlessCheck detekt test :app:lintDebug :app:assembleDebug
```

`./gradlew spotlessApply` fixes formatting.

## Ground rules

- **`gesture/` and `hid/` stay pure Kotlin** with no Android imports. Put platform code in `app/`.
- **The HID descriptor is frozen once released.** Hosts cache it when pairing, so changing it
  forces every user to re-pair. Discuss descriptor changes in an issue first.
- **Cite sources** for HID or Bluetooth behaviour (spec section, Android reference page, or AOSP
  file). Mark anything unconfirmed as unverified.
- **Do not claim hardware results you have not observed.** Bug reports and compatibility entries
  should name the phone, Android version, computer, OS and what was actually seen. A copy of the
  in-app diagnostics log helps.
- All user-facing text goes in Android string resources, with accessible labels.
- No network access, ads or analytics.

## Reporting a compatibility result

Include: phone model, Android version, computer model, OS and version, what you tested, what
happened, and the diagnostics log if available.
