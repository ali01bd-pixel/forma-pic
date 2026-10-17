package com.ali.formapic.ui.screens

import android.view.MotionEvent
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.ali.formapic.data.camera.VideoRecordStatus
import com.ali.formapic.data.preferences.FormaPicSettings
import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.MonochromeProfile
import com.ali.formapic.domain.model.RenderingPreference
import com.ali.formapic.ui.components.CompositionGridOverlay
import com.ali.formapic.ui.components.LiveHistogramHud
import com.ali.formapic.ui.components.ProBadgeChip
import com.ali.formapic.ui.components.ProManualControlDeck
import com.ali.formapic.ui.theme.*
import com.ali.formapic.ui.viewmodel.CameraUiState
import com.ali.formapic.ui.viewmodel.FormaPicViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    viewModel: FormaPicViewModel,
    cameraState: CameraUiState,
    settings: FormaPicSettings,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // Re-bind CameraX whenever lens facing, shooting mode, aspect ratio, or video quality changes
    LaunchedEffect(
        cameraState.lensFacing,
        cameraState.activeMode,
        settings.aspectRatio,
        settings.videoQuality
    ) {
        viewModel.cameraController.initializeAndBind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = cameraState.lensFacing,
            mode = cameraState.activeMode,
            aspectRatioOption = settings.aspectRatio,
            flashMode = cameraState.flashMode,
            videoQualityOption = settings.videoQuality
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // 1. Live CameraX Viewfinder
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        viewModel.cameraController.triggerTapToFocus(
                            previewView = previewView,
                            x = event.x,
                            y = event.y,
                            lockAeAf = cameraState.proParams.aeLocked
                        )
                        viewModel.onTapFocus(event.x, event.y)
                        true
                    } else false
                }
        )

        // 2. Composition Grid, Focus Peaking & Tap Focus Reticle Overlay
        CompositionGridOverlay(
            gridType = settings.gridOverlay,
            focusPeakPoints = if (cameraState.proParams.focusPeakingEnabled) {
                cameraState.telemetry.focusPeakPoints
            } else emptyList(),
            focusTapPoint = cameraState.focusIndicatorPoint
        )

        // 3. Front-Camera Selfie Screen Flash Illumination Overlay
        if (cameraState.screenFlashOverlayActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF8E7))
            )
        }

        // 4. Top DSLR Status & Quick-Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
                    .border(0.8.dp, TitaniumSilver.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand + Active Mode Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FORMA PIC",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = ChampagneGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(CharcoalElevated, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cameraState.activeMode.shortBadge,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PureWhite
                        )
                    }
                }

                // Flash Control
                IconButton(
                    onClick = { viewModel.cycleFlashMode() },
                    modifier = Modifier.size(32.dp)
                ) {
                    val flashIcon = when (cameraState.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash Mode",
                        tint = if (cameraState.flashMode == ImageCapture.FLASH_MODE_OFF) TitaniumSilver else ChampagneGold
                    )
                }

                // Rendering Profile Quick Selector (Natural / Vibrant / Professional)
                ProBadgeChip(
                    text = settings.renderingPreference.label.uppercase(),
                    active = settings.renderingPreference != RenderingPreference.NATURAL,
                    onClick = {
                        val nextPref = when (settings.renderingPreference) {
                            RenderingPreference.NATURAL -> RenderingPreference.VIBRANT
                            RenderingPreference.VIBRANT -> RenderingPreference.PROFESSIONAL
                            RenderingPreference.PROFESSIONAL -> RenderingPreference.NATURAL
                        }
                        // Persist preference
                        kotlinx.coroutines.MainScope().launch {
                            viewModel.preferencesRepo.updateRenderingPreference(nextPref)
                        }
                    }
                )

                // Pro Controls Drawer Toggle
                ProBadgeChip(
                    text = "PRO HUD",
                    active = cameraState.isProPanelExpanded,
                    onClick = { viewModel.toggleProPanel() }
                )

                // Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Smart Scene Recognition Badge + Live Histogram HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // AI Scene & Lens Optical Honesty Pill
                Column(
                    modifier = Modifier
                        .background(CharcoalSurface.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                        .border(0.8.dp, TitaniumSilver.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "AI SCENE: ${cameraState.telemetry.detectedScene.label.uppercase()}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ChampagneGold
                    )
                    val zoomTypeLabel = when {
                        cameraState.currentZoomRatio <= 1.05f -> "Optical Primary Lens"
                        cameraState.currentZoomRatio <= 2.0f -> "Lossless Sensor Crop"
                        else -> "Digital Zoom (${String.format("%.1fx", cameraState.currentZoomRatio)})"
                    }
                    Text(
                        text = "$zoomTypeLabel • ${settings.aspectRatio.label}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TitaniumSilver
                    )
                }

                if (cameraState.proParams.showHistogram || cameraState.isProPanelExpanded) {
                    LiveHistogramHud(
                        telemetry = cameraState.telemetry,
                        showZebraWarnings = cameraState.proParams.showZebraClipping
                    )
                }
            }
        }

        // 5. Center Timer Countdown or Multi-Frame Capture Progress Indicator
        if (cameraState.countdownRemainingSeconds > 0) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${cameraState.countdownRemainingSeconds}",
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChampagneGold
                )
            }
        }

        cameraState.captureProgressMessage?.let { progressMsg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CharcoalSurface.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
                    .border(1.dp, ChampagneGold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = ChampagneGold,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = progressMsg, color = PureWhite, fontSize = 13.sp)
                }
            }
        }

        cameraState.statusBannerMessage?.let { banner ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 130.dp)
                    .background(CharcoalElevated.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                    .border(1.dp, ChampagneGold.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = banner, color = SoftGold, fontSize = 12.sp)
            }
        }

        // 6. Bottom Controls Stack (Mode-specific controls, Pro Deck, Zoom Bar, Mode Carousel, Shutter Bar)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode-Specific Contextual Controls (Portrait Bokeh Slider, Monochrome Profiles, Video Timer)
            if (cameraState.activeMode == CameraMode.PORTRAIT) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .background(CharcoalSurface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aperture Bokeh f/${String.format("%.1f", 5.6f - cameraState.portraitBokehStrength * 4.2f)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ChampagneGold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        value = cameraState.portraitBokehStrength,
                        onValueChange = { viewModel.setPortraitBokehStrength(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = ChampagneGold, activeTrackColor = ChampagneGold)
                    )
                }
            }

            if (cameraState.activeMode == CameraMode.BLACK_AND_WHITE) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .background(CharcoalSurface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonochromeProfile.entries.forEach { profile ->
                        ProBadgeChip(
                            text = profile.label,
                            active = cameraState.activeMonochromeProfile == profile,
                            onClick = { viewModel.setMonochromeProfile(profile) }
                        )
                    }
                }
            }

            if (cameraState.activeMode.isVideoMode && cameraState.videoRecordStatus is VideoRecordStatus.Recording) {
                val recState = cameraState.videoRecordStatus
                Row(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .background(CrimsonRecord.copy(alpha = 0.90f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mins = recState.durationSeconds / 60
                    val secs = recState.durationSeconds % 60
                    Text(
                        text = String.format("● REC %02d:%02d", mins, secs),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (recState.isPaused) "RESUME" else "PAUSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoftGold,
                        modifier = Modifier.clickable {
                            viewModel.cameraController.pauseOrResumeVideo(recState.isPaused)
                        }
                    )
                }
            }

            // Expandable DSLR Pro Manual Deck
            AnimatedVisibility(visible = cameraState.isProPanelExpanded) {
                ProManualControlDeck(
                    params = cameraState.proParams,
                    capabilities = cameraState.capabilities,
                    onUpdate = { viewModel.updateProParameters(it) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Optical / Sensor Crop / Digital Zoom Selector Pill
            Row(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .background(CharcoalSurface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .border(0.8.dp, TitaniumSilver.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val zoomStops = buildList {
                    if (cameraState.capabilities.minZoomRatio < 0.9f) add(0.6f)
                    add(1.0f)
                    add(2.0f)
                    if (cameraState.capabilities.maxDigitalZoom >= 3.0f) add(3.0f)
                    if (cameraState.capabilities.maxDigitalZoom >= 5.0f) add(5.0f)
                }
                zoomStops.forEach { stop ->
                    val isSelected = kotlin.math.abs(cameraState.currentZoomRatio - stop) < 0.2f
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) ChampagneGold else CharcoalElevated)
                            .clickable { viewModel.setZoom(stop) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (stop < 1f) "${stop}x" else "${stop.toInt()}x",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) ObsidianBlack else PureWhite
                        )
                    }
                }
            }

            // Horizontal Photography Mode Selector Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CameraMode.entries.forEach { mode ->
                    val isSelected = cameraState.activeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) ChampagneGold.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ChampagneGold else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.selectCameraMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = mode.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                            color = if (isSelected) ChampagneGold else TitaniumSilver
                        )
                    }
                }
            }

            // Primary Bottom Shutter Row: Latest Gallery Thumbnail | Luxury Shutter Button | Camera Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalElevated)
                        .border(1.5.dp, TitaniumSilver.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenGallery),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraState.latestMediaUri != null) {
                        AsyncImage(
                            model = cameraState.latestMediaUri,
                            contentDescription = "Open Gallery",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = TitaniumSilver
                        )
                    }
                }

                // Luxury DSLR Shutter Button with Animated Scale
                val shutterScale by animateFloatAsState(
                    targetValue = if (cameraState.isCapturing) 0.88f else 1.0f,
                    label = "shutterScale"
                )
                val isVideoRecording = cameraState.videoRecordStatus is VideoRecordStatus.Recording

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(3.dp, ChampagneGold, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isVideoRecording -> CrimsonRecord
                                cameraState.activeMode.isVideoMode -> CrimsonRecord.copy(alpha = 0.85f)
                                else -> PureWhite
                            }
                        )
                        .clickable { viewModel.onShutterPressed() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideoRecording) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(PureWhite, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Switch Front / Rear Camera Button
                IconButton(
                    onClick = { viewModel.toggleLensFacing() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CharcoalElevated)
                        .border(1.dp, TitaniumSilver.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = PureWhite
                    )
                }
            }
        }
    }
}
