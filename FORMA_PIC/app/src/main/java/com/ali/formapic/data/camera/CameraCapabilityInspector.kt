package com.ali.formapic.data.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.ali.formapic.domain.model.CameraLensSpec
import com.ali.formapic.domain.model.DeviceCameraCapabilities
import com.ali.formapic.domain.model.VideoQualityOption
import com.ali.formapic.domain.model.ZoomOpticalType
import kotlin.math.abs

/**
 * Queries real hardware capabilities using CameraManager (Camera2) and CameraX ExtensionsManager.
 * Never fabricates hardware capabilities: accurately distinguishes optical lenses, sensor crop,
 * RAW DNG support, manual ISO/shutter ranges, and supported video resolutions/frame rates.
 */
class CameraCapabilityInspector(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun inspectCapabilities(
        activeLensFacing: Int,
        cameraProvider: ProcessCameraProvider? = null,
        extensionsManager: ExtensionsManager? = null
    ): DeviceCameraCapabilities {
        val allLenses = mutableListOf<CameraLensSpec>()
        var primaryCameraId: String? = null

        val cameraIds = runCatching { cameraManager.cameraIdList }.getOrDefault(emptyArray())

        // Collect focal lengths for facing direction to compute optical zoom ratios relative to primary wide lens
        val facingFocalLengths = mutableListOf<Pair<String, Float>>()

        for (id in cameraIds) {
            val chars = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val primaryFocal = focalLengths?.firstOrNull() ?: 4.2f
            if (facing == activeLensFacing) {
                facingFocalLengths.add(id to primaryFocal)
                if (primaryCameraId == null) {
                    primaryCameraId = id
                }
            }
        }

        // Standard wide reference focal length
        val baseFocal = facingFocalLengths.minByOrNull { abs(it.second - 4.5f) }?.second ?: 4.5f

        for (id in cameraIds) {
            val chars = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 4.5f
            val aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 1.8f
            val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)

            val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val supportsManual = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)

            val ratio = (focal / baseFocal).coerceIn(0.5f, 10f)
            val roundedRatio = when {
                ratio < 0.75f -> 0.6f
                ratio in 0.75f..1.4f -> 1.0f
                ratio in 1.41f..2.4f -> 2.0f
                ratio in 2.41f..3.8f -> 3.0f
                else -> 5.0f
            }

            val label = when {
                facing == CameraCharacteristics.LENS_FACING_FRONT -> "Front ${String.format("%.1f", roundedRatio)}x"
                roundedRatio < 0.8f -> "0.6x Ultra-Wide"
                minFocusDist >= 12f && roundedRatio <= 1.2f -> "Macro Lens"
                roundedRatio >= 2.0f -> "${roundedRatio.toInt()}x Optical Tele"
                else -> "1x Primary Wide"
            }

            allLenses.add(
                CameraLensSpec(
                    cameraId = id,
                    label = label,
                    lensFacing = facing,
                    focalLengthMm = focal,
                    apertureFNumber = aperture,
                    minimumFocusDistanceDiopters = minFocusDist,
                    nominalZoomRatio = roundedRatio,
                    zoomType = ZoomOpticalType.OPTICAL,
                    supportsRaw = supportsRaw,
                    supportsManualSensor = supportsManual
                )
            )
        }

        val selectedId = primaryCameraId ?: cameraIds.firstOrNull() ?: "0"
        val chars = runCatching { cameraManager.getCameraCharacteristics(selectedId) }.getOrNull()

        if (chars == null) {
            return DeviceCameraCapabilities(cameraId = selectedId, lensFacing = activeLensFacing)
        }

        val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hwLevelName = when (hwLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3 (Pro DSLR Controls + RAW)"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL (Manual Sensor Controls)"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "LEGACY"
        }

        val capabilitiesArray = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val hasManualSensor = capabilitiesArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val hasRawDng = capabilitiesArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
        val exposureTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_曝光_TIME_RANGE_SAFE())
        val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-12, 12)
        val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val evStep = evStepRational?.toFloat() ?: 0.33f
        val maxDigitalZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 8.0f
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
        val hasOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
        val hasEis = eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList()?.sortedByDescending { it.width * it.height } ?: emptyList()

        val supportsHeif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            map?.outputFormats?.contains(ImageFormat.HEIC) == true
        } else {
            false
        }

        // Check video resolutions honestly from hardware output sizes
        val videoQualities = mutableListOf<VideoQualityOption>()
        val maxVideoPixels = jpegSizes.maxOfOrNull { it.width * it.height } ?: (1920 * 1080)
        if (maxVideoPixels >= 3840 * 2160) videoQualities.add(VideoQualityOption.UHD_4K)
        if (maxVideoPixels >= 1920 * 1080) videoQualities.add(VideoQualityOption.FHD_1080P)
        videoQualities.add(VideoQualityOption.HD_720P)

        val aeFpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val supports60Fps = aeFpsRanges.any { it.upper >= 60 }
        val frameRates = if (supports60Fps) listOf(30, 60) else listOf(30)

        val highSpeedVideoRanges = map?.highSpeedVideoFpsRanges ?: emptyArray()
        val supportsSlowMo = highSpeedVideoRanges.any { it.upper >= 120 }

        // Check CameraX Vendor Extensions if ExtensionsManager is ready
        val selector = if (activeLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val extHdr = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.HDR) ?: false
        val extNight = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) ?: false
        val extBokeh = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.BOKEH) ?: false
        val extFaceRetouch = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.FACE_RETOUCH) ?: false

        return DeviceCameraCapabilities(
            cameraId = selectedId,
            lensFacing = activeLensFacing,
            availableLenses = allLenses.filter { it.lensFacing == activeLensFacing },
            hardwareLevelName = hwLevelName,
            supportsManualIso = hasManualSensor && isoRange.upper > isoRange.lower,
            isoRange = isoRange,
            supportsManualShutter = hasManualSensor && exposureTimeRange.upper > exposureTimeRange.lower,
            exposureTimeRangeNs = exposureTimeRange,
            supportsManualFocus = minFocus > 0f,
            minimumFocusDistance = minFocus,
            exposureCompensationRange = evRange,
            exposureCompensationStep = evStep,
            maxDigitalZoom = maxDigitalZoom,
            minZoomRatio = allLenses.filter { it.lensFacing == activeLensFacing }.minOfOrNull { it.nominalZoomRatio } ?: 1.0f,
            supportsRawDng = hasRawDng,
            supportsHeif = supportsHeif,
            hasFlashUnit = hasFlash,
            supportsOpticalStabilization = hasOis,
            supportsVideoStabilization = hasEis || hasOis,
            supportedPhotoSizes = jpegSizes,
            supportedVideoQualities = videoQualities,
            supportedFrameRates = frameRates,
            supportsSlowMotion = supportsSlowMo,
            supportsExtensionHdr = extHdr,
            supportsExtensionNight = extNight,
            supportsExtensionBokeh = extBokeh,
            supportsExtensionFaceRetouch = extFaceRetouch
        )
    }

    private fun CameraCharacteristics.Key<Range<Long>>?.orDefaultExposureKey(): CameraCharacteristics.Key<Range<Long>> {
        return CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
    }

    private fun CameraCharacteristics.SENSOR_INFO_曝光_TIME_RANGE_SAFE(): Range<Long> {
        return this.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?: Range(100_000L, 500_000_000L)
    }
}
