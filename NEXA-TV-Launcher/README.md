# NEXA Smart TV Launcher — V1.3.4

Native Android TV launcher built with Kotlin and classic Android Views. Designed for Android TV devices and compatible TV boxes with remote-first navigation.

**Creator:** Mirza Qasim  
**Origin:** Faisalabad, Pakistan  
**AI assistant:** BixSigma AI, powered by each user's own Gemini API key.

## Home screen and apps

- The Home screen opens directly with the widget strip, Favorites and the YouTube video feed. The large introductory slogan and shortcut-card panel have been removed.
- Home Favorites shows only apps explicitly added by the user.
- Long-press OK/Center on an app to open its options menu: open, add/remove Favorites, move app or request uninstall through Android's system confirmation.
- The app library supports Grid and Rows layouts. Manual app order is shared by the app library and Favorites.
- On Home, long-press OK/Center on the clock or animated widget to open its options. Choose **Move widget** to enter move mode or **Center widget** to center it.
- During widget move mode, LEFT/RIGHT/UP/DOWN adjust its position inside the widget strip. Press OK/Center to save or BACK to cancel. Outside move mode, arrow keys retain normal focus navigation and vertical scrolling; widgets do not drift when the user scrolls.

## YouTube video cards — API-free Hindi/Urdu channel feeds

- No YouTube Data API key, Gemini key reuse, Google Cloud project or OAuth setup is required for the public feed.
- The maintained starter catalogue contains **212 named channel entries across 22 categories**, covering Pakistani and Indian dramas, movies, news, music/naats, technology, gaming, vlogs, lifestyle, food, education, Islamic content, kids/family, sports, documentaries, travel, DIY, podcasts, business and cars.
- NEXA reads public YouTube Atom upload feeds. Handle-based entries are resolved to stable channel IDs and cached locally. Unavailable channels and feeds with no public uploads are skipped; active uploads are merged, duplicates removed and results sorted by publish date.
- If the selected category has no available uploads, Home tries other selected categories for an active feed. If no feed can be loaded, NEXA shows a direct YouTube category-browse option instead of an empty video rail.
- Video cards show title, channel and thumbnail. Selecting a card opens that video in the installed YouTube app when available.
- Categories can be selected from **YouTube → Choose video categories**. Topic opens adjust NEXA's local category order; this is not YouTube's private recommendation algorithm.
- **Settings → YouTube Recommendations** can switch the Home video section ON or OFF.
- **Open my official YouTube Home** launches the installed YouTube app for the signed-in user's recommendations, watch history and subscriptions.

### YouTube feed limitations

The public Atom feed is a channel-upload feed, not a global YouTube search endpoint. The 212-entry catalogue is a maintained curated starter list, not a claim that every entry is active or that thousands of channels have been verified. YouTube handles can change, feeds can be blocked or empty, uploads can be removed, and availability may vary by region. If no public feed is available, users can browse the category directly in YouTube. NEXA cannot read private watch history, perform unrestricted global channel discovery or reproduce YouTube's personal recommendation algorithm.

## Wallpapers and widgets

- Six built-in offline gradient wallpapers plus a local image picker for gaming, space, nature or personal wallpapers.
- Wallpaper framing controls: Fill Screen, Fit Full Image or Center.
- Adjustable wallpaper overlay strength to balance image visibility and card readability.
- Widgets can be repositioned with a TV remote using the explicit long-press options and move mode described above.
- Glass, Dark and Light appearance modes plus accent colour selection.
- **Futuristic Widgets** settings offer an offline animated Rotating Earth globe or Orbital Reactor, with side/center placement and an off switch.
- Widget positions are stored locally on the TV.

## BixSigma AI

- Chat using Google's Gemini Interactions API.
- Add, replace, test and remove a personal Gemini API key from the TV interface. The Add API key dialog includes a remote-friendly **Paste from clipboard** button.
- The AI page includes a **Secret** vault opened with the supplied UI PIN. It can copy the personal API key already saved on that TV. A fresh install does not contain a shared developer key; users create their own key in Google AI Studio. The PIN is a convenience gate, not a security boundary, because app code can be inspected.
- Gemini key storage uses app-private preferences and Android Keystore AES-GCM encryption where supported.
- Every assistant reply has a speaker control. It uses Gemini 3.8 Flash TTS to generate natural spoken audio with the same personal API key used for chat. Press the speaker again to stop playback. The control shows when audio is being generated or played.
- Voice generation requires internet access and TTS model access/quota for the user's Google AI project. Availability, limits and terms are controlled by Google.
- NEXA does not contain a shared developer API key. Do not embed API keys in the public APK or source repository; a client-side key can be extracted and its quota abused.

## Compatibility and testing

Configured for minSdk 21, compileSdk 35, JDK 17, Android Gradle Plugin 8.7.3, Kotlin 2.1.20 and Gradle 8.9. Cloud builds run unit tests, Android lint and assemble a debug APK.

The generated test APK is copied to:
`releases/NEXA-TV-Launcher-v1.3.4-debug.apk`

It is also uploaded as the `NEXA-TV-Launcher-debug-APK` artifact in GitHub Actions. This is a debug APK for testing, not a signed public Play release.

## Safe installation and testing

1. Install the APK as a normal app; do not replace or disable the factory launcher.
2. Open NEXA from the TV Apps list.
3. Test LEFT/RIGHT/UP/DOWN/CENTER/BACK remote navigation and normal vertical Home scrolling.
4. Test opening and returning from several apps.
5. Test long-press OK on an app, Favorites, app move mode and uninstall confirmation.
6. Test long-press OK on the clock and animated widget, options menu, four-way movement, OK save and BACK cancel.
7. Test the recommendations ON/OFF switch, categories, feed fallback, thumbnails and video launching.
8. Test built-in wallpapers, custom wallpaper selection, framing and overlay controls.
9. Test BixSigma chat, API-key clipboard paste, Gemini TTS play/stop and the TV's speaker output.
10. Only after the checks pass, choose NEXA as the default Home app. Keep the original launcher enabled so it can be selected again if needed.

## Current limitations

- YouTube recommendations are curated public channel uploads, not global search or account-personalized recommendations.
- Some channel entries may be inactive, renamed or blocked. NEXA skips unavailable feeds and falls back to other categories; a category search remains available in YouTube.
- Gemini speech generation depends on the user's own key, TTS model access, internet connectivity and quota.
- Microphone input uses Android's SpeechRecognizer and depends on the TV firmware, recognition service and microphone permissions.
- System apps protected by TV firmware may not be uninstallable through Android's normal package installer.
- Wallpaper picker availability depends on TV firmware and installed document-provider apps.
- Blur is represented by translucent glass-style surfaces for older Android compatibility.
- NEXA does not disable, uninstall or modify factory launcher packages automatically.

## Project structure

- `app/src/main/` — Android manifest, Kotlin source and resources.
- `app/src/test/` — JVM unit tests.
- `.github/workflows/` — cloud build workflows.
