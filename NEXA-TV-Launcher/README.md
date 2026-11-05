# NEXA Smart TV Launcher — V1.2.0

Native Android TV launcher built with Kotlin and classic Android Views. Designed for older Android TV boxes and remote-control navigation.

**Creator:** Mirza Qasim  
**Origin:** Faisalabad, Pakistan  
**AI assistant:** BixSigma AI, powered by the user's own Gemini API key.

## Home screen and apps

- Clean Home layout that can hide the large introductory panels and make the wallpaper the focus.
- Home screen Favorites row shows only apps explicitly added by the user.
- Long-press the remote's OK/Center button on an app to open the styled application options menu.
- App options include open, add/remove Favorites, move up/down in the library, and request uninstall through Android's system confirmation.
- Full installed-app library supports Grid and line-wise Rows layouts.
- Manual app ordering is shared by the app library and Favorites row.
- Live clock and date widget on Home. Drag it with a pointer/touch input or move it with LEFT/RIGHT on a remote; CENTER recenters it.

## YouTube video cards

- Category-based public video discovery using YouTube Data API v3.
- Real video thumbnails, titles and channel names displayed in horizontal TV-friendly cards.
- Selecting a card opens that exact video in the installed official YouTube app when available.
- Edit up to six topics such as Gaming, AI & Technology, Education or Science.
- NEXA gives a topic higher local priority when the user opens a video from it. This is a lightweight on-device interest score, not a copy of YouTube's private recommendation model.
- **My YouTube Home** still opens the official YouTube app so the signed-in user can access YouTube's own account-based feed.

### YouTube API key setup

The video cards need a separate Google Cloud API key; a Gemini API key cannot be used for YouTube Data API requests.

1. Open [Google Cloud Credentials](https://console.cloud.google.com/apis/credentials).
2. Select or create a project.
3. Enable **YouTube Data API v3** in the API Library.
4. Create an API key and restrict it to YouTube Data API v3.
5. In NEXA, open **YouTube → Connect YouTube video feed** and paste the key.

NEXA requests only public video search results and thumbnails. Public video metadata can use an API key; private account data requires OAuth authorization. The official YouTube app remains the place for private, signed-in recommendations. Google's API quota and key restrictions apply. See [YouTube API authentication](https://developers.google.com/youtube/data/guides/authentication) and [quota information](https://developers.google.com/youtube/data/determine_quota_cost).

## Wallpapers and widgets

- Six built-in offline gradient wallpapers plus a local image picker for gaming, space, nature or personal wallpapers.
- Wallpaper framing controls: Fill Screen, Fit Full Image or Center.
- Adjustable wallpaper overlay strength to balance image visibility and card readability.
- Glass, Dark and Light appearance modes plus accent colour selection.
- Clean Home mode hides extra panels for a minimal wallpaper-first screen.
- **Futuristic Widgets** settings offer an offline animated Rotating Earth globe or Orbital Reactor, with side/center placement and an off switch.
- The clock widget can be repositioned on the Home strip; its position is stored locally.

## BixSigma AI

- Chat using Google's Gemini Interactions API.
- Add, replace, test and remove a personal Gemini API key from the TV interface.
- Automatic fallback across supported Gemini Flash choices when a model is temporarily overloaded.
- Gemini key stored in app-private preferences; Android Keystore AES-GCM encryption is used where supported.
- The key is entered on the TV and is not stored in this repository or included in the APK.
- Gemini requests require an internet connection. Free-tier model availability, quotas and data terms are controlled by Google. Do not submit passwords or other sensitive information.

## Compatibility and testing

Configured for minSdk 21, compileSdk 35, JDK 17, Android Gradle Plugin 8.7.3, Kotlin 2.1.20 and Gradle 8.9. Cloud builds run unit tests, Android lint and assemble a debug APK.

The generated test APK is copied to:
`releases/NEXA-TV-Launcher-v1.2.0-debug.apk`

It is also uploaded as the `NEXA-TV-Launcher-debug-APK` artifact in GitHub Actions. This is a debug APK for testing, not a signed public release.

## Safe installation and testing

1. Install the APK as a normal app; do not replace or disable the factory launcher.
2. Open NEXA from the TV Apps list.
3. Test LEFT/RIGHT/UP/DOWN/CENTER/BACK remote navigation.
4. Test opening and returning from several apps.
5. Test long-press OK on an app, Favorites, moving an app and uninstall confirmation.
6. Test YouTube API key setup, categories, thumbnails and video launching.
7. Test built-in wallpapers, custom wallpaper selection, framing and overlay controls.
8. Test the draggable clock and each futuristic widget.
9. Only after the checks pass, choose NEXA as the default Home app.
10. Keep the original launcher enabled so it can be selected again if needed.

## Current limitations

- YouTube topic ranking uses local interest selections and card opens; it does not access private watch history or reproduce YouTube's account-level recommendation algorithm.
- Public YouTube video cards require the user's own YouTube Data API v3 key and remain subject to Google's quota and regional/API restrictions.
- Gemini voice input, microphone activation, AI-generated speech and screensaver controls are not implemented.
- System apps protected by the TV firmware may not be uninstallable through Android's normal package installer.
- Wallpaper picker availability depends on TV firmware and available document-provider apps.
- Blur is represented by translucent glass-style surfaces for older Android compatibility.
- NEXA does not disable, uninstall or modify factory launcher packages automatically.

## Project structure

- `app/src/main/` — Android manifest, Kotlin source and resources.
- `app/src/test/` — JVM unit tests.
- `.github/workflows/` — cloud build workflows.
