# BT Sound Manager

An Android app that monitors Bluetooth connections, keeps a local history of events, and automatically switches the device interruption mode based on the Bluetooth devices you choose.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

## Overview

BT Sound Manager runs entirely on-device. It listens for Bluetooth connection and disconnection events, records them in a local database, and can apply a sound mode (Normal, Do Not Disturb, Alarms only or Silent) while your selected devices are connected or disconnected within a schedule.

The app performs no network requests and declares no `INTERNET` or `ACCESS_NETWORK_STATE` permissions. There are no ads, analytics or in-app purchases.

## Features

### Bluetooth monitoring and notifications

- Notifies every connection and disconnection, for any device, with no per-device filter.
- Uses independent notification channels for connected and disconnected devices.
- Hides the disconnection notification when a device connects, and vice versa.
- Shows an informative notification when Bluetooth is turned on.
- When Bluetooth is turned off, hides the connection notification and shows an actionable notification to enable it.
- Resolves device names from the latest saved name, the event name or the address.

### Event history

- Groups events by day with Today/Yesterday labels.
- Filters by event type and by device.
- Deletes visible events, a single day or the full history.
- Keeps events for a configurable retention period (3 to 90 days, 7 by default), measured from local midnight.
- Hides connection/disconnection pairs separated by less than one second.
- Refreshes paired and connected device names when the app returns to the foreground.

### Sound-mode automation

- Enable or disable automation.
- All-day schedule or start/end times, including ranges that cross midnight.
- Apply to all Bluetooth devices or to a selected set.
- Configure the mode used when at least one selected device is connected and the mode used when none is connected.
- Force the selected mode if it is changed manually.
- Save the media volume before changing the mode and restore it afterwards.
- Re-evaluates on boot (after 15 seconds), app update, app start, user change, interruption-filter change, schedule start and Bluetooth connection/disconnection.

### Reliability and background behavior

- Foreground service (`specialUse`) for continuous, reliable monitoring.
- Manifest receiver processes the broadcasts the system delivers when the service is not running.
- WorkManager fallback applies automation with a minimum 3-second delay when the receiver handles the event and the service is not active.
- `AlarmManager` schedules the start of the automation window, with an inexact fallback when exact alarms are not permitted.
- Reactivation when the user returns, including a best-effort Bluetooth enable and an actionable notification when the system requires it.

### UI and accessibility

- Jetpack Compose with Material Design 3.
- Bottom navigation on phones and a navigation rail on wide screens.
- System, light and dark themes.
- Material You dynamic color on Android 12 and later.
- Content descriptions, 48 dp touch targets, dynamic font scaling and RTL support.

## How it works

Connection changes are captured from Bluetooth ACL broadcasts and combined with `BluetoothAdapter.getProfileProxy` / `BluetoothManager.getConnectedDevices` queries for classic, BLE, hearing-aid and LE Audio profiles. Events are stored in Room, settings in DataStore, and automation is coordinated through injected use cases.

The interruption mode is applied with `NotificationManager.setInterruptionFilter`, and media volume is saved and restored through the audio manager. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full design.

## Requirements

- JDK 21
- Android SDK 37
- A `local.properties` file with `sdk.dir` pointing to your Android SDK

## Build and run

```bash
git clone https://github.com/ryosoftware/bt-sound-manager.git
cd bt-sound-manager
# create local.properties and set sdk.dir=/path/to/Android/Sdk
./gradlew assembleDebug
./gradlew installDebug
```

Run the checks:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew assembleRelease
```

## Permissions

| Permission | Purpose |
| --- | --- |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (Android 11 and lower) | Read adapter state and device connections |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Detect nearby device connections without location access |
| `BLUETOOTH_CONNECT` | Read connected devices and their names |
| `POST_NOTIFICATIONS` | Connection, disconnection and automation notifications |
| `RECEIVE_BOOT_COMPLETED` | Restore monitoring after a reboot |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Reliable background monitoring |
| `ACCESS_NOTIFICATION_POLICY` | Change the interruption filter |
| `SCHEDULE_EXACT_ALARM` | Start the automation schedule on time |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Offer an optional battery optimization exemption |
| `MODIFY_AUDIO_SETTINGS` | Save and restore media volume |
| `VIBRATE` | Notification vibration |

`INTERNET` and `ACCESS_NETWORK_STATE` are explicitly removed from the merged manifest.

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Dependency injection | Hilt 2.60.1 |
| Persistence | Room 2.8.5, DataStore Preferences 1.3.0 |
| Background work | WorkManager 2.12.0, AlarmManager |
| Async | Coroutines and StateFlow |
| Build | Android Gradle Plugin 9.5.1, Compose BOM 2026.09.00 |

`minSdk` is 26 (Android 8.0); `compileSdk` and `targetSdk` are 37.

## Project structure

```text
com.ryosoftware.bt_sound_manager
├── core/          # time and shared utilities
├── domain/        # models, repository contracts and pure use cases
├── data/          # Room, DataStore, Bluetooth, notifications and repositories
├── presentation/  # Compose screens, navigation, theme and ViewModels
├── service/       # foreground service, workers and automation coordinator
├── receiver/      # manifest receivers and notification actions
└── di/            # Hilt modules
```

Only the `:app` module exists; layers are separated inside it.

## Testing

- 50 unit tests covering automation planning, schedule windows, retention, event minimization, device merging, broadcast gating and ViewModel logic.
- 3 instrumented tests covering navigation, event rendering and Room persistence.
- Lint completes with no errors.

See [docs/TESTING.md](docs/TESTING.md) for the full strategy and the checks that require a physical device.

## Privacy

- No network requests and no `INTERNET` permission.
- No advertising, analytics or purchases.
- No third-party data sharing.
- Bluetooth events and settings are stored locally in Room and DataStore.

## Certificate Signature Verification

The SHA-256 digest of the certificate used to sign the app is as follows, and remains constant regardless of the version:

`0d1320cf211b15d6eafae535446673b5f4b952c0080972837a8764330fbf2a55`

The app signature certification can be checked by the following command:

apksigner verify --verbose --print-certs app-release.apk | grep "Signer #1 certificate SHA-256 digest"

## Known limitations

- Silent Bluetooth reactivation after a user change depends on Android and OEM restrictions; otherwise an actionable notification is shown.
- When the receiver delegates to WorkManager, the mode change may run slightly after the 3-second delay, depending on system constraints.
- Exact alarms and Do Not Disturb access depend on user configuration.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Features](docs/FEATURES.md)
- [Testing](docs/TESTING.md)
- [Agent and contributor guidelines](AGENTS.md)

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the full text.

## Contributing

Bug reports and pull requests are welcome through the [issue tracker](https://github.com/ryosoftware/bt-sound-manager/issues). Please keep changes within the existing layered architecture, do not add network, analytics or advertising dependencies, and run the build and tests before opening a pull request.
