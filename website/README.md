# Nexa TV Launcher website

Static, remote-friendly product site deployed to Vercel.

## Files
- `index.html` — responsive landing page, SEO metadata and TV remote spatial navigation.
- `screenshots/*.svg` — illustrative interface previews (not physical-device captures).
- `assets/` — brand mark and social preview.
- `robots.txt` and `sitemap.xml` — crawl directives and sitemap.
- `vercel.json` — permanent download redirect and same-origin release metadata rewrite.

## APK update workflow
The website's Download buttons point to `/download`, mapped to the stable GitHub asset:
`releases/NEXA-TV-Launcher-latest.apk`.

The Android GitHub Actions workflow updates this stable APK and `releases/latest.json` whenever a new debug APK is built. The website reads `/release.json` to refresh its displayed version number, so an APK update does not require changing the download button or redeploying the site.

For manual releases, replace the stable APK and keep `latest.json` in sync. Preserve the stable asset filename and URL.
