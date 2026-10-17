package com.ali.formapic.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.formapic.domain.model.AspectRatioOption
import com.ali.formapic.domain.model.PhotoEditState
import com.ali.formapic.ui.components.ProBadgeChip
import com.ali.formapic.ui.theme.*
import com.ali.formapic.ui.viewmodel.FormaPicViewModel
import com.ali.formapic.ui.viewmodel.StudioEditorUiState

private enum class StudioCategory(val label: String) {
    PRESETS("AI & Presets"),
    LIGHT("Light & Tone"),
    COLOR("Color & WB"),
    DETAIL("Detail & Bokeh"),
    PORTRAIT("Portrait & Skin"),
    GEOMETRY("Crop & Rotate")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioEditorScreen(
    viewModel: FormaPicViewModel,
    editorState: StudioEditorUiState,
    onNavigateBack: () -> Unit
) {
    var activeCategory by remember { mutableStateOf(StudioCategory.PRESETS) }
    val edit = editorState.editState

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
                    Column {
                        Text("AI PHOTO STUDIO", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ChampagneGold)
                        Text(
                            text = if (editorState.isShowingBeforeOriginal) "Viewing: ORIGINAL" else "Non-Destructive Preview",
                            fontSize = 11.sp,
                            color = TitaniumSilver
                        )
                    }
                },
                actions = {
                    // Hold to Compare Original vs Edited
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (editorState.isShowingBeforeOriginal) ChampagneGold else CharcoalElevated)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.toggleBeforeAfterComparison(true)
                                        tryAwaitRelease()
                                        viewModel.toggleBeforeAfterComparison(false)
                                    }
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Compare,
                                contentDescription = "Compare",
                                tint = if (editorState.isShowingBeforeOriginal) ObsidianBlack else PureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COMPARE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (editorState.isShowingBeforeOriginal) ObsidianBlack else PureWhite
                            )
                        }
                    }

                    // Reset Button
                    IconButton(onClick = { viewModel.applyStudioPreset("Reset Original") }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TitaniumSilver)
                    }

                    // Export Copy Button (Never overwrites original)
                    Button(
                        onClick = { viewModel.saveEditedStudioPhoto(onSaved = onNavigateBack) },
                        enabled = !editorState.isSavingExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneGold,
                            contentColor = ObsidianBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE COPY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Interactive Image Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ObsidianBlack)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val displayBmp = if (editorState.isShowingBeforeOriginal) {
                    editorState.originalBitmap
                } else {
                    editorState.previewBitmap
                }

                if (displayBmp != null) {
                    Image(
                        bitmap = displayBmp.asImageBitmap(),
                        contentDescription = "Edited Photo Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (editorState.isProcessingEdit || editorState.isSavingExport) {
                    CircularProgressIndicator(color = ChampagneGold)
                }
            }

            // 2. Category Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioCategory.entries.forEach { category ->
                    ProBadgeChip(
                        text = category.label.uppercase(),
                        active = activeCategory == category,
                        onClick = { activeCategory = category }
                    )
                }
            }

            // 3. Parameter Sliders Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(CharcoalSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeCategory) {
                    StudioCategory.PRESETS -> {
                        Text(
                            text = "One-Tap Computational AI & Color Grading Presets",
                            fontSize = 12.sp,
                            color = ChampagneGold,
                            fontWeight = FontWeight.SemiBold
                        )
                        val presets = listOf(
                            "AI One-Tap Enhance",
                            "Golden Hour Cinema",
                            "Editorial Portrait",
                            "Leica Monochrome",
                            "Reset Original"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            presets.forEach { preset ->
                                val selected = edit.activePresetName == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) ChampagneGold else CharcoalElevated)
                                        .border(1.dp, ChampagneGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable { viewModel.applyStudioPreset(preset) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (preset.startsWith("AI")) {
                                            Icon(
                                                Icons.Default.AutoFixHigh,
                                                contentDescription = null,
                                                tint = if (selected) ObsidianBlack else ChampagneGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = preset,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) ObsidianBlack else PureWhite
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        StudioSliderRow(
                            label = "Vignette Optical Falloff",
                            value = edit.vignetteStrength,
                            range = 0f..100f
                        ) { v -> viewModel.updateStudioEditState { it.copy(vignetteStrength = v) } }
                    }

                    StudioCategory.LIGHT -> {
                        StudioSliderRow("Exposure (EV)", edit.exposure, -2f..2f) { v ->
                            viewModel.updateStudioEditState { it.copy(exposure = v) }
                        }
                        StudioSliderRow("Brightness", edit.brightness, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(brightness = v) }
                        }
                        StudioSliderRow("Contrast", edit.contrast, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(contrast = v) }
                        }
                        StudioSliderRow("Highlights Recovery", edit.highlights, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(highlights = v) }
                        }
                        StudioSliderRow("Shadows Lift", edit.shadows, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(shadows = v) }
                        }
                        StudioSliderRow("Whites Point", edit.whites, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(whites = v) }
                        }
                        StudioSliderRow("Blacks Depth", edit.blacks, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(blacks = v) }
                        }
                    }

                    StudioCategory.COLOR -> {
                        StudioSliderRow("Color Temperature (Cool/Warm)", edit.temperature, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(temperature = v) }
                        }
                        StudioSliderRow("Tint (Green/Magenta)", edit.tint, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(tint = v) }
                        }
                        StudioSliderRow("Saturation", edit.saturation, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(saturation = v) }
                        }
                        StudioSliderRow("Skin-Safe Vibrance", edit.vibrance, -100f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(vibrance = v) }
                        }
                    }

                    StudioCategory.DETAIL -> {
                        StudioSliderRow("Natural Detail Sharpening", edit.sharpening, 0f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(sharpening = v) }
                        }
                        StudioSliderRow("Luminance Noise Reduction", edit.noiseReduction, 0f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(noiseReduction = v) }
                        }
                        StudioSliderRow("Subject-Aware Bokeh Blur", edit.backgroundBlurBokeh, 0f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(backgroundBlurBokeh = v) }
                        }
                    }

                    StudioCategory.PORTRAIT -> {
                        Text(
                            text = "Identity-Safe Retouching (Preserves natural skin texture & facial geometry)",
                            fontSize = 11.sp,
                            color = TitaniumSilver
                        )
                        StudioSliderRow("Natural Skin Texture Refinement", edit.skinRetouchStrength, 0f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(skinRetouchStrength = v) }
                        }
                        StudioSliderRow("Portrait Background Blur", edit.backgroundBlurBokeh, 0f..100f) { v ->
                            viewModel.updateStudioEditState { it.copy(backgroundBlurBokeh = v) }
                        }
                    }

                    StudioCategory.GEOMETRY -> {
                        StudioSliderRow("Straighten / Rotate Angle", edit.rotationDegrees, -45f..45f) { v ->
                            viewModel.updateStudioEditState { it.copy(rotationDegrees = v) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AspectRatioOption.entries.forEach { ratio ->
                                ProBadgeChip(
                                    text = ratio.label,
                                    active = edit.cropAspectRatio == ratio,
                                    onClick = {
                                        viewModel.updateStudioEditState { it.copy(cropAspectRatio = ratio) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = PureWhite)
            Text(
                text = String.format("%.1f", value),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = ChampagneGold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ChampagneGold,
                activeTrackColor = ChampagneGold,
                inactiveTrackColor = CharcoalElevated
            )
        )
    }
}
