# Lunoco stable releases and in-app updates

Lunoco's official sideload feed is https://api.github.com/repos/rgordonSJO/lunoco/releases/latest.
Downloader code **7639133** continues to resolve to the existing latest stable `Lunoco.apk` asset.
No repository commit, development suffix, release-note link or user-configured URL is an update version.

## First migration and identity

Existing public/development APKs have versionCode 1 and development version names. They cannot establish a reliable chronological stable version, so local/debug builds do not advertise or install GitHub updates automatically. They show an explanatory response to Check for Updates.

A reviewed first stable updater-enabled build must be installed in place once using the existing Downloader/direct APK path or another explicitly authorized installation. It must retain the installed application ID **com.ryan.lunatv.jellyfin.performance**, the original private signing key and its certificate SHA-256 **57a0d2a501b9a679311a88aad8d9b41a1b605119ce2cf638694dae2563cf1045**. Do not publish a different signer or an APK with the base development namespace by mistake. Thereafter normal stable updates use increasing numeric codes and stable semantic versions.

The inherited tag-count fallback remains for local development compatibility. It is not the production release numbering system.

## Building a reviewed stable sideload APK

Use both explicit properties, for example:

```powershell
./gradlew.bat :app:assembleDefaultPerformance '-Plunoco.releaseVersion=1.0.0' '-Plunoco.releaseVersionCode=2'
```

These are illustrative numbers, not an instruction to publish this version. Check the highest code ever distributed and choose a strictly greater value for every release, with an increasing stable versionName. Reusing a code is forbidden even if an old release was deleted.

The existing optimized Performance build type intentionally preserves the public application's .performance namespace and original local signing configuration. Explicit stable properties remove its local version suffix and mark it as a stable update track. Ordinary local performance builds keep their diagnostic suffix, code and development guard. Debug builds remain guarded even if stable properties were supplied.

Verify the final APK rather than assuming task names establish identity. Preserve its exact matching R8 map outside generated output directories. Never commit keys, signing passwords, APKs or private mappings. Never expose signing credentials in CI; no automatic deployment has been added.

## Required release assets

Publish a normal stable release, not draft/prerelease, with a strict tag `vMAJOR.MINOR.PATCH` and exactly:

1. `Lunoco.apk` — the tested universal signed APK.
2. `Lunoco-update.json` — a UTF-8 schema-1 manifest, at most 16KiB:

```json
{
  "schema": 1,
  "packageName": "com.ryan.lunatv.jellyfin.performance",
  "versionName": "1.0.0",
  "versionCode": 2,
  "sha256": "REPLACE_WITH_THE_VERIFIED_APK_SHA256"
}
```

The manifest version must exactly match the release tag without v and the actual APK. Numeric versionCode must be 2–2100000000 and strictly greater than installed. Use concise human-readable release notes. URLs in notes are never executable updater input.

The official API asset's SHA-256 digest, when present, must agree with the manifest. The manifest is separately retrieved through HTTPS from the same official tagged release; it is trusted publisher metadata, not a locally calculated digest being mistaken for an expected checksum. This does not protect against compromise of the publisher account/key; Android signer continuity is also required. Exact existing signer sets are accepted; certificate rotation requires a separately verified migration.

## Publication checklist (manual, never automatic)

1. Complete source/security/license review, regressions and physical acceptance.
2. Verify package, stable track, versionName/code, minimum SDK, signer, approved artwork/native libraries and matching R8 map.
3. Verify safe download/Android installer behavior on Google TV with a properly signed, higher-code compatible test APK. Do not alter watched history just to test an updater.
4. Calculate the verified APK SHA-256 and write the manifest from actual artifact metadata.
5. Create the authorized normal GitHub Release with corresponding source/tag and required GPL source/attributions. This document is not publication authorization.
6. Upload the exact APK as `Lunoco.apk`, plus manifest, and verify GitHub API metadata/digest.
7. Download both assets independently and verify checksums, package, signer and code again.
8. Verify https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk and Downloader code 7639133.
9. Verify once more that the app offers only the eligible new release and Android performs an in-place confirmed update without losing account/settings data.

The existing v0.10.2 asset has no approved migration role simply because its tag is newer than 0.0.0-local. A missing or malformed compatibility manifest is not an excuse to bypass validation. No existing release or Downloader destination is modified by source implementation.

## Runtime behavior

Stable sideload builds check asynchronously on startup, respecting the existing automatic-check preference. Attempts including failures are throttled for about 24h; rate-limit reset/retry information can extend the delay. Explicit Check for Updates bypasses this throttle but not the simultaneous-request guard.

Automatic offers wait for ready Home, 30 seconds without input and the normal resumed/focused application window. They never appear during setup/playback or over another focused window. Later/Back suppress automatic re-prompting for the process session. Manual checks remain possible.

Downloads use a bounded app-owned cache file, display progress, support cancellation and verify exact size/SHA-256, archive package/version/code and current signer before installation. No storage permission or shell/root install is used. Users grant unknown-source installation permission through Android, then explicitly retry Update Now. Android provides installation confirmation; the app never treats ACTION_VIEW result codes as reliable installation-success proof.

A canceled/failed download or handoff removes its temporary file; returned installer flows also clean up. Android may terminate the app during installation, so cleanup cannot be guaranteed synchronously after process death. Next download removes stale files in the updater's own cache directory; the OS may also evict cache.

## Google Play distribution

The existing appstore flavor has GitHub updating disabled and no REQUEST_INSTALL_PACKAGES permission. The firetv flavor is likewise excluded from this GitHub installer. Runtime detection of a Play installation also disables this path, even for a mistakenly sideload-configured build.

For current namespace compatibility, an appstore build must use an approved variant retaining .performance (for example appstorePerformance), not silently switch to appstoreRelease's different base package. Store updates are managed by Google Play; Check for Updates gives the managed-store explanation rather than contacting GitHub. A future Play in-app-update SDK integration is separate work, not a reason to ship sideload permissions in the store variant.

The preserved certificate currently has DN `C=US, O=Android, CN=Android Debug`; this phase deliberately does not replace it. Play signing eligibility and any secure migration must be reviewed separately before a store submission. This is not a Play-ready signing configuration claim.

Play App Signing must retain a compatible app-signing certificate to upgrade sideload installations with the same package. An upload key alone does not establish that compatibility. Different signing certificates cannot freely replace each other; any migration requires deliberate review. No Play listing, pricing, account or signing enrollment is changed here.

## Primary references

- [GitHub release asset metadata and digest](https://docs.github.com/en/rest/releases/assets)
- [Android package and signing information / install permission](https://developer.android.com/reference/android/content/pm/PackageManager)
- [Google Play update and installation-permission policy](https://support.google.com/googleplay/android-developer/answer/18265346)
