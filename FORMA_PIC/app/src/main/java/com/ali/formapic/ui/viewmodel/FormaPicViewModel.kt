package com.ali.formapic.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaActionSound
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ali.formapic.data.camera.FormaCameraController
import com.ali.formapic.data.camera.VideoRecordStatus
import com.ali.formapic.data.media.ExifMetadataDetails
import com.ali.formapic.data.media.FormaMediaItem
import com.ali.formapic.data.media.MediaGalleryRepository
import com.ali.formapic.data.preferences.FormaPicSettings
import com.ali.formapic.data.preferences.UserPreferencesRepository
import com.ali.formapic.domain.model.AspectRatioOption
import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.DeviceCameraCapabilities
import com.ali.formapic.domain.model.GridOverlayType
import com.ali.formapic.domain.model.LiveFrameTelemetry
import com.ali.formapic.domain.model.MonochromeProfile
import com.ali.formapic.domain.model.PhotoEditState
import com.ali.formapic.domain.model.ProManualParameters
import com.ali.formapic.domain.model.RenderingPreference
import com.ali.formapic.domain.model.VideoQualityOption
import com.ali.formapic.domain.processing.ComputationalPhotographyEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val activeMode: CameraMode = CameraMode.AI_AUTO,
    val lensFacing: Int = CameraCharacteristics.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val currentZoomRatio: Float = 1.0f,
    val capabilities: DeviceCameraCapabilities = DeviceCameraCapabilities(),
    val proParams: ProManualParameters = ProManualParameters(),
    val telemetry: LiveFrameTelemetry = LiveFrameTelemetry(),
    val isProPanelExpanded: Boolean = false,
    val isCapturing: Boolean = false,
    val countdownRemainingSeconds: Int = 0,
    val captureProgressMessage: String? = null,
    val screenFlashOverlayActive: Boolean = false,
    val portraitBokehStrength: Float = 0.65f,
    val activeMonochromeProfile: MonochromeProfile = MonochromeProfile.CLASSIC_SILVER,
    val panoramaFramesCaptured: Int = 0,
    val videoRecordStatus: VideoRecordStatus = VideoRecordStatus.Idle,
    val latestMediaUri: Uri? = null,
    val statusBannerMessage: String? = null,
    val focusIndicatorPoint: Pair<Float, Float>? = null
)

data class StudioEditorUiState(
    val sourceMediaItem: FormaMediaItem? = null,
    val originalBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val editState: PhotoEditState = PhotoEditState(),
    val isShowingBeforeOriginal: Boolean = false,
    val isProcessingEdit: Boolean = false,
    val isSavingExport: Boolean = false,
    val exifDetails: ExifMetadataDetails? = null
)

class FormaPicViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesRepo = UserPreferencesRepository(application)
    val galleryRepo = MediaGalleryRepository(application)
    private val mediaActionSound = runCatching { MediaActionSound() }.getOrNull()

    val settingsState: StateFlow<FormaPicSettings> = preferencesRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, FormaPicSettings())

    private val _cameraState = MutableStateFlow(CameraUiState())
    val cameraState: StateFlow<CameraUiState> = _cameraState.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<FormaMediaItem>>(emptyList())
    val galleryItems: StateFlow<List<FormaMediaItem>> = _galleryItems.asStateFlow()

    private val _editorState = MutableStateFlow(StudioEditorUiState())
    val editorState: StateFlow<StudioEditorUiState> = _editorState.asStateFlow()

    private val panoramaBuffer = mutableListOf<Bitmap>()

    val cameraController = FormaCameraController(
        context = application,
        scope = viewModelScope,
        onTelemetry = { telemetry ->
            _cameraState.update { it.copy(telemetry = telemetry) }
        },
        onCapabilitiesChanged = { caps ->
            _cameraState.update { it.copy(capabilities = caps) }
        },
        onCameraError = { msg ->
            showTransientStatus(msg)
        }
    )

    init {
        refreshGallery()
    }

    fun refreshGallery(searchQuery: String = "") {
        viewModelScope.launch {
            val items = galleryRepo.loadGalleryMedia(searchQuery)
            _galleryItems.value = items
            if (_cameraState.value.latestMediaUri == null && items.isNotEmpty()) {
                _cameraState.update { it.copy(latestMediaUri = items.first().uri) }
            }
        }
    }

    fun selectCameraMode(mode: CameraMode) {
        val currentCaps = _cameraState.value.capabilities
        if (mode == CameraMode.PRO_RAW && !currentCaps.supportsRawDng) {
            showTransientStatus("RAW DNG is unsupported on this lens; capturing maximum-quality 100% uncompressed pipeline JPEG.")
        }
        if (mode == CameraMode.MACRO && currentCaps.minimumFocusDistance < 4f) {
            showTransientStatus("Dedicated macro hardware not detected; optimizing closest supported focus distance.")
        }
        val newLensFacing = if (mode == CameraMode.SELFIE) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            _cameraState.value.lensFacing
        }
        panoramaBuffer.clear()
        _cameraState.update {
            it.copy(
                activeMode = mode,
                lensFacing = newLensFacing,
                isProPanelExpanded = mode == CameraMode.PRO_DSLR,
                panoramaFramesCaptured = 0
            )
        }
    }

    fun toggleLensFacing() {
        val nextFacing = if (_cameraState.value.lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        _cameraState.update { it.copy(lensFacing = nextFacing, currentZoomRatio = 1.0f) }
    }

    fun cycleFlashMode() {
        val nextFlash = when (_cameraState.value.flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        cameraController.setFlashMode(nextFlash)
        _cameraState.update { it.copy(flashMode = nextFlash) }
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(
            _cameraState.value.capabilities.minZoomRatio,
            _cameraState.value.capabilities.maxDigitalZoom
        )
        cameraController.setZoomRatio(clamped)
        _cameraState.update { it.copy(currentZoomRatio = clamped) }
    }

    fun toggleProPanel() {
        _cameraState.update { it.copy(isProPanelExpanded = !it.isProPanelExpanded) }
    }

    fun updateProParameters(updater: (ProManualParameters) -> ProManualParameters) {
        val updated = updater(_cameraState.value.proParams)
        _cameraState.update { it.copy(proParams = updated) }
        cameraController.applyProManualControls(updated, _cameraState.value.capabilities)
    }

    fun setPortraitBokehStrength(strength: Float) {
        _cameraState.update { it.copy(portraitBokehStrength = strength.coerceIn(0f, 1f)) }
    }

    fun setMonochromeProfile(profile: MonochromeProfile) {
        _cameraState.update { it.copy(activeMonochromeProfile = profile) }
    }

    fun onTapFocus(x: Float, y: Float) {
        _cameraState.update { it.copy(focusIndicatorPoint = x to y) }
        viewModelScope.launch {
            delay(1800)
            _cameraState.update { it.copy(focusIndicatorPoint = null) }
        }
    }

    /**
     * Executes photo capture or video toggle depending on the active CameraMode.
     */
    fun onShutterPressed() {
        val state = _cameraState.value
        val settings = settingsState.value

        if (state.activeMode.isVideoMode) {
            if (state.videoRecordStatus is VideoRecordStatus.Recording) {
                cameraController.stopVideoRecording()
            } else {
                cameraController.startVideoRecording(
                    enableAudio = settings.recordAudioInVideo,
                    prefix = settings.filenamePrefix
                ) { status ->
                    _cameraState.update { it.copy(videoRecordStatus = status) }
                    if (status is VideoRecordStatus.Saved) {
                        showTransientStatus("Video saved to Gallery")
                        refreshGallery()
                    }
                }
            }
            return
        }

        if (state.isCapturing) return

        viewModelScope.launch {
            // 1. Optional Shutter Timer Countdown
            if (settings.timerSeconds > 0) {
                for (sec in settings.timerSeconds downTo 1) {
                    _cameraState.update { it.copy(countdownRemainingSeconds = sec) }
                    delay(1000)
                }
                _cameraState.update { it.copy(countdownRemainingSeconds = 0) }
            }

            // 2. Selfie Screen Flash illumination if front camera + flash ON
            val useScreenFlash = state.lensFacing == CameraCharacteristics.LENS_FACING_FRONT &&
                state.flashMode == ImageCapture.FLASH_MODE_ON
            if (useScreenFlash) {
                _cameraState.update { it.copy(screenFlashOverlayActive = true) }
                delay(160)
            }

            if (settings.shutterSoundEnabled) {
                runCatching { mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK) }
            }

            _cameraState.update { it.copy(isCapturing = true) }

            try {
                when (state.activeMode) {
                    CameraMode.NIGHT, CameraMode.LONG_EXPOSURE -> {
                        val frameCount = if (state.activeMode == CameraMode.LONG_EXPOSURE) 5 else 4
                        val frames = mutableListOf<Bitmap>()
                        for (i in 1..frameCount) {
                            _cameraState.update {
                                it.copy(captureProgressMessage = "Hold steady... Capturing frame $i/$frameCount")
                            }
                            cameraController.captureSingleBitmap()?.let { frames.add(it) }
                            delay(140)
                        }
                        if (frames.isNotEmpty()) {
                            _cameraState.update { it.copy(captureProgressMessage = "Aligning & merging multi-frame stack...") }
                            val merged = ComputationalPhotographyEngine.mergeMultiFrameSequence(
                                frames = frames,
                                isLightTrailMode = state.activeMode == CameraMode.LONG_EXPOSURE
                            )
                            val finalBmp = ComputationalPhotographyEngine.processCapturedPhoto(
                                source = merged,
                                mode = state.activeMode,
                                renderingPreference = settings.renderingPreference,
                                processingStrength = settings.processingStrength,
                                detectedScene = state.telemetry.detectedScene,
                                targetAspectRatio = settings.aspectRatio
                            )
                            saveAndPublishBitmap(finalBmp, settings)
                        }
                    }

                    CameraMode.BURST_BEST_SHOT -> {
                        val totalBurst = settings.burstShotCount.coerceIn(3, 10)
                        val burstFrames = mutableListOf<Bitmap>()
                        for (i in 1..totalBurst) {
                            _cameraState.update {
                                it.copy(captureProgressMessage = "Burst capturing $i/$totalBurst...")
                            }
                            cameraController.captureSingleBitmap()?.let { burstFrames.add(it) }
                        }
                        if (burstFrames.isNotEmpty()) {
                            _cameraState.update { it.copy(captureProgressMessage = "AI selecting sharpest Best-Shot frame...") }
                            val bestIdx = ComputationalPhotographyEngine.selectSharpestBurstFrame(burstFrames)
                            val bestFrame = burstFrames[bestIdx]
                            val processed = ComputationalPhotographyEngine.processCapturedPhoto(
                                source = bestFrame,
                                mode = CameraMode.AI_AUTO,
                                renderingPreference = settings.renderingPreference,
                                processingStrength = settings.processingStrength,
                                detectedScene = state.telemetry.detectedScene,
                                targetAspectRatio = settings.aspectRatio
                            )
                            saveAndPublishBitmap(processed, settings)
                            showTransientStatus("Best Shot (#${bestIdx + 1} of ${burstFrames.size}) saved with peak sharpness")
                        }
                    }

                    CameraMode.PANORAMA -> {
                        val frame = cameraController.captureSingleBitmap()
                        if (frame != null) {
                            panoramaBuffer.add(frame)
                            val capturedCount = panoramaBuffer.size
                            if (capturedCount < 3) {
                                _cameraState.update {
                                    it.copy(
                                        panoramaFramesCaptured = capturedCount,
                                        captureProgressMessage = "Pan slowly right and tap shutter (${capturedCount}/3)"
                                    )
                                }
                            } else {
                                _cameraState.update { it.copy(captureProgressMessage = "Stitching 3-frame panorama...") }
                                val stitched = ComputationalPhotographyEngine.stitchPanoramaFrames(panoramaBuffer.toList())
                                panoramaBuffer.clear()
                                _cameraState.update { it.copy(panoramaFramesCaptured = 0) }
                                saveAndPublishBitmap(stitched, settings)
                                showTransientStatus("Seamless Panorama stitched and saved")
                            }
                        }
                    }

                    else -> {
                        _cameraState.update { it.copy(captureProgressMessage = "Processing ${state.activeMode.title}...") }
                        val rawBitmap = cameraController.captureSingleBitmap()
                        if (rawBitmap != null) {
                            val mirror = state.lensFacing == CameraCharacteristics.LENS_FACING_FRONT && settings.frontCameraMirror
                            val processed = ComputationalPhotographyEngine.processCapturedPhoto(
                                source = rawBitmap,
                                mode = state.activeMode,
                                renderingPreference = settings.renderingPreference,
                                processingStrength = settings.processingStrength,
                                detectedScene = state.telemetry.detectedScene,
                                monochromeProfile = state.activeMonochromeProfile,
                                portraitBokehStrength = state.portraitBokehStrength,
                                mirrorHorizontally = mirror,
                                targetAspectRatio = settings.aspectRatio
                            )
                            saveAndPublishBitmap(processed, settings)
                        }
                    }
                }
            } finally {
                _cameraState.update {
                    it.copy(
                        isCapturing = false,
                        captureProgressMessage = null,
                        screenFlashOverlayActive = false
                    )
                }
            }
        }
    }

    private suspend fun saveAndPublishBitmap(bitmap: Bitmap, settings: FormaPicSettings) {
        val uri = galleryRepo.saveCapturedBitmap(
            bitmap = bitmap,
            prefix = settings.filenamePrefix,
            subfolder = settings.saveSubfolder,
            isEditedCopy = false,
            useHeifIfSupported = settings.highEfficiencyHeif
        )
        if (uri != null) {
            _cameraState.update { it.copy(latestMediaUri = uri) }
            refreshGallery()
        }
    }

    // ---------------------------------------------------------
    // AI PHOTO ENHANCEMENT STUDIO ACTIONS
    // ---------------------------------------------------------

    fun openPhotoInStudio(item: FormaMediaItem) {
        viewModelScope.launch {
            _editorState.update { it.copy(isProcessingEdit = true, sourceMediaItem = item) }
            val bmp = galleryRepo.loadBitmapFromUri(item.uri, maxDimension = 2048)
            val exif = galleryRepo.readExifDetails(item.uri)
            val initialEditState = PhotoEditState(
                renderingPreference = settingsState.value.renderingPreference,
                processingStrength = settingsState.value.processingStrength
            )
            _editorState.update {
                it.copy(
                    originalBitmap = bmp,
                    previewBitmap = bmp,
                    editState = initialEditState,
                    isShowingBeforeOriginal = false,
                    isProcessingEdit = false,
                    exifDetails = exif
                )
            }
        }
    }

    fun updateStudioEditState(updater: (PhotoEditState) -> PhotoEditState) {
        val currentOrig = _editorState.value.originalBitmap ?: return
        val newEditState = updater(_editorState.value.editState)
        _editorState.update { it.copy(editState = newEditState, isProcessingEdit = true) }

        viewModelScope.launch {
            val updatedPreview = ComputationalPhotographyEngine.applyStudioPipeline(
                source = currentOrig,
                state = newEditState
            )
            _editorState.update {
                it.copy(
                    previewBitmap = updatedPreview,
                    isProcessingEdit = false
                )
            }
        }
    }

    fun applyStudioPreset(presetName: String) {
        updateStudioEditState { current ->
            when (presetName) {
                "AI One-Tap Enhance" -> current.copy(
                    aiAutoEnhanced = true,
                    exposure = 0.15f,
                    highlights = -22f,
                    shadows = 26f,
                    contrast = 12f,
                    vibrance = 18f,
                    sharpening = 24f,
                    activePresetName = presetName
                )
                "Golden Hour Cinema" -> current.copy(
                    aiAutoEnhanced = false,
                    temperature = 24f,
                    highlights = -25f,
                    shadows = 15f,
                    contrast = 14f,
                    saturation = 8f,
                    vignetteStrength = 25f,
                    activePresetName = presetName
                )
                "Editorial Portrait" -> current.copy(
                    aiAutoEnhanced = false,
                    exposure = 0.10f,
                    highlights = -18f,
                    shadows = 18f,
                    skinRetouchStrength = 30f,
                    backgroundBlurBokeh = 45f,
                    sharpening = 18f,
                    activePresetName = presetName
                )
                "Leica Monochrome" -> current.copy(
                    aiAutoEnhanced = false,
                    saturation = -100f,
                    contrast = 28f,
                    highlights = -15f,
                    blacks = -10f,
                    sharpening = 28f,
                    vignetteStrength = 20f,
                    activePresetName = presetName
                )
                else -> PhotoEditState(activePresetName = "Reset Original")
            }
        }
    }

    fun toggleBeforeAfterComparison(showOriginal: Boolean) {
        _editorState.update { it.copy(isShowingBeforeOriginal = showOriginal) }
    }

    fun saveEditedStudioPhoto(onSaved: () -> Unit) {
        val preview = _editorState.value.previewBitmap ?: return
        val settings = settingsState.value
        viewModelScope.launch {
            _editorState.update { it.copy(isSavingExport = true) }
            val uri = galleryRepo.saveCapturedBitmap(
                bitmap = preview,
                prefix = settings.filenamePrefix,
                subfolder = settings.saveSubfolder,
                isEditedCopy = true, // Preserves original photo untouched
                useHeifIfSupported = settings.highEfficiencyHeif,
                quality = 100
            )
            _editorState.update { it.copy(isSavingExport = false) }
            if (uri != null) {
                showTransientStatus("Saved full-resolution edited copy (Original preserved)")
                refreshGallery()
                onSaved()
            }
        }
    }

    fun deleteMedia(item: FormaMediaItem) {
        viewModelScope.launch {
            if (galleryRepo.deleteMediaItem(item.uri)) {
                showTransientStatus("Deleted ${item.displayName}")
                refreshGallery()
            }
        }
    }

    fun duplicateMedia(item: FormaMediaItem) {
        viewModelScope.launch {
            val bmp = galleryRepo.loadBitmapFromUri(item.uri)
            if (bmp != null) {
                galleryRepo.saveCapturedBitmap(
                    bitmap = bmp,
                    prefix = "FORMA_COPY_",
                    isEditedCopy = true
                )
                showTransientStatus("Duplicated ${item.displayName}")
                refreshGallery()
            }
        }
    }

    fun showTransientStatus(message: String) {
        _cameraState.update { it.copy(statusBannerMessage = message) }
        viewModelScope.launch {
            delay(3200)
            _cameraState.update {
                if (it.statusBannerMessage == message) it.copy(statusBannerMessage = null) else it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.shutdown()
        runCatching { mediaActionSound?.release() }
    }
}
