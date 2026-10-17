package com.ali.formapic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.formapic.domain.model.GridOverlayType
import com.ali.formapic.domain.model.LiveFrameTelemetry
import com.ali.formapic.ui.theme.ChampagneGold
import com.ali.formapic.ui.theme.CharcoalSurface
import com.ali.formapic.ui.theme.CrimsonRecord
import com.ali.formapic.ui.theme.EmeraldPeak
import com.ali.formapic.ui.theme.PureWhite
import com.ali.formapic.ui.theme.TitaniumSilver

@Composable
fun CompositionGridOverlay(
    gridType: GridOverlayType,
    focusPeakPoints: List<Pair<Float, Float>>,
    focusTapPoint: Pair<Float, Float>?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.30f)
        val strokeWidth = 1.dp.toPx()

        when (gridType) {
            GridOverlayType.NONE -> Unit
            GridOverlayType.RULE_OF_THIRDS -> {
                for (i in 1..2) {
                    val x = w * (i / 3f)
                    val y = h * (i / 3f)
                    drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth)
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth)
                }
            }
            GridOverlayType.GOLDEN_RATIO -> {
                val phi1 = 0.382f
                val phi2 = 0.618f
                drawLine(ChampagneGold.copy(alpha = 0.38f), Offset(w * phi1, 0f), Offset(w * phi1, h), strokeWidth)
                drawLine(ChampagneGold.copy(alpha = 0.38f), Offset(w * phi2, 0f), Offset(w * phi2, h), strokeWidth)
                drawLine(ChampagneGold.copy(alpha = 0.38f), Offset(0f, h * phi1), Offset(w, h * phi1), strokeWidth)
                drawLine(ChampagneGold.copy(alpha = 0.38f), Offset(0f, h * phi2), Offset(w, h * phi2), strokeWidth)
            }
            GridOverlayType.SQUARE_FRAME -> {
                val side = minOf(w, h)
                val top = (h - side) / 2f
                val left = (w - side) / 2f
                drawRect(
                    color = ChampagneGold.copy(alpha = 0.55f),
                    topLeft = Offset(left, top),
                    size = Size(side, side),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            GridOverlayType.DIAGONAL_CROSS -> {
                drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeWidth)
                drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeWidth)
                drawCircle(ChampagneGold.copy(alpha = 0.5f), radius = 16.dp.toPx(), center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
            }
        }

        // Draw Live Focus Peaking Highlights (Emerald green edge indicators)
        for ((nx, ny) in focusPeakPoints) {
            drawCircle(
                color = EmeraldPeak.copy(alpha = 0.85f),
                radius = 2.2.dp.toPx(),
                center = Offset(nx * w, ny * h)
            )
        }

        // Draw Tap-to-Focus & Exposure DSLR Reticle
        focusTapPoint?.let { (fx, fy) ->
            val boxSize = 68.dp.toPx()
            val half = boxSize / 2f
            val cx = fx.coerceIn(half, w - half)
            val cy = fy.coerceIn(half, h - half)
            drawRect(
                color = ChampagneGold,
                topLeft = Offset(cx - half, cy - half),
                size = Size(boxSize, boxSize),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = ChampagneGold,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
fun LiveHistogramHud(
    telemetry: LiveFrameTelemetry,
    showZebraWarnings: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(138.dp)
            .background(CharcoalSurface.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
            .border(0.8.dp, TitaniumSilver.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HISTOGRAM",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ChampagneGold
            )
            Text(
                text = "EV ${String.format("%.0f", telemetry.averageLuminance)}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TitaniumSilver
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            val hist = telemetry.luminanceHistogram
            val maxVal = (hist.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val binWidth = size.width / hist.size.toFloat()

            val path = Path()
            path.moveTo(0f, size.height)
            hist.forEachIndexed { idx, count ->
                val normH = (count / maxVal).coerceIn(0f, 1f) * size.height
                path.lineTo(idx * binWidth, size.height - normH)
            }
            path.lineTo(size.width, size.height)
            path.close()

            drawPath(path, color = PureWhite.copy(alpha = 0.45f))
            drawPath(path, color = ChampagneGold, style = Stroke(width = 1.2.dp.toPx()))
        }

        if (showZebraWarnings) {
            val hiPct = (telemetry.highlightClippedRatio * 100f).toInt()
            val loPct = (telemetry.shadowCrushedRatio * 100f).toInt()
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SHD $loPct%",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (loPct > 15) ChampagneGold else TitaniumSilver
                )
                Text(
                    text = "CLIP $hiPct%",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (hiPct > 5) CrimsonRecord else TitaniumSilver
                )
            }
        }
    }
}
