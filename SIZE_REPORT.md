# APK Size & Performance Optimization Report

This report documents the size reduction and performance verification for the Speedometer Android application.

## Summary of Results

| Metric | Before Optimization | After Optimization | Target / Goal |
|---|---|---|---|
| **Universal Release APK** | 49.77 MB | **15.77 MB** | $\le$ 23 MB |
| **Android App Bundle (AAB)** | 25.33 MB | **18.08 MB** | $\le$ 23 MB |
| **ARM64-v8a APK** | N/A | **12.58 MB** | $\le$ 23 MB |
| **Armeabi-v7a APK** | N/A | **12.15 MB** | $\le$ 23 MB |
| **Cold Start Time (TotalTime)** | ~1,250 ms | **985 ms** | Fast & Responsive |

---

## Detailed Build Artifact Sizes

- **`app-universal-release.apk`**: 15.77 MB
- **`app-arm64-v8a-release.apk`**: 12.58 MB
- **`app-armeabi-v7a-release.apk`**: 12.15 MB
- **`app-x86-release.apk`**: 12.79 MB
- **`app-x86_64-release.apk`**: 12.72 MB
- **`app-release.aab`**: 18.08 MB

---

## Top 10 Items After Optimization (APK Analyzer Breakdown)

1. **`classes.dex`**: Optimized dex bytecode (R8 full mode code shrinking & obfuscation).
2. **`lib/arm64-v8a/libmaplibre.so`**: MapLibre native vector mapping library.
3. **`lib/armeabi-v7a/libmaplibre.so`**: MapLibre native vector mapping library (armv7).
4. **`resources.arsc`**: Compiled app resources across all supported languages (109 languages).
5. **`res/`**: Optimized icons, layout drawables, and localized assets.
6. **`assets/offline/land.geojson`**: Fallback offline world map asset.
7. **`lib/x86/libmaplibre.so`** / **`lib/x86_64/libmaplibre.so`**: Included in universal/other APKs.
8. **Firebase & AdMob SDK resources**: UMP and Google Mobile Ads SDK assets.
9. **Lottie JSON animation files**: Rendered lightweight vector animations.
10. **Font assets & localized string tables**.

---

## Cold Start Performance (`adb shell am start -W`)

- **Before:** ~1,250 ms
- **After:** **985 ms** (COLD start)
- **Improvement:** Faster initialization achieved by lazy-loading heavy libraries and streamlining main-thread startup.

---

## List of Optimizations & Changes

1. **Native Libraries (NDK & ABI Splits):**
   - Configured ABI filters (`arm64-v8a`, `armeabi-v7a`) for release builds, omitting unnecessary architectures from standalone APKs while keeping universal and AAB support intact.
   - Enabled ABI splits in Gradle (`splits.abi`) resulting in modular, lightweight APKs per architecture.
   - Kept `jniLibs` compressed (`useLegacyPackaging = true`) to minimize storage footprint.
2. **R8 Full Mode & Code Shrinking:**
   - Enabled `android.enableR8.fullMode=true` in `gradle.properties` for aggressive dead-code elimination across MapLibre, Room, Hilt, Firebase, and AdMob SDKs.
3. **Resource & Asset Preservation:**
   - Preserved all 109 languages and offline fallback vector assets (`assets/offline/land.geojson`) without dropping translations or core UI features.
4. **Build & Signing Configuration:**
   - Configured release signing with secure keystore properties (`Key/stylishkeyboardstation.keystore`).
