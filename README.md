# NEXA TV Launcher

NEXA is a native Android TV launcher by Mirza Qasim. The current public direct-download build is **signed Release APK v1.3.7 (versionCode 13)**.

- Android source: [NEXA-TV-Launcher](NEXA-TV-Launcher/)
- Website and download page: https://nexa-tv-launcher.vercel.app/
- Download latest signed Release APK: [NEXA-TV-Launcher-latest.apk](releases/NEXA-TV-Launcher-latest.apk)
- Version metadata: [latest.json](releases/latest.json)
- SHA-256 checksum: [NEXA-TV-Launcher-latest.apk.sha256](releases/NEXA-TV-Launcher-latest.apk.sha256)
- Automated signed-release workflow: [NEXA Signed Release APK](.github/workflows/nexa-release.yml)

## Release status

The APK in `releases/NEXA-TV-Launcher-latest.apk` is built with the protected NEXA release keystore and the package ID `com.qasim.nexa.launcher`. The keystore is not stored in the repository; GitHub Actions restores it from four repository secrets.

The Vercel `/download` route serves the latest APK from this repository. Vercel is the public download website; the APK binary and version metadata are stored in GitHub.

Older files with a `-debug.apk` suffix are development/test builds. The debug build workflow is manual-only and does not overwrite the public signed Release APK.

## Publishing future updates

When changing Android app code, increment both `versionCode` and `versionName` in `NEXA-TV-Launcher/app/build.gradle.kts` before committing. Android requires a higher versionCode for each subsequent installable update. Pushing app-source changes to `main` triggers tests, lint, signed Release APK generation, repository publication, and the Vercel website version update.

Keep the release keystore and its passwords backed up securely. Every public update must be signed with the same release key so that Android can install it over the existing Release version.

Keep the factory launcher available until the new build has been checked on the target Android TV device.
