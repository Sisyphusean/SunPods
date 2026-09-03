# Sony Pods Battery

A small, private Android battery companion for the Sony WF-1000XM5.

## What the MVP does

* Reads left, right, and case battery levels directly from the earbuds
* Shows the last result in the app, a low-priority notification, and a home-screen widget
* Refreshes manually, from the widget, or when Android reports that the earbuds connected
* Opens Sony's RFCOMM control channel only for a refresh and always closes it afterward
* Stores only battery state and a short sanitized diagnostic log on the phone

There is no internet permission, account, advertising, analytics SDK, or persistent background service.

## Install and test

1. Pair the WF-1000XM5 in Android Bluetooth settings.
2. Install the APK and allow Nearby devices. Notification permission is optional.
3. Make sure Sony Sound Connect is not currently reading the earbuds, then tap **Refresh battery**.
4. Open the charging case if the case level says **Not available**. Sony normally reports case battery only while the case is awake.
5. If a refresh fails, use **Share diagnostics** in the app and send the text with the phone model and Android version.

Do not run this app and Sony Sound Connect at the exact same moment. Sony exposes one private control connection, so whichever app owns it first can temporarily block the other. This app releases that connection immediately after every attempt.

## Build

Use JDK 17 and Android SDK 35:

```sh
./gradlew test assembleDebug
```

## Protocol note

This clean implementation uses the Sony MDR packet framing and battery queries documented by open source interoperability projects. The relevant commands are `22 09` for both earbuds and `22 0A` for the case. Protocol behavior was cross-checked against SonyBridge and Gadgetbridge. SonyBridge is MIT licensed and Gadgetbridge is AGPL-3.0-or-later. No source code from either project is included here.

Sony and WF-1000XM5 are trademarks of Sony Group Corporation. This project is independent and is not endorsed by Sony.
