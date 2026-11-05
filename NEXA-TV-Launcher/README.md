# NEXA Smart TV Launcher — V1.1.1

Native Android TV launcher built with Kotlin and classic Android Views. Designed for older Android TV boxes and remote-control navigation.

**Creator:** Mirza Qasim  
**Origin:** Faisalabad, Pakistan  
**AI assistant:** BixSigma AI, powered by the user's own Gemini API key.

## Included

- Android HOME activity and Leanback launcher entry point.
- Remote-friendly navigation and visible focus states.
- Installed app discovery using LAUNCHER and LEANBACK_LAUNCHER intents.
- App launching with Leanback-first and standard launcher fallback.
- Six built-in offline gradient wallpapers plus a local wallpaper picker.
- Glass, dark and light appearance modes.
- Accent colour selection and local preference persistence.
- BixSigma AI chat using Google's Gemini Interactions API.
- Automatic fallback across Gemini 3.8 Flash, 3.7 Flash, 3.6 Flash and 3.5 Flash-Lite when a model is temporarily overloaded.
- Exponential retry on temporary service errors and rate limits; the active model is remembered for conversation continuity.
- Add, replace, test and remove a personal Gemini API key from the TV interface.
- Gemini key stored in app-private preferences; Android Keystore AES-GCM encryption is used where supported.
- YouTube Home shortcut to open the official YouTube app's signed-in recommendation feed.
- Editable YouTube interest cards on NEXA Home; each opens a relevant YouTube search in the official app when available.
- The app does not claim to fetch private YouTube account recommendations directly.

## Gemini API setup

1. Open BixSigma AI from NEXA navigation.
2. Select **Add Gemini API key**.
3. Create/copy your key from Google AI Studio and paste it on the TV.
4. Select **Test Gemini connection**.
5. Start chatting with BixSigma AI.

The API key is entered on the TV and is not stored in this repository or included in the APK. Requests are sent directly from the TV to Google's Gemini API. The app uses server-side interaction chaining; on Google's free tier, stored interaction objects are retained for one day by default. Free-tier model availability, quotas and data terms are controlled by Google. Do not submit passwords or other sensitive information.

## YouTube

The **My YouTube Home** card launches the official YouTube app so that a signed-in user can see YouTube's own personalized recommendations. The interest cards are local shortcuts to topics selected by the user; they open YouTube search results and do not require a separate YouTube Data API key.

## Compatibility and testing

Configured for minSdk 21, compileSdk 35, JDK 17, Android Gradle Plugin 8.7.3, Kotlin 2.1.20 and Gradle 8.9. Cloud builds run unit tests, Android lint and assemble a debug APK.

A successful build is also copied to `releases/NEXA-TV-Launcher-v1.1.1-debug.apk` in this repository and uploaded as a GitHub Actions artifact. This is a debug APK for testing, not a signed public release. Test on the target P291 TV box before selecting NEXA as the permanent default Home app.

## Safe installation and testing

1. Install the APK as a normal app; do not replace or disable the factory launcher.
2. Open NEXA from the TV Apps list.
3. Test LEFT/RIGHT/UP/DOWN/CENTER/BACK remote navigation.
4. Test opening and returning from several apps.
5. Test Gemini key setup and a short chat using your own API key.
6. Test YouTube Home and interest cards.
7. Test each built-in wallpaper and the custom wallpaper picker.
8. Test appearance options.
9. Only after the checks pass, choose NEXA as the default Home app.
10. Keep the original launcher enabled so it can be selected again if needed.

## Current limitations

- Gemini requests require an internet connection and a valid user-supplied API key.
- The Gemini free tier is subject to Google's model availability, rate limits and terms.
- Voice assistant, microphone input, AI-generated speech, app sorting, favourites editing and screensaver controls are not implemented in this version.
- YouTube's personal recommendations remain inside the official YouTube app; NEXA's cards are topic shortcuts.
- Blur is represented by translucent glass-style surfaces for older Android compatibility.
- App visibility depends on Android package visibility and installed apps exposing launcher intents.
- Wallpaper picker availability depends on TV firmware.
- NEXA does not disable, uninstall or modify system packages.

## Project structure

- app/src/main/ — Android manifest, Kotlin source and resources.
- app/src/test/ — JVM unit tests.
- .github/workflows/ — cloud build workflows.
