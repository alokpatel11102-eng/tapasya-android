# तपस्या (Tapasya) — Android Study Focus

A native Kotlin Android application that runs a fixed-duration study session, returns the user to Home when a selected app is opened, and keeps the session deadline in persistent storage.

## Requirements

- Android Studio (current stable release)
- JDK 17 or newer
- Android SDK Platform 35 and Android SDK Build-Tools installed in SDK Manager
- Internet access for the first Gradle sync
- An Android device or emulator running Android 8.0 / API 26 or newer

## Open and build

1. Extract `Tapasya-Android-Project.zip`, if using the supplied archive.
2. In Android Studio, choose **Open** and select the extracted `Tapasya-Android-Project` directory (the directory containing `settings.gradle.kts`).
3. Allow Gradle sync to download the Android Gradle Plugin and dependencies.
4. In **Tools → SDK Manager**, install **Android SDK Platform 35** and the matching Build-Tools.
5. Build with Android Studio's **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or from a terminal in the project directory:

   ```sh
   ./gradlew assembleDebug
   ```

6. The debug APK is created at:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

The APK is a debug build signed with Android's debug key. It is for local installation and testing.

## Install on a device

- Enable Developer options and USB debugging, connect the phone, then use Android Studio's **Run** button; or
- Copy `app-debug.apk` to the device and open it. If prompted, permit installation from that source.

The project uses application ID `com.tapasya.focus`.

## First-time setup

1. Open **तपस्या**.
2. Tap **Accessibility Settings खोलें**.
3. Find **तपस्या App ब्लॉकर** in Android's Accessibility settings and turn it on. Android requires the person using the phone to grant this permission manually; the app never enables it programmatically.
4. Return to Tapasya. The screen confirms when the service is enabled.
5. Choose a preset or enter a custom duration from 10 to 180 minutes.
6. Open **Apps चुनें**, search or scroll the installed launchable apps, check one or more apps, and tap **चयन सेव करें**.
7. Tap **तपस्या शुरू करें**. Android 13 and newer ask separately for notification permission. If it is declined, the study session can still run; Android may display the foreground-service status in its Task Manager rather than the notification drawer.

Instagram, YouTube, and Facebook are selected by default when installed and available as launchable apps. The user can select or clear any listed app before starting.

## Study session behavior

- A session stores `active`, `startTime`, `endTime`, and the package names selected for that session before starting its foreground service.
- The persisted `endTime` is the source of truth. The timer always calculates `endTime - System.currentTimeMillis()`; reopening the app does not restart or extend the timer.
- While active, the app hides duration, app-selection, language, and start controls. There is no in-app pause, break, skip, cancel, stop, or unlock action.
- The foreground service updates the ongoing **Tapasya Study Mode** notification and completes the session at the stored deadline.
- The Accessibility service watches window changes. If a selected app becomes foreground, it performs Android's Home global action once for that app entry and displays “तपस्या चालू है — पहले पढ़ाई पूरी करें।” It does not repeatedly launch activities.
- At the deadline, Tapasya saves completion time and duration, marks the session inactive, clears active session data, and removes the foreground notification. The app shows **तपस्या पूरी हुई!** and the total study time when opened.
- The app uses Hindi by default and includes an English / Hindi toggle outside an active session.

## Android platform limits

Android does not provide ordinary apps a tamper-proof way to lock another app. Tapasya uses the user-granted Accessibility service and a foreground timer, but Android's system controls remain authoritative. A user can disable Accessibility, force-stop or uninstall Tapasya, revoke permissions, or use device/OEM controls to stop background work. Some manufacturers also apply aggressive battery restrictions. These actions can interrupt blocking or timer updates; the app cannot prevent or conceal them. This project intentionally does not claim kiosk-grade or unbypassable enforcement.

The dialer, emergency/safety apps, Android system UI, permission controllers, Settings, and Tapasya itself are excluded from blocking.

## Troubleshooting

- **Start says Accessibility is disabled:** return to Android Accessibility settings and enable **तपस्या App ब्लॉकर**. Go back to Tapasya to refresh its status.
- **A selected app is not blocked:** confirm the service remains enabled. Some OEMs require allowing Tapasya to run in the background or removing it from battery optimization.
- **The notification is missing on Android 13+:** allow notifications for Tapasya in Android app settings. Denying notification permission does not stop or unlock a study session; Android may show foreground-service status in its Task Manager instead of the notification drawer.
- **The timer appears to jump after reopening:** it is recalculated from the saved system-clock end time, not from an in-memory countdown. Manual changes to the device clock can affect wall-clock-based deadlines.
- **Gradle reports that the SDK is missing:** install Android SDK Platform 35 and Build-Tools from Android Studio's SDK Manager, then sync again.
- **A device stops the service in the background:** allow Tapasya background activity in that device's battery / app-launch settings. OEM controls vary.

## Project layout

- `app/src/main/java/com/tapasya/focus/` — activities, app repository, persistent session manager, Accessibility service, notification helper, and foreground timer service.
- `app/src/main/res/` — Hindi and English strings, dark theme, layouts, app icon, and Accessibility service configuration.
- `app/src/main/AndroidManifest.xml` — launcher activity, package visibility, notification and foreground-service permissions, Accessibility and timer service declarations.
