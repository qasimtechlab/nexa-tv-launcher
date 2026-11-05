# NEXA Smart TV Launcher — V1.2.1

Native Android TV launcher built with Kotlin and classic Android Views. Designed for older Android TV boxes and remote-control navigation.

**Creator:** Mirza Qasim  
**Origin:** Faisalabad, Pakistan  
**AI assistant:** BixSigma AI, powered by the user's own Gemini API key.

## Home screen and apps

- Clean Home layout that can hide the large introductory panels and make the wallpaper the focus.
- Home screen Favorites row shows only apps explicitly added by the user.
- Long-press the remote's OK/Center button on an app to open the styled application options menu.
- App options include open, add/remove Favorites, move up/down or enter an exact numbered position in the library, and request uninstall through Android's system confirmation.
- Full installed-app library supports Grid and line-wise Rows layouts.
- Manual app ordering is shared by the app library and Favorites row.
- Live clock and date widget on Home. Drag it with a pointer/touch input or move it with LEFT/RIGHT on a remote; the clock can also be centered with a preset.

## YouTube video cards — API-free Hindi/Urdu channel feeds

- No YouTube Data API key, Gemini key reuse, Google Cloud project or OAuth setup is required for the curated public video feed.
- NEXA includes **32 curated Pakistani and Indian channels across seven categories**: Pakistani Dramas, Indian Movies, Indian TV & Dramas, Hindi/Urdu Technology, Hindi/Urdu Gaming, Pakistani Vlogs and Indian Vlogs.
- The initial list includes official broadcaster/movie-library channels such as HUM TV, ARY Digital, Har Pal Geo, Green TV, SET India, Colors TV, Goldmines, Shemaroo Movies, Ultra Movie Parlour and Rajshri, plus Hindi/Urdu tech, gaming and vlog creators including Tech Burner, AS Gaming, Gyan Gaming, Lokesh Gamer and Rabeeca Khan.
- NEXA reads each configured channel's public YouTube Atom uploads feed, merges recent uploads, removes duplicate videos and sorts by publish date.
- Cards show video title, channel name and a thumbnail derived from the YouTube video ID. Selecting a card opens that exact video in the installed YouTube app when available.
- Categories can be enabled or disabled from **YouTube → Choose video categories**. Topic opens adjust only NEXA's local category order; this is not YouTube's private recommendation algorithm.
- **Settings → YouTube Recommendations** can switch the Home video section ON or OFF. OFF hides the entire recommendation section; the user's explicitly selected Favorite Apps row and the rest of the Home layout remain unchanged.
- **Open my official YouTube Home** launches the installed YouTube app for the signed-in user's own recommendations, watch history and subscriptions.

### YouTube feed limitations

The public Atom feed is a channel-upload feed, not a global YouTube search endpoint. NEXA can only show recent public uploads from channel IDs included in its curated catalogue. It cannot discover every Pakistani or Indian channel automatically, read private account watch history, or reproduce YouTube's personal recommendation engine. Channel uploads, video availability, copyright permissions and regional playback restrictions can change; movie and drama cards are shown only when the channel itself publicly exposes those uploads. The catalogue is a curated starter library and can be expanded after channel IDs and upload feeds are verified.

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
`releases/NEXA-TV-Launcher-v1.2.1-debug.apk`

It is also uploaded as the `NEXA-TV-Launcher-debug-APK` artifact in GitHub Actions. This is a debug APK for testing, not a signed public release.

## Safe installation and testing

1. Install the APK as a normal app; do not replace or disable the factory launcher.
2. Open NEXA from the TV Apps list.
3. Test LEFT/RIGHT/UP/DOWN/CENTER/BACK remote navigation.
4. Test opening and returning from several apps.
5. Test long-press OK on an app, Favorites, moving an app and uninstall confirmation.
6. Test the recommendations ON/OFF switch, category feeds, thumbnails and video launching without entering an API key.
7. Test built-in wallpapers, custom wallpaper selection, framing and overlay controls.
8. Test the draggable clock and each futuristic widget.
9. Only after the checks pass, choose NEXA as the default Home app.
10. Keep the original launcher enabled so it can be selected again if needed.

## Current limitations

- YouTube topic ranking uses local interest selections and card opens; it does not access private watch history or reproduce YouTube's account-level recommendation algorithm.
- Curated YouTube cards use public channel Atom feeds; they are limited to the channel IDs bundled in NEXA and do not provide global search or account-personalized recommendations.
- Gemini voice input, microphone activation, AI-generated speech and screensaver controls are not implemented.
- System apps protected by the TV firmware may not be uninstallable through Android's normal package installer.
- Wallpaper picker availability depends on TV firmware and available document-provider apps.
- Blur is represented by translucent glass-style surfaces for older Android compatibility.
- NEXA does not disable, uninstall or modify factory launcher packages automatically.

## Project structure

- `app/src/main/` — Android manifest, Kotlin source and resources.
- `app/src/test/` — JVM unit tests.
- `.github/workflows/` — cloud build workflows.
