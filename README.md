# Velocity Connect

Android BLE starter app for the Vlektra Velocity 180 SE.

## What this build currently does
- Scans for the known BLE device name `DL-D21A05120867`
- Connects to the bike
- Discovers service `FFF0`
- Subscribes to notifications from characteristic `FFF1`
- Displays received values as raw hexadecimal bytes

**Important:** The currently observed `FFF1` data is only raw BLE data (`00` was observed). This project does not decode speed, battery percentage, temperature, or other dashboard values because their data format has not yet been established. It does not send control commands to the bike.

## Build APK with GitHub Actions
1. Upload the complete contents of this ZIP to the root of your GitHub repository.
2. In GitHub, open **Actions** and enable workflows if prompted.
3. Run the workflow named **Build Android APK** (or push a commit to start it).
4. Open the completed workflow run, scroll to **Artifacts**, and download `VelocityConnect-debug-apk`.
5. Extract the APK from the downloaded artifact and install it on your Android phone.

The workflow uses GitHub-hosted runners; no local Android SDK installation is needed.

## BLE permissions
On Android 12+, allow Nearby devices/Bluetooth permissions. On Android 11 and below, allow location permission for BLE scanning.
