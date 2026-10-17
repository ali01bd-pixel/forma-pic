package com.ali.formapic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.formapic.domain.model.DeviceCameraCapabilities
import com.ali.formapic.domain.model.ProManualParameters
import com.ali.formapic.ui.theme.ChampagneGold
import com.ali.formapic.ui.theme.CharcoalElevated
import com.ali.formapic.ui.theme.CharcoalSurface
import com.ali.formapic.ui.theme.ObsidianBlack
import com.ali.formapic.ui.theme.PureWhite
import com.ali.formapic.ui.theme.TitaniumSilver
import kotlin.math.roundToInt

private enum class ProDialTab(val title: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    FOCUS("FOCUS"),
    WB("WB KELVIN"),
    EV("EV COMP")
}

@Composable
fun ProManualControlDeck(
    params: ProManualParameters,
    capabilities: DeviceCameraCapabilities,
    onUpdate: ((ProManualParameters) -> ProManualParameters) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ProDialTab.ISO) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CharcoalSurface.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .border(1.dp, ChampagneGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Top Auxiliary Toggles: RAW DNG, Focus Peaking, Histogram, AE/AF Lock
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProBadgeChip(
                text = if (capabilities.supportsRawDng) {
                    if (params.captureRawDng) "RAW+JPG ON" else "RAW DNG"
                } else "RAW N/A",
                active = params.captureRawDng && capabilities.supportsRawDng,
                onClick = {
                    if (capabilities.supportsRawDng) {
                        onUpdate { it.copy(captureRawDng = !it.captureRawDng) }
                    }
                }
            )
            ProBadgeChip(
                text = "PEAKING",
                active = params.focusPeakingEnabled,
                onClick = { onUpdate { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) } }
            )
            ProBadgeChip(
                text = "HISTOGRAM",
                active = params.showHistogram,
                onClick = { onUpdate { it.copy(showHistogram = !it.showHistogram) } }
            )
            ProBadgeChip(
                text = "ZEBRA",
                active = params.showZebraClipping,
                onClick = { onUpdate { it.copy(showZebraClipping = !it.showZebraClipping) } }
            )
            ProBadgeChip(
                text = if (params.aeLocked) "AE LOCKED" else "AE/AF LOCK",
                active = params.aeLocked,
                onClick = { onUpdate { it.copy(aeLocked = !it.aeLocked, afLocked = !it.afLocked) } }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Parameter Slider Row
        when (selectedTab) {
            ProDialTab.ISO -> {
                val minIso = capabilities.isoRange.lower.toFloat()
                val maxIso = capabilities.isoRange.upper.toFloat()
                ProSliderControl(
                    title = "ISO Sensitivity",
                    valueLabel = if (params.isIsoAuto) "AUTO" else "ISO ${params.isoValue}",
                    isAuto = params.isIsoAuto,
                    onToggleAuto = { onUpdate { it.copy(isIsoAuto = !it.isIsoAuto) } },
                    sliderValue = params.isoValue.toFloat().coerceIn(minIso, maxIso),
                    valueRange = minIso..maxIso,
                    onValueChange = { newVal ->
                        onUpdate { it.copy(isIsoAuto = false, isoValue = newVal.roundToInt()) }
                    }
                )
            }

            ProDialTab.SHUTTER -> {
                // Map slider 0..1 logarithmically from 1/8000s to 1s
                val shutterStops = listOf(
                    125_000L,      // 1/8000s
                    250_000L,      // 1/4000s
                    500_000L,      // 1/2000s
                    1_000_000L,    // 1/1000s
                    2_000_000L,    // 1/500s
                    4_000_000L,    // 1/250s
                    8_000_000L,    // 1/125s
                    16_666_666L,   // 1/60s
                    33_333_333L,   // 1/30s
                    66_666_666L,   // 1/15s
                    125_000_000L,  // 1/8s
                    250_000_000L,  // 1/4s
                    500_000_000L,  // 0.5s
                    1_000_000_000L // 1.0s
                )
                val currentIdx = shutterStops.indexOfFirst { it >= params.shutterSpeedNs }.coerceAtLeast(0)
                ProSliderControl(
                    title = "Shutter Speed",
                    valueLabel = params.formattedShutterSpeed(),
                    isAuto = params.isShutterAuto,
                    onToggleAuto = { onUpdate { it.copy(isShutterAuto = !it.isShutterAuto) } },
                    sliderValue = currentIdx.toFloat(),
                    valueRange = 0f..(shutterStops.lastIndex.toFloat()),
                    onValueChange = { idxFloat ->
                        val chosenNs = shutterStops[idxFloat.roundToInt().coerceIn(0, shutterStops.lastIndex)]
                        onUpdate { it.copy(isShutterAuto = false, shutterSpeedNs = chosenNs) }
                    }
                )
            }

            ProDialTab.FOCUS -> {
                val maxDiopter = capabilities.minimumFocusDistance.coerceAtLeast(10f)
                ProSliderControl(
                    title = "Manual Focus Distance",
                    valueLabel = if (params.isFocusAuto) "AF-C" else String.format("%.1f dpt", params.focusDistanceDiopters),
                    isAuto = params.isFocusAuto,
                    onToggleAuto = { onUpdate { it.copy(isFocusAuto = !it.isFocusAuto) } },
                    sliderValue = params.focusDistanceDiopters.coerceIn(0f, maxDiopter),
                    valueRange = 0f..maxDiopter,
                    onValueChange = { dpt ->
                        onUpdate { it.copy(isFocusAuto = false, focusDistanceDiopters = dpt) }
                    }
                )
            }

            ProDialTab.WB -> {
                ProSliderControl(
                    title = "White Balance Temperature",
                    valueLabel = if (params.isWhiteBalanceAuto) "AWB" else "${params.colorTemperatureKelvin}K",
                    isAuto = params.isWhiteBalanceAuto,
                    onToggleAuto = { onUpdate { it.copy(isWhiteBalanceAuto = !it.isWhiteBalanceAuto) } },
                    sliderValue = params.colorTemperatureKelvin.toFloat(),
                    valueRange = 2500f..8500f,
                    onValueChange = { kelvin ->
                        onUpdate { it.copy(isWhiteBalanceAuto = false, colorTemperatureKelvin = (kelvin / 100).roundToInt() * 100) }
                    }
                )
            }

            ProDialTab.EV -> {
                ProSliderControl(
                    title = "Exposure Compensation (EV)",
                    valueLabel = String.format("%+.1f EV", params.exposureCompensationEv),
                    isAuto = params.exposureCompensationEv == 0f,
                    onToggleAuto = { onUpdate { it.copy(exposureCompensationEv = 0f) } },
                    sliderValue = params.exposureCompensationEv,
                    valueRange = -2.0f..2.0f,
                    onValueChange = { ev ->
                        onUpdate { it.copy(exposureCompensationEv = (ev * 10).roundToInt() / 10f) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // DSLR Parameter Selector Dials
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProDialTab.entries.forEach { tab ->
                val valueSummary = when (tab) {
                    ProDialTab.ISO -> if (params.isIsoAuto) "AUTO" else "${params.isoValue}"
                    ProDialTab.SHUTTER -> params.formattedShutterSpeed()
                    ProDialTab.FOCUS -> if (params.isFocusAuto) "AF" else "MF"
                    ProDialTab.WB -> if (params.isWhiteBalanceAuto) "AWB" else "${params.colorTemperatureKelvin}K"
                    ProDialTab.EV -> String.format("%+.1f", params.exposureCompensationEv)
                }
                val isSelected = selectedTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CharcoalElevated else ObsidianBlack.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ChampagneGold else TitaniumSilver.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) ChampagneGold else TitaniumSilver
                    )
                    Text(
                        text = valueSummary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = PureWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun ProSliderControl(
    title: String,
    valueLabel: String,
    isAuto: Boolean,
    onToggleAuto: () -> Unit,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 12.sp, color = TitaniumSilver)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ChampagneGold
                )
                Spacer(modifier = Modifier.width(8.dp))
                ProBadgeChip(
                    text = "AUTO",
                    active = isAuto,
                    onClick = onToggleAuto
                )
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ChampagneGold,
                activeTrackColor = ChampagneGold,
                inactiveTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
fun ProBadgeChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) ChampagneGold else CharcoalElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (active) ObsidianBlack else PureWhite
        )
    }
}
