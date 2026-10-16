# OffTalk - Native Android Offline Walkie-Talkie

OffTalk is an offline Android P2P walkie-talkie application built with **Jetpack Compose**, **Kotlin**, and **Wi-Fi Direct**.

## Features
- **Zero Network Required**: Works with NO Internet, NO SIM, NO Router, and NO Bluetooth.
- **Wi-Fi Direct Service Discovery**: Uses DNS-SD advertising to discover only phones running OffTalk on the same channel.
- **Push-to-Talk (PTT)**: Half-duplex PCM audio streaming over UDP socket link.
- **Offline GPS SOS Emergency Broadcast**: Transmits real-time offline GPS coordinates to connected peers with an emergency alarm.
- **Background Foreground Service**: Keeps listening/talking even when the screen is turned off.

## How to Build via GitHub Actions
1. Extract the project source zip.
2. Push the files to a new GitHub repository.
3. GitHub Actions will automatically trigger `./gradlew assembleDebug`.
4. Go to **Actions** -> **Build OffTalk Debug APK** -> **Artifacts** to download `app-debug.apk`.
