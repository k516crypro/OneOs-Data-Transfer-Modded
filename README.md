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

Requires JDK 21 and Android SDK (android-37). From `app-android/`:

```bash
./gradlew :app:assembleFullDebug --stacktrace
```

APK output: `app-android/app/build/outputs/apk/full/debug/`

## GitHub Actions

Push to `main` or run **Actions → Build OneOS APK → Run workflow**. Download `oneos-full-debug` from the completed run's Artifacts (14-day retention).

CI verifies compilation only; real-device testing is still required for transfers, cancellation, low storage, Guest visibility, and root behavior.

## Safety

Experimental software. Back up important data and test on a non-critical device. No license is selected yet; reuse permissions are not granted by default.
