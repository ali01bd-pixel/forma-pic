package com.ali.formapic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.formapic.data.preferences.FormaPicSettings
import com.ali.formapic.domain.model.AspectRatioOption
import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.DeviceCameraCapabilities
import com.ali.formapic.domain.model.GridOverlayType
import com.ali.formapic.domain.model.RenderingPreference
import com.ali.formapic.domain.model.VideoQualityOption
import com.ali.formapic.ui.components.ProBadgeChip
import com.ali.formapic.ui.theme.*
import com.ali.formapic.ui.viewmodel.FormaPicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FormaPicViewModel,
    settings: FormaPicSettings,
    capabilities: DeviceCameraCapabilities,
    onOpenAboutUs: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = viewModel.preferencesRepo

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PureWhite)
                    }
                },
                title = {
                    Text("FORMA PIC SETTINGS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                },
                actions = {
                    TextButton(onClick = onOpenAboutUs) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ABOUT US", color = ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hardware Diagnostics Banner
            SettingsSectionCard(title = "Active Hardware & Sensor Capabilities") {
                Text("Hardware Level: ${capabilities.hardwareLevelName}", fontSize = 12.sp, color = PureWhite)
                Text(
                    text = "Manual Sensor: ${if (capabilities.supportsManualIso) "Supported (ISO ${capabilities.isoRange.lower}–${capabilities.isoRange.upper})" else "Auto Only"}",
                    fontSize = 12.sp,
                    color = TitaniumSilver
                )
                Text(
                    text = "RAW DNG Capture: ${if (capabilities.supportsRawDng) "Supported" else "Not supported by current lens"}",
                    fontSize = 12.sp,
                    color = TitaniumSilver
                )
                Text(
                    text = "Stabilization: ${if (capabilities.supportsOpticalStabilization) "Optical (OIS) + EIS" else if (capabilities.supportsVideoStabilization) "Electronic (EIS)" else "Standard"}",
                    fontSize = 12.sp,
                    color = TitaniumSilver
                )
            }

            // Photography & Rendering Preferences
            SettingsSectionCard(title = "Computational Photography & Rendering") {
                Text("Default Rendering Style", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenderingPreference.entries.forEach { pref ->
                        ProBadgeChip(
                            text = pref.label,
                            active = settings.renderingPreference == pref,
                            onClick = { scope.launch { repo.updateRenderingPreference(pref) } }
                        )
                    }
                }
                Text(settings.renderingPreference.description, fontSize = 11.sp, color = TitaniumSilver, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "AI Processing Strength: ${(settings.processingStrength * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = PureWhite
                )
                Slider(
                    value = settings.processingStrength,
                    onValueChange = { scope.launch { repo.updateProcessingStrength(it) } },
                    colors = SliderDefaults.colors(thumbColor = ChampagneGold, activeTrackColor = ChampagneGold)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text("Aspect Ratio", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatioOption.entries.forEach { ratio ->
                        ProBadgeChip(
                            text = ratio.label,
                            active = settings.aspectRatio == ratio,
                            onClick = { scope.launch { repo.updateAspectRatio(ratio) } }
                        )
                    }
                }
            }

            // Composition Grid, Timer & Burst
            SettingsSectionCard(title = "Viewfinder, Grid, Timer & Burst") {
                Text("Composition Grid Overlay", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GridOverlayType.entries.forEach { grid ->
                        ProBadgeChip(
                            text = grid.name.take(6),
                            active = settings.gridOverlay == grid,
                            onClick = { scope.launch { repo.updateGridOverlay(grid) } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Shutter Timer", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 5, 10).forEach { sec ->
                        ProBadgeChip(
                            text = if (sec == 0) "OFF" else "${sec}s",
                            active = settings.timerSeconds == sec,
                            onClick = { scope.launch { repo.updateTimerSeconds(sec) } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Burst Sequence Frame Count", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { cnt ->
                        ProBadgeChip(
                            text = "$cnt Frames",
                            active = settings.burstShotCount == cnt,
                            onClick = { scope.launch { repo.updateBurstCount(cnt) } }
                        )
                    }
                }
            }

            // Video Recording Configuration (Queried from Hardware)
            SettingsSectionCard(title = "Video Recording (Hardware Verified)") {
                Text("Supported Video Resolution", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    capabilities.supportedVideoQualities.forEach { q ->
                        ProBadgeChip(
                            text = "${q.label} (~${q.approxMbPerMinute}MB/m)",
                            active = settings.videoQuality == q,
                            onClick = { scope.launch { repo.updateVideoQuality(q) } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Supported Frame Rate", fontSize = 13.sp, color = PureWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    capabilities.supportedFrameRates.forEach { fps ->
                        ProBadgeChip(
                            text = "${fps} FPS",
                            active = settings.videoFrameRate == fps,
                            onClick = { scope.launch { repo.updateVideoFps(fps) } }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = "Record Microphone Audio",
                    subtitle = "Requests microphone permission only when enabled",
                    checked = settings.recordAudioInVideo,
                    onCheckedChange = { scope.launch { repo.updateRecordAudio(it) } }
                )
            }

            // Storage, Privacy & Toggles
            SettingsSectionCard(title = "Storage, Formats & Privacy") {
                SettingsSwitchRow(
                    title = "Preserve Original Photo When Editing",
                    subtitle = "Exports edited photos as new copies (_EDIT) without overwriting originals",
                    checked = settings.keepOriginalWhenEdited,
                    onCheckedChange = { scope.launch { repo.updateKeepOriginal(it) } }
                )
                SettingsSwitchRow(
                    title = "Shutter Sound",
                    subtitle = "Play DSLR acoustic shutter click on capture",
                    checked = settings.shutterSoundEnabled,
                    onCheckedChange = { scope.launch { repo.updateShutterSound(it) } }
                )
                SettingsSwitchRow(
                    title = "Mirror Front Camera Selfies",
                    subtitle = "Save natural mirrored perspective on front camera",
                    checked = settings.frontCameraMirror,
                    onCheckedChange = { scope.launch { repo.updateFrontMirror(it) } }
                )
                SettingsSwitchRow(
                    title = "Geotagging (EXIF Location)",
                    subtitle = "Disabled by default for privacy; requests GPS only if enabled",
                    checked = settings.geotaggingEnabled,
                    onCheckedChange = { scope.launch { repo.updateGeotagging(it) } }
                )
            }

            // About Us Navigation Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalElevated)
                    .border(1.dp, ChampagneGold, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenAboutUs)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("FORMA PIC — Version 1.0.0", fontWeight = FontWeight.Bold, color = ChampagneGold, fontSize = 15.sp)
                        Text("Created by ALI • Tap to view About Us & Privacy Policy", color = PureWhite, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.Info, contentDescription = null, tint = ChampagneGold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CharcoalSurface, RoundedCornerShape(14.dp))
            .border(0.8.dp, TitaniumSilver.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = ChampagneGold
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.Medium)
            Text(text = subtitle, fontSize = 11.sp, color = TitaniumSilver)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold
            )
        )
    }
}

/**
 * Dedicated About Us Screen displaying the exact required brand information:
 * FORMA PIC
 * Professional Photography. Simplified.
 * Created by ALI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PureWhite)
                    }
                },
                title = {
                    Text("ABOUT US", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branded Luxury Lens Emblem
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(CharcoalSurface)
                    .border(2.5.dp, ChampagneGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraEnhance,
                    contentDescription = "FORMA PIC Emblem",
                    tint = ChampagneGold,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Exact Required Brand Information (Section 12)
            Text(
                text = "FORMA PIC",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                color = PureWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Professional Photography. Simplified.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ChampagneGold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(CharcoalElevated)
                    .border(1.dp, ChampagneGold, RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Created by ALI",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = SoftGold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Version 1.0.0 (API 28 – API 36)",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = TitaniumSilver
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Application Description Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, TitaniumSilver.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "APPLICATION OVERVIEW",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ChampagneGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "FORMA PIC combines AndroidX CameraX, Camera2 manual sensor controls, multi-frame computational photography, and a non-destructive AI Photo Enhancement Studio into an intuitive DSLR-inspired interface. Designed to deliver authentic detail, accurate skin tones, and balanced dynamic range across compatible Android devices.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = PureWhite
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // On-Device Privacy Policy Entry Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, ChampagneGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRIVACY POLICY & ON-DEVICE GUARANTEE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ChampagneGold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 100% On-Device Processing: All photo capture, HDR tone mapping, Night multi-frame stacking, portrait bokeh, and AI Studio adjustments run locally on your device.
" +
                        "• Zero Cloud Uploads: FORMA PIC never transmits, collects, or uploads your photos, videos, or personal metadata to external servers.
" +
                        "• Minimal Permissions: Camera access is used strictly for capturing media. Microphone access is requested only when audio video recording is enabled, and location is never accessed unless geotagging is explicitly turned on by you.",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = TitaniumSilver
                )
            }
        }
    }
}
