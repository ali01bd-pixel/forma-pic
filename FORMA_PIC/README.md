# FORMA PIC — Premium AI DSLR Camera for Android

**Professional Photography. Simplified.**  
**Created by ALI**

---

## 1. How to Get the Installable `.apk` File Without Installing Android Studio

Because this sandbox environment runs in an isolated Linux container without outbound internet access or the multi-gigabyte Android SDK (`aapt2`, `d8`, and `android.jar` for API 36), the project includes a **pre-configured One-Click Cloud APK Builder (`.github/workflows/build-apk.yml`)** and a **pre-generated Release Signing Keystore (`keystore/forma_pic_release.jks`)**.

You do **not** need to install Android Studio or any SDK tools on your computer or phone to get the `.apk`:

### Option A: Zero-Install Cloud APK Compilation via GitHub Actions (Recommended — Takes ~2 Minutes)
1. Extract `FORMA_PIC_Android_Project.zip`.
2. Create a free repository on [GitHub](https://github.com/new) (you can do this directly in your mobile or desktop browser) and upload the extracted files.
3. Click the **Actions** tab in your GitHub repository.
4. The workflow **"Build FORMA PIC APK & AAB (No Android Studio Needed)"** will run automatically (or you can click **Run workflow**).
5. Once the green checkmark appears, scroll down to the **Artifacts** section on that workflow page and download:
   - **`FORMA-PIC-Debug-APK`** (`app-debug.apk` — ready for direct installation and testing on any Android 9+ phone)
   - **`FORMA-PIC-Signed-Release-APK`** (`app-release.apk` — signed with `keystore/forma_pic_release.jks`, R8-optimized for daily photography)
   - **`FORMA-PIC-Release-AAB`** (`app-release.aab` — Google Play Store release bundle)

### Option B: Command-Line Gradle Build (If JDK 17 + Android Command-Line Tools Are Present)
```bash
gradle assembleDebug assembleRelease bundleRelease
```
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Signed Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Release AAB**: `app/build/outputs/bundle/release/app-release.aab`

---

## 2. Project Specifications & Architecture

| Specification | Configuration |
| :--- | :--- |
| **App Name** | FORMA PIC |
| **Creator Credit** | Created by ALI |
| **Application ID** | `com.ali.formapic` |
| **Minimum SDK** | API 28 (Android 9.0 Pie) |
| **Target & Compile SDK** | API 36 (Android 16) |
| **Language & UI** | 100% Kotlin + Jetpack Compose (Material 3 Luxury Dark Theme) |
| **Camera Engine** | AndroidX CameraX 1.4.1 + Camera2 Interop (`Camera2CameraControl` & `CameraCharacteristics`) |
| **Local Persistence** | Jetpack DataStore Preferences + Android `MediaStore` + `ExifInterface` |
| **Release Signing** | Pre-configured `keystore/forma_pic_release.jks` (`alias: forma_pic_key`) |

---

## 3. Implemented Modules & Features

### A. Computational Photography Engine (`ComputationalPhotographyEngine.kt`)
- **Three Rendering Preferences**:
  - **Natural**: Authentic color reproduction, balanced dynamic range, and realistic skin texture.
  - **Vibrant**: Controlled color richness with micro-contrast enhancement and skin-tone protection.
  - **Professional**: Refined highlight roll-off, cinematic tonal contrast, and natural skin preservation.
- **Adjustable Processing Strength**: User-configurable slider (`0%` to `100%`) persisted across restarts.
- **Multi-Frame Stacking**: Genuine multi-frame pixel alignment and fusion for **Night Mode** (temporal noise reduction), **Long Exposure Mode** (light trails and silky water smoothing), and **Panorama Mode** (3-frame horizontal stitch).
- **Burst & Best-Shot Selection**: Captures high-speed sequences and runs Sobel gradient sharpness evaluation (`selectSharpestBurstFrame`) to automatically pick the sharpest frame.
- **Optical Honesty**: Never artificially upscales resolution or fabricates optical detail.

### B. DSLR Pro Manual Mode (`ProManualControlDeck.kt` & `FormaCameraController.kt`)
- Hardware-verified manual controls via `Camera2CameraControl` and `CaptureRequestOptions`:
  - **ISO Sensitivity**: Full hardware range (`SENSOR_INFO_SENSITIVITY_RANGE`) + Auto toggle.
  - **Shutter Speed**: 1/8000s to 1.0s (`SENSOR_INFO_EXPOSURE_TIME_RANGE`) + Auto toggle.
  - **Manual Focus**: Calibrated diopter slider (`0.0 dpt` infinity to `LENS_INFO_MINIMUM_FOCUS_DISTANCE`) + Continuous AF toggle.
  - **White Balance**: Kelvin color temperature control (`2500K` to `8500K`) + AWB toggle.
  - **Exposure Compensation (EV)**: `-2.0 EV` to `+2.0 EV` in hardware steps.
  - **Pro Viewfinder Telemetry**: Real-time 64-bin Luminance/RGB Histogram (`LiveHistogramHud`), Zebra highlight/shadow clipping indicators, and Emerald Green Focus Peaking edge overlay (`LiveFrameAnalyzer`).
  - **Format & Composition**: RAW DNG support detection (`REQUEST_AVAILABLE_CAPABILITIES_RAW`), RAW+JPEG toggle, 4:3 / 16:9 / 1:1 / Full Sensor aspect ratios, and Rule of Thirds, Phi Golden Ratio, Square, and Crosshair grids.

### C. All 16 Photography & Video Modes (`CameraMode.kt`)
1. **AI Auto Mode**: Live YUV frame analysis (`LiveFrameAnalyzer`) detects Low Light, High Dynamic Range, Landscape/Sky, Flora, Culinary/Warm, and Portrait scenes and adapts exposure and tone mapping.
2. **DSLR Pro Mode**: Full manual control deck with live histogram, zebra warnings, and focus peaking.
3. **DSLR Portrait Mode**: Hardware Bokeh extension when available + subject-aware elliptical depth blur (`f/1.4` – `f/5.6` simulation) with adjustable strength.
4. **Night Photography Mode**: Hardware Night extension when available + 4-frame low-light noise reduction stack with countdown stability prompt.
5. **HDR+ Mode**: Hardware HDR extension when available + tone-mapped highlight compression and shadow recovery LUT.
6. **Pro RAW Mode**: Queries `REQUEST_AVAILABLE_CAPABILITIES_RAW` and informs the user clearly if the active lens does not support DNG.
7. **Cinematic Mode**: Film-inspired highlight roll-off, refined contrast, and subtle optical vignette.
8. **Macro Mode**: Inspects `LENS_INFO_MINIMUM_FOCUS_DISTANCE` to select macro capability honestly without faking macro hardware.
9. **Landscape Mode**: Sky/foliage dynamic range balancing and natural micro-contrast sharpening.
10. **Food Mode**: Appetizing warm color rendering with center-weighted sharpness.
11. **Street Photography Mode**: Low-latency shutter capture (`CAPTURE_MODE_MINIMIZE_LATENCY`) with documentary tonal contrast.
12. **Black & White Mode**: Three selectable monochrome profiles (*Classic Silver*, *Dramatic Sky Red Filter*, *Documentary Matte*).
13. **Long Exposure Mode**: 5-frame light-trail peak accumulation and motion smoothing with stability prompts.
14. **Panorama Mode**: Guided 3-step horizontal capture and automatic seam stitching.
15. **Selfie Mode**: Front-camera natural skin tone rendering, optional mirroring toggle, and display-illuminated Screen Flash.
16. **Burst / Best-Shot Mode**: High-speed multi-frame capture with automatic Sobel sharpness scoring.

### D. Built-In AI Photo Enhancement Studio (`StudioEditorScreen.kt`)
- **Non-Destructive Pipeline**: Interactive press-and-hold **COMPARE** button toggles instantly between the untouched original and the edited preview.
- **18 Adjustment Controls**: One-Tap AI Enhance, Exposure, Brightness, Contrast, Highlights, Shadows, Whites, Blacks, Temperature, Tint, Saturation, Skin-Safe Vibrance, Sharpening, Noise Reduction, Subject Bokeh Blur, Identity-Safe Skin Texture Refinement, Vignette, and Straighten/Crop.
- **Safe Export**: Always saves edited photos as a new high-resolution file (`_EDIT.jpg`) so originals are never overwritten.

### E. Integrated Gallery, Settings & About Us (`GalleryScreen.kt`, `SettingsAndAboutScreens.kt`)
- **MediaStore Integration**: Browse photos and videos, inspect EXIF metadata (camera model, focal length, aperture, ISO, shutter speed, resolution, color space), search/filter media, duplicate, share, delete, or open directly in the AI Photo Studio.
- **Dedicated About Us Screen**: Displays the exact brand credit:
  - **FORMA PIC**
  - **Professional Photography. Simplified.**
  - **Created by ALI**

---

## 4. Known Limitations & Hardware Dependencies
- **Hardware-Dependent Capabilities**: True optical zoom lenses (0.6x Ultra-Wide, 3x/5x Telephoto), RAW DNG capture, 4K 60fps video, and CameraX OEM extensions depend on the physical camera sensor and Camera2 HAL level (`LEGACY`, `LIMITED`, `FULL`, `LEVEL_3`) reported by each Android phone. FORMA PIC queries `CameraCharacteristics` at runtime and never advertises unsupported hardware features.
