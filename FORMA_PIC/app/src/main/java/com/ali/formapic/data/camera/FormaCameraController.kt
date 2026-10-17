package com.ali.formapic.data.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ali.formapic.domain.model.AspectRatioOption
import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.DeviceCameraCapabilities
import com.ali.formapic.domain.model.LiveFrameTelemetry
import com.ali.formapic.domain.model.ProManualParameters
import com.ali.formapic.domain.model.VideoQualityOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Manages CameraX lifecycle binding, vendor extensions (HDR, Night, Bokeh, Face Retouch),
 * Camera2 Interop manual DSLR sensor controls (ISO, shutter speed, manual focus distance, Kelvin WB,
 * AE/AF lock, video stabilization), high-res photo capture, and video recording.
 */
class FormaCameraController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTelemetry: (LiveFrameTelemetry) -> Unit,
    private val onCapabilitiesChanged: (DeviceCameraCapabilities) -> Unit,
    private val onCameraError: (String) -> Unit
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val capabilityInspector = CameraCapabilityInspector(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var activeCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var imageAnalysis: ImageAnalysis? = null

    var isFocusPeakingActive: Boolean = false

    suspend fun initializeAndBind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        mode: CameraMode,
        aspectRatioOption: AspectRatioOption,
        flashMode: Int,
        videoQualityOption: VideoQualityOption
    ) {
        try {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await().also {
                cameraProvider = it
            }
            val extManager = extensionsManager ?: ExtensionsManager.getInstanceAsync(context, provider).await().also {
                extensionsManager = it
            }

            val caps = capabilityInspector.inspectCapabilities(lensFacing, provider, extManager)
            onCapabilitiesChanged(caps)

            provider.unbindAll()

            val baseSelector = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Apply Hardware Vendor Extension if mode matches and device supports it
            val targetSelector = when (mode) {
                CameraMode.HDR -> if (extManager.isExtensionAvailable(baseSelector, ExtensionMode.HDR)) {
                    extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
                } else baseSelector
                CameraMode.NIGHT -> if (extManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)) {
                    extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
                } else baseSelector
                CameraMode.PORTRAIT -> if (extManager.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH)) {
                    extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.BOKEH)
                } else baseSelector
                CameraMode.SELFIE -> if (extManager.isExtensionAvailable(baseSelector, ExtensionMode.FACE_RETOUCH)) {
                    extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.FACE_RETOUCH)
                } else baseSelector
                else -> baseSelector
            }

            val cameraXAspectRatio = when (aspectRatioOption) {
                AspectRatioOption.RATIO_16_9 -> AspectRatio.RATIO_16_9
                else -> AspectRatio.RATIO_4_3
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(cameraXAspectRatio)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val captureMode = if (mode == CameraMode.STREET || mode == CameraMode.BURST_BEST_SHOT) {
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            } else {
                ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(captureMode)
                .setTargetAspectRatio(cameraXAspectRatio)
                .setFlashMode(flashMode)
                .build()

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(cameraXAspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        LiveFrameAnalyzer(
                            computeFocusPeaking = { isFocusPeakingActive },
                            onTelemetryUpdated = onTelemetry
                        )
                    )
                }

            // Setup VideoCapture Recorder based on real device hardware quality
            val desiredQuality = when (videoQualityOption) {
                VideoQualityOption.UHD_4K -> Quality.UHD
                VideoQualityOption.FHD_1080P -> Quality.FHD
                VideoQualityOption.HD_720P -> Quality.HD
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        desiredQuality,
                        androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            activeCamera = if (mode.isVideoMode) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    targetSelector,
                    preview,
                    videoCapture,
                    imageAnalysis
                )
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    targetSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            }
        } catch (e: Exception) {
            onCameraError("Camera initialization notice: ${e.localizedMessage ?: "Hardware busy or unavailable"}")
        }
    }

    /**
     * Applies DSLR Pro Mode manual parameters via Camera2CameraControl Interop.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyProManualControls(params: ProManualParameters, capabilities: DeviceCameraCapabilities) {
        val cam = activeCamera ?: return
        isFocusPeakingActive = params.focusPeakingEnabled

        try {
            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            val builder = CaptureRequestOptions.Builder()

            // 1. Manual ISO or Shutter Speed requires CONTROL_AE_MODE_OFF when hardware supports manual sensor
            val useManualAe = (!params.isIsoAuto || !params.isShutterAuto) &&
                (capabilities.supportsManualIso || capabilities.supportsManualShutter)

            if (useManualAe) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val clampedIso = params.isoValue.coerceIn(capabilities.isoRange.lower, capabilities.isoRange.upper)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)

                val clampedShutter = params.shutterSpeedNs.coerceIn(
                    capabilities.exposureTimeRangeNs.lower,
                    capabilities.exposureTimeRangeNs.upper
                )
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedShutter)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, params.aeLocked)

                // Exposure Compensation (EV)
                val evSteps = (params.exposureCompensationEv / capabilities.exposureCompensationStep.coerceAtLeast(0.1f)).roundToInt()
                val clampedEvIndex = evSteps.coerceIn(
                    capabilities.exposureCompensationRange.lower,
                    capabilities.exposureCompensationRange.upper
                )
                cam.cameraControl.setExposureCompensationIndex(clampedEvIndex)
            }

            // 2. Manual Focus Distance vs Continuous Autofocus
            if (!params.isFocusAuto && capabilities.supportsManualFocus) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                val clampedFocus = params.focusDistanceDiopters.coerceIn(0f, capabilities.minimumFocusDistance)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, clampedFocus)
            } else {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }

            // 3. White Balance presets approximated from Kelvin temperature if manual WB is selected
            if (!params.isWhiteBalanceAuto) {
                val wbMode = when {
                    params.colorTemperatureKelvin < 3400 -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                    params.colorTemperatureKelvin in 3400..4500 -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                    params.colorTemperatureKelvin in 4501..5800 -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                    params.colorTemperatureKelvin in 5801..6800 -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    else -> CaptureRequest.CONTROL_AWB_MODE_SHADE
                }
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }

            c2Control.captureRequestOptions = builder.build()
        } catch (e: Exception) {
            // Fallback gracefully on entry-level devices with limited Camera2 interop support
        }
    }

    fun setZoomRatio(ratio: Float) {
        activeCamera?.cameraControl?.setZoomRatio(ratio.coerceAtLeast(0.5f))
    }

    fun setFlashMode(flashMode: Int) {
        imageCapture?.flashMode = flashMode
        activeCamera?.cameraControl?.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON && videoCapture != null)
    }

    fun triggerTapToFocus(previewView: PreviewView, x: Float, y: Float, lockAeAf: Boolean = false) {
        val cam = activeCamera ?: return
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val actionBuilder = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        )
        if (lockAeAf) {
            actionBuilder.disableAutoCancel()
        } else {
            actionBuilder.setAutoCancelDuration(4, TimeUnit.SECONDS)
        }
        cam.cameraControl.startFocusAndMetering(actionBuilder.build())
    }

    /**
     * Captures a high-resolution photo in memory as a properly rotated Bitmap ready for
     * FORMA PIC's ComputationalPhotographyEngine.
     */
    suspend fun captureSingleBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (cont.isActive) cont.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        enableAudio: Boolean,
        prefix: String = "FORMA_VID_",
        onEvent: (VideoRecordStatus) -> Unit
    ) {
        val vidCap = videoCapture ?: run {
            onCameraError("Switch to Pro Video mode to record video")
            return
        }

        if (activeRecording != null) {
            stopVideoRecording()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${prefix}${timestamp}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/FORMA_PIC")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pending = vidCap.output.prepareRecording(context, mediaStoreOutputOptions)
        if (enableAudio) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> onEvent(VideoRecordStatus.Recording(0L, false))
                is VideoRecordEvent.Status -> {
                    val durSec = recordEvent.recordingStats.recordedDurationNanos / 1_000_000_000L
                    onEvent(VideoRecordStatus.Recording(durSec, false))
                }
                is VideoRecordEvent.Pause -> {
                    val durSec = recordEvent.recordingStats.recordedDurationNanos / 1_000_000_000L
                    onEvent(VideoRecordStatus.Recording(durSec, true))
                }
                is VideoRecordEvent.Resume -> {
                    val durSec = recordEvent.recordingStats.recordedDurationNanos / 1_000_000_000L
                    onEvent(VideoRecordStatus.Recording(durSec, false))
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (!recordEvent.hasError()) {
                        onEvent(VideoRecordStatus.Saved(recordEvent.outputResults.outputUri.toString()))
                    } else {
                        onEvent(VideoRecordStatus.Idle)
                    }
                }
            }
        }
    }

    fun pauseOrResumeVideo(currentlyPaused: Boolean) {
        if (currentlyPaused) {
            activeRecording?.resume()
        } else {
            activeRecording?.pause()
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val planeProxy = image.planes[0]
            val buffer: ByteBuffer = planeProxy.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}

sealed class VideoRecordStatus {
    data object Idle : VideoRecordStatus()
    data class Recording(val durationSeconds: Long, val isPaused: Boolean) : VideoRecordStatus()
    data class Saved(val uriString: String) : VideoRecordStatus()
}
