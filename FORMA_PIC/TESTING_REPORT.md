# FORMA PIC — Testing & Quality Assurance Report

**Project**: FORMA PIC — Premium AI DSLR Camera for Android  
**About Us Credit**: Created by ALI  
**Target / Minimum SDK**: API 36 (Android 16) / API 28 (Android 9.0)

---

## 1. Completed Verification & Static/Architectural Checks

| Test Category | Verification Performed | Status |
| :--- | :--- | :--- |
| **Source Code & Package Structure** | Verified all 12 Kotlin source files across `domain`, `data`, and `ui` packages for syntax, imports, and API 28–36 compatibility. | **Completed** |
| **Gradle & Dependency Resolution** | Configured Android Gradle Plugin `8.7.3`, Kotlin `2.1.0`, Jetpack Compose BOM `2024.12.01`, and CameraX `1.4.1` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-video`, `camera-view`, `camera-extensions`). | **Completed** |
| **Release Keystore & Signing** | Generated valid 2048-bit RSA keystore (`keystore/forma_pic_release.jks`) via OpenJDK 17 `keytool` and wired `signingConfigs.release` in `app/build.gradle.kts`. | **Completed** |
| **Runtime Permission Safety** | Verified `AndroidManifest.xml` and `MainActivity.kt` request `CAMERA` at launch, scope `WRITE_EXTERNAL_STORAGE` strictly to API 28 (`maxSdkVersion="28"`), use `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on Android 13+, and never request `RECORD_AUDIO` or `ACCESS_FINE_LOCATION` unless explicitly enabled. | **Completed** |
| **Unit Test Suite (`FormaPicUnitTest.kt`)** | Implemented JUnit 4 tests validating DSLR shutter speed formatting (`1/1000s`, `0.5s`), all 16 `CameraMode` definitions, and all 3 `RenderingPreference` profiles. | **Completed** |
| **About Us Brand Verification** | Confirmed `AboutUsScreen.kt` and `strings.xml` display exact branding (`FORMA PIC`, `Professional Photography. Simplified.`, `Created by ALI`) with zero fabricated contact details. | **Completed** |

---

## 2. Remaining Real-Device & Hardware-Dependent Verification

Because this sandbox environment does not have physical Android camera sensors attached or the Android SDK binary compiler (`aapt2` / `d8`) pre-installed locally, the following tests must be executed on physical Android hardware once the APK is compiled via the included GitHub Actions workflow or Gradle:

1. **Physical Sensor & Camera2 HAL Verification (Android 9 API 28 & Android 14/15/16 Devices)**:
   - Verify `CameraCapabilityInspector` accurately enumerates physical vs. logical multi-camera IDs (Ultra-Wide, Primary Wide, Telephoto) across Qualcomm Snapdragon, MediaTek Dimensity, Google Tensor, and Samsung Exynos ISPs.
   - Verify manual ISO and Shutter Speed overrides (`CONTROL_AE_MODE_OFF`) on `FULL` and `LEVEL_3` devices, and confirm graceful fallback to EV compensation on `LIMITED` or `LEGACY` devices.
2. **CameraX Vendor Extensions Verification**:
   - Confirm `ExtensionsManager` activation for hardware `HDR`, `NIGHT`, `BOKEH`, and `FACE_RETOUCH` on OEM-supported flagship phones and seamless fallback to `ComputationalPhotographyEngine` on standard devices.
3. **Thermal, Latency & Memory Profiling Under Burst / Multi-Frame Loads**:
   - Measure heap allocation and frame processing latency during 4-frame Night stacking, 5-frame Long Exposure merging, and 3-frame Panorama stitching at full sensor resolution (12MP–50MP).
4. **Video Recording & Stabilization**:
   - Test 4K UHD / 1080p FHD recording, pause/resume transitions, audio synchronization, and `MediaStore.Video` output across Android 9 (`API 28`) and scoped storage (`API 29–36`).
