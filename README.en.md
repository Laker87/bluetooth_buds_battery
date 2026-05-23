# Bluetooth Buds Battery

[Russian version](README.md)

Android app for monitoring Bluetooth headphone battery levels. It shows either a single battery level or separate `Left / Right / Case` levels when the headset exposes that data.

## Features

- Current battery level for connected Bluetooth headphones.
- Single `Battery` mode and separate `Left / Right / Case` mode.
- Background monitoring for connection and battery updates.
- Compact and expanded notification with circular battery indicators.
- Home screen widget styled like the expanded notification.
- Previously connected headphones history with the last known battery level.
- First-run setup with step-by-step permission requests.
- Theme, language, and accent color settings.
- GitHub Actions workflow for release builds and GitHub Releases publishing.

## Screenshots

| Main screen | Expanded Notification | Compact Notification |
| --- | --- | --- |
| ![Main screen](store-assets/screenshots/ru/Bluetooth_Buds_Battery1.jpg) | ![Expanded notification](store-assets/screenshots/ru/Bluetooth_Buds_Battery6.jpg) | ![Compact notification](store-assets/screenshots/ru/Bluetooth_Buds_Battery4.jpg) |

## Project Info

- App name: `Bluetooth Buds Battery`
- Application ID: `com.laker.btbudsbattery`
- Minimum Android version: `minSdk = 31` / Android 12+
- Target Android version: `targetSdk = 36`
- JDK: `17`
- Current version: `1.1`

## Battery Data Sources

The app combines several data sources:

- standard Bluetooth profiles and system battery level;
- BLE advertisement/service data for supported TWS models;
- Apple Continuity payload for AirPods and Beats;
- vendor events and Bluetooth audio device callbacks where available.

Separate `Left / Right / Case` support depends on the headset model and on the data exposed to Android. If separate values are not available, the app falls back to a single battery level.

## Supported Models

Verified or targeted support:

- Realme Buds T310: `Left / Right / Case`.
- Apple AirPods 1 / 2 / 3.
- Apple AirPods Pro / Pro 2 / Pro 3.
- Apple AirPods Max.
- Beats X, Beats Flex, Beats Solo 3, Beats Studio 3, Beats Solo Pro, Beats Studio Pro, Beats Solo 4.
- Beats Fit Pro, Beats Studio Buds, Beats Studio Buds+, Beats Solo Buds.

Apple/Beats values are often coarse, for example in 10% steps, because that is how the public BLE payload exposes them.

## Adding a New Model

If your headphones show correct battery levels in Android Bluetooth settings but this app does not show `Left / Right / Case`, useful diagnostics are:

- headphone model;
- Android version and phone model;
- screenshot of the Android Bluetooth device screen with correct battery values;
- `Bluetooth HCI Snoop Log`, if available;
- app logs with `BtBatteryRepo`, `BluetoothBatteryService`, and `BluetoothConnectionReceiver` tags.

TWS parsers are modular:

- common contract: `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/TwsBatteryAdvertisementParser.kt`
- registry: `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/TwsBatteryParserRegistry.kt`
- model example: `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/realme/RealmeT310FastPairParser.kt`

## Permissions

Runtime permissions:

- `BLUETOOTH_CONNECT` — access connected Bluetooth devices.
- `BLUETOOTH_SCAN` — read Bluetooth/BLE data.
- `POST_NOTIFICATIONS` — show the monitoring notification on Android 13+.
- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` — Android 11 and below only, where Android ties Bluetooth scanning to location permissions.

Manifest-only permissions:

- `BLUETOOTH` and `BLUETOOTH_ADMIN` — Android 11 and below only.
- `FOREGROUND_SERVICE`.
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`.

`BLUETOOTH_SCAN` is declared with `android:usesPermissionFlags="neverForLocation"`.

## Build

Debug:

```powershell
.\gradlew.bat assembleDebug
```

Release:

```powershell
.\gradlew.bat assembleRelease
```

Release APK:

```text
app/build/outputs/apk/release/app-release.apk
```

The release build uses R8 and resource shrinking.

```

## Limitations

- Not all Bluetooth headphones expose separate `Left / Right / Case` values.
- Some models update battery values with delay or in coarse steps.
- Background service and notification behavior may vary across Android vendor skins.
- The app does not read Google Fast Pair notifications.

## License

This project is distributed under the **GNU General Public License v3.0**.
