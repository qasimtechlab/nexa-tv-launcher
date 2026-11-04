# NEXA Smart TV Launcher — V1.0 starter

Native Android TV home launcher built with Kotlin and classic Android Views. The first release focuses on compatibility with older Android TV boxes and remote-control navigation.

**Creator:** Mirza Qasim  
**Origin:** Faisalabad, Pakistan  
**AI module:** BixSigma AI (interface/identity shell only; no provider is connected in this build)

## Included in this first build

- Android HOME activity plus Leanback launcher entry point.
- Remote-friendly navigation and visible focus states.
- Installed app discovery using LAUNCHER and LEANBACK_LAUNCHER intents.
- App launching with Leanback-first and standard launcher fallback.
- Local wallpaper picker using Android's Storage Access Framework.
- Glass, dark and light appearance modes.
- Accent colour selection and local preference persistence.
- BixSigma AI information screen with creator identity; no fake AI replies and no API key collection.

## Compatibility and testing

Configured for minSdk 21, compileSdk 35, JDK 17, Android Gradle Plugin 8.7.3, Kotlin 2.1.20 and Gradle 8.9. This source has not yet been compiled on a GitHub-hosted Android SDK runner or tested on the target P291 TV box. The first Actions run is the authoritative compilation check.

The first output is a debug APK for testing, not a signed public release. Do not set it as the permanent default Home app until it is tested and the factory launcher recovery path is known.

## Install and test safely

1. Install the APK as a normal app; do not replace or disable the factory launcher.
2. Open NEXA from the TV Apps list.
3. Test LEFT/RIGHT/UP/DOWN/CENTER/BACK navigation.
4. Test opening and returning from several apps.
5. Test choosing and restoring a wallpaper.
6. Test appearance options.
7. Only after the checks pass, choose NEXA as the default Home app.
8. Keep the original launcher enabled so it can be selected again if needed.

## Current limitations

- BixSigma AI is an interface and identity shell only, not connected to a live provider.
- Voice assistant, live AI chat, favourites editing, app sorting and screensaver controls are not implemented yet.
- Blur is represented by translucent glass-style surfaces for older Android compatibility.
- App visibility depends on Android package visibility and installed apps exposing launcher intents.
- Wallpaper picker availability depends on TV firmware.
- NEXA does not disable, uninstall or modify system packages.

## Project structure

- app/src/main/ — Android manifest, Kotlin source and resources.
- app/src/test/ — JVM unit tests.
- .github/workflows/ — cloud build workflows.
