package com.ali.formapic.domain.model

import android.util.Range
import android.util.Size

/**
 * All 15 Photography Modes + Video Mode supported in FORMA PIC.
 */
enum class CameraMode(
    val title: String,
    val shortBadge: String,
    val description: String,
    val isProManual: Boolean = false,
    val isVideoMode: Boolean = false
) {
    AI_AUTO("AI Auto", "AUTO", "Scene-aware exposure, white balance, and smart multi-frame optimization"),
    PRO_DSLR("DSLR Pro", "PRO", "Full manual ISO, shutter speed, focus distance, Kelvin WB, EV, histogram & RAW", isProManual = true),
    PORTRAIT("DSLR Portrait", "PORTRAIT", "Subject-aware depth separation, natural bokeh & authentic skin tones"),
    NIGHT("Night Mode", "NIGHT", "Multi-frame low-light noise reduction with highlight/shadow preservation"),
    HDR("HDR+", "HDR", "High dynamic range highlight and shadow recovery for high-contrast scenes"),
    PRO_RAW("Pro RAW", "RAW", "Uncompressed DNG sensor capture with optional companion JPEG"),
    CINEMATIC("Cinematic", "CINE", "Anamorphic highlight roll-off, filmic contrast & subtle vignette"),
    MACRO("Macro", "MACRO", "Close-up focus optimization using dedicated macro lens or minimum focus distance"),
    LANDSCAPE("Landscape", "LAND", "Balanced sky/foreground exposure and natural foliage/sky color rendering"),
    FOOD("Food", "FOOD", "Warm, appetizing yet natural color reproduction with center-weighted sharpness"),
    STREET("Street", "STREET", "Zero-lag fast shutter priority with documentary contrast"),
    BLACK_AND_WHITE("Black & White", "B&W", "Rich monochrome tonal profiles: Classic Silver, High Contrast & Documentary"),
    LONG_EXPOSURE("Long Exposure", "LONG EXP", "Multi-frame light trails, silky water & motion smoothing with stability check"),
    PANORAMA("Panorama", "PANO", "Guided horizontal alignment and multi-frame seamless stitching"),
    SELFIE("Selfie", "SELFIE", "Front-camera natural skin tone rendering, screen flash & optional subtle retouch"),
    BURST_BEST_SHOT("Burst / Best Shot", "BURST", "High-speed sequence capture with automatic sharpest-frame scoring"),
    VIDEO("Pro Video", "VIDEO", "Hardware-queried 720p/1080p/4K recording, stabilization, slow-mo & time-lapse", isVideoMode = true)
}

/**
 * Three primary image rendering preferences as specified in Section 2.
 */
enum class RenderingPreference(val label: String, val description: String) {
    NATURAL("Natural", "Realistic color, balanced exposure, and authentic sensor details"),
    VIBRANT("Vibrant", "Rich yet controlled colors with enhanced micro-contrast"),
    PROFESSIONAL("Professional", "Refined tones, smooth highlight roll-off, cinematic color & natural skin texture")
}

/**
 * Monochrome profiles for Black & White mode.
 */
enum class MonochromeProfile(val label: String, val rWeight: Float, val gWeight: Float, val bWeight: Float, val contrastBoost: Float) {
    CLASSIC_SILVER("Classic Silver", 0.299f, 0.587f, 0.114f, 1.08f),
    RED_FILTER_DRAMATIC("Dramatic Sky (Red Filter)", 0.55f, 0.35f, 0.10f, 1.22f),
    DOCUMENTARY_MATTE("Documentary Matte", 0.33f, 0.52f, 0.15f, 1.04f)
}

/**
 * Detected scene categories from live frame analysis.
 */
enum class DetectedScene(val label: String, val suggestedEvBias: Float, val saturationMultiplier: Float, val warmthShift: Float) {
    NEUTRAL("Standard Scene", 0.0f, 1.0f, 0.0f),
    PORTRAIT("Portrait / Face", 0.15f, 0.98f, 4.0f),
    LANDSCAPE("Landscape / Sky", -0.15f, 1.08f, 0.0f),
    LOW_LIGHT("Low Light / Night", 0.25f, 0.96f, -2.0f),
    HIGH_CONTRAST("High Dynamic Range", -0.20f, 1.04f, 0.0f),
    FOOD("Culinary / Warm", 0.20f, 1.10f, 10.0f),
    FLORA("Flowers / Foliage", 0.0f, 1.07f, 2.0f),
    ARCHITECTURE("Buildings / Urban", -0.10f, 1.02f, -3.0f),
    MACRO_CLOSEUP("Close-Up Subject", 0.10f, 1.04f, 0.0f)
}

/**
 * Aspect Ratios selectable in Pro Mode and Settings.
 */
enum class AspectRatioOption(val label: String, val ratioWidth: Int, val ratioHeight: Int) {
    RATIO_4_3("4:3 (Full Sensor)", 4, 3),
    RATIO_16_9("16:9 (Widescreen)", 16, 9),
    RATIO_1_1("1:1 (Square)", 1, 1),
    RATIO_FULL("Native Sensor", 4, 3)
}

/**
 * On-screen composition guides.
 */
enum class GridOverlayType(val label: String) {
    NONE("Off"),
    RULE_OF_THIRDS("Rule of Thirds (3x3)"),
    GOLDEN_RATIO("Phi Golden Ratio"),
    SQUARE_FRAME("1:1 Square Guide"),
    DIAGONAL_CROSS("Diagonal & Center Crosshair")
}

/**
 * Clearly distinguishes optical zoom, lossless sensor crop, and digital zoom.
 */
enum class ZoomOpticalType(val badgeText: String) {
    OPTICAL("Optical Lens"),
    SENSOR_CROP("Sensor Crop"),
    DIGITAL("Digital Zoom")
}

data class CameraLensSpec(
    val cameraId: String,
    val label: String,
    val lensFacing: Int,
    val focalLengthMm: Float,
    val apertureFNumber: Float,
    val minimumFocusDistanceDiopters: Float,
    val nominalZoomRatio: Float,
    val zoomType: ZoomOpticalType,
    val supportsRaw: Boolean,
    val supportsManualSensor: Boolean
)

/**
 * Real hardware capabilities queried via CameraCharacteristics & CameraX ExtensionsManager.
 */
data class DeviceCameraCapabilities(
    val cameraId: String = "0",
    val lensFacing: Int = 1,
    val availableLenses: List<CameraLensSpec> = emptyList(),
    val hardwareLevelName: String = "LIMITED",
    val supportsManualIso: Boolean = false,
    val isoRange: Range<Int> = Range(100, 3200),
    val supportsManualShutter: Boolean = false,
    val exposureTimeRangeNs: Range<Long> = Range(100_000L, 1_000_000_000L), // 1/10000s to 1s
    val supportsManualFocus: Boolean = false,
    val minimumFocusDistance: Float = 0f,
    val exposureCompensationRange: Range<Int> = Range(-12, 12),
    val exposureCompensationStep: Float = 0.33f,
    val maxDigitalZoom: Float = 8.0f,
    val minZoomRatio: Float = 1.0f,
    val supportsRawDng: Boolean = false,
    val supportsHeif: Boolean = false,
    val hasFlashUnit: Boolean = true,
    val supportsOpticalStabilization: Boolean = false,
    val supportsVideoStabilization: Boolean = true,
    val supportedPhotoSizes: List<Size> = emptyList(),
    val supportedVideoQualities: List<VideoQualityOption> = listOf(
        VideoQualityOption.FHD_1080P,
        VideoQualityOption.HD_720P
    ),
    val supportedFrameRates: List<Int> = listOf(30),
    val supportsSlowMotion: Boolean = false,
    // CameraX Vendor Extensions availability
    val supportsExtensionHdr: Boolean = false,
    val supportsExtensionNight: Boolean = false,
    val supportsExtensionBokeh: Boolean = false,
    val supportsExtensionFaceRetouch: Boolean = false
)

enum class VideoQualityOption(val label: String, val width: Int, val height: Int, val approxMbPerMinute: Int) {
    UHD_4K("4K UHD", 3840, 2160, 350),
    FHD_1080P("1080p FHD", 1920, 1080, 130),
    HD_720P("720p HD", 1280, 720, 65)
}

/**
 * DSLR Manual Controls State.
 */
data class ProManualParameters(
    val isIsoAuto: Boolean = true,
    val isoValue: Int = 200,
    val isShutterAuto: Boolean = true,
    val shutterSpeedNs: Long = 16_666_666L, // 1/60s
    val isFocusAuto: Boolean = true,
    val focusDistanceDiopters: Float = 0.0f,
    val isWhiteBalanceAuto: Boolean = true,
    val colorTemperatureKelvin: Int = 5500,
    val exposureCompensationEv: Float = 0.0f,
    val aeLocked: Boolean = false,
    val afLocked: Boolean = false,
    val focusPeakingEnabled: Boolean = false,
    val showHistogram: Boolean = true,
    val showZebraClipping: Boolean = false,
    val captureRawDng: Boolean = false,
    val saveRawPlusJpeg: Boolean = true
) {
    fun formattedShutterSpeed(): String {
        if (isShutterAuto) return "AUTO"
        val seconds = shutterSpeedNs / 1_000_000_000.0
        return if (seconds >= 0.5) {
            String.format("%.1fs", seconds)
        } else {
            val denom = (1.0 / seconds).toInt().coerceAtLeast(1)
            "1/${denom}s"
        }
    }
}

/**
 * Real-time frame telemetry computed by ImageAnalysis analyzer.
 */
data class LiveFrameTelemetry(
    val luminanceHistogram: IntArray = IntArray(64),
    val rHistogram: IntArray = IntArray(64),
    val gHistogram: IntArray = IntArray(64),
    val bHistogram: IntArray = IntArray(64),
    val highlightClippedRatio: Float = 0f,
    val shadowCrushedRatio: Float = 0f,
    val averageLuminance: Float = 128f,
    val sharpnessScore: Float = 0f,
    val detectedScene: DetectedScene = DetectedScene.NEUTRAL,
    val focusPeakPoints: List<Pair<Float, Float>> = emptyList(),
    val devicePitchDegrees: Float = 0f,
    val deviceRollDegrees: Float = 0f,
    val isDeviceSteady: Boolean = true
)

/**
 * Non-destructive Photo Editor parameters for AI Photo Enhancement Studio.
 */
data class PhotoEditState(
    val aiAutoEnhanced: Boolean = false,
    val renderingPreference: RenderingPreference = RenderingPreference.NATURAL,
    val processingStrength: Float = 0.75f,
    val exposure: Float = 0f,          // -2.0 to +2.0
    val brightness: Float = 0f,        // -100 to +100
    val contrast: Float = 0f,          // -100 to +100
    val highlights: Float = 0f,        // -100 to +100
    val shadows: Float = 0f,           // -100 to +100
    val whites: Float = 0f,            // -100 to +100
    val blacks: Float = 0f,            // -100 to +100
    val temperature: Float = 0f,       // -100 (Cool) to +100 (Warm)
    val tint: Float = 0f,              // -100 (Green) to +100 (Magenta)
    val saturation: Float = 0f,        // -100 to +100
    val vibrance: Float = 0f,          // -100 to +100
    val sharpening: Float = 15f,       // 0 to 100
    val noiseReduction: Float = 10f,   // 0 to 100
    val backgroundBlurBokeh: Float = 0f, // 0 to 100
    val skinRetouchStrength: Float = 0f, // 0 to 100 (Optional, identity-safe)
    val blemishReduction: Float = 0f,  // 0 to 100
    val vignetteStrength: Float = 0f,  // 0 to 100
    val filmGrain: Float = 0f,         // 0 to 100
    val rotationDegrees: Float = 0f,   // -45 to +45 or 90 deg steps
    val cropAspectRatio: AspectRatioOption? = null,
    val activePresetName: String = "None"
)
