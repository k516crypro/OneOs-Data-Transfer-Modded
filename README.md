# OneOS — Data Transfer (Modded)

> Experimental / not device-validated. A successful CI build does not prove runtime safety or device compatibility.

## Data Transfer

- Host → Guest only; Host source is read-only.
- Destination: Guest Pictures/Movies/Documents/Download under OneOS Transfer.
- Streaming uses 64 KiB chunks, not whole-file memory loading.
- SHA-256 verification before success; `.part` temporary file then atomic rename.
- Duplicate policy: Skip by default, Rename option; Replace disabled.
- Guest storage check when available with 100 MiB margin.
- Gallery/media indexing is not guaranteed yet.

## Build

Requires JDK 21 and Android SDK API 37. From the repository root, build and stage the Guest helper first, then assemble the main APK:

```bash
./scripts/build-guest-manager.sh debug
cd app-android
./gradlew :app:assembleFullDebug --stacktrace
```

APK output: `app-android/app/build/outputs/apk/full/debug/`

## GitHub Actions

Push to `main` or run **Actions → Build OneOS APK → Run workflow**. Download `oneos-full-debug` from the completed run's Artifacts (14-day retention). The workflow needs repository Actions secrets `GH_PACKAGES_USER` and `GH_PACKAGES_TOKEN` to resolve Samsung SESL packages from GitHub Packages; the token must have `read:packages` access. Keep credentials in Actions secrets, never in source.

CI verifies compilation only; real-device testing is still required for transfers, cancellation, low storage, Guest visibility, and root behavior.

## License and safety

See `LICENSE`, `NOTICE`, and `CREDITS.md` for license and attribution terms. In particular, OneLineage-derived components are included with separate written permission for this OneOS distribution; that permission does not grant general reuse rights elsewhere. This software is experimental: back up important data and test on a non-critical device.
