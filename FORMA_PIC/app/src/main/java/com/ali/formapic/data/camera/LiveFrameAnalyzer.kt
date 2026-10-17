package com.ali.formapic.data.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ali.formapic.domain.model.DetectedScene
import com.ali.formapic.domain.model.LiveFrameTelemetry
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time ImageAnalysis.Analyzer that processes YUV_420_888 camera frames at low latency
 * to compute:
 * 1. 64-bin Luminance & RGB histograms
 * 2. Highlight clipping & shadow crushing ratios (for Pro Mode Zebra warnings)
 * 3. Sobel edge gradient energy for Focus Peaking coordinates & sharpness scoring (Burst Best-Shot)
 * 4. Smart Scene Classification (Low Light, High Dynamic Range, Portrait/Warm, Foliage/Sky, Culinary)
 */
class LiveFrameAnalyzer(
    private val computeFocusPeaking: () -> Boolean,
    private val onTelemetryUpdated: (LiveFrameTelemetry) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val minIntervalMs = 110L // ~9 FPS telemetry updates for smooth UI without thermal drain

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestamp < minIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = now

        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                image.close()
                return
            }

            val yBuffer = planes[0].buffer
            val yRowStride = planes[0].rowStride
            val yPixelStride = planes[0].pixelStride

            val uBuffer = if (planes.size > 1) planes[1].buffer else null
            val vBuffer = if (planes.size > 2) planes[2].buffer else null
            val uvRowStride = if (planes.size > 1) planes[1].rowStride else 0
            val uvPixelStride = if (planes.size > 1) planes[1].pixelStride else 0

            val width = image.width
            val height = image.height

            val lumHist = IntArray(64)
            val rHist = IntArray(64)
            val gHist = IntArray(64)
            val bHist = IntArray(64)

            var totalLum = 0L
            var sampleCount = 0
            var highlightClipCount = 0
            var shadowCrushCount = 0
            var warmSkinPixelCount = 0
            var skyBluePixelCount = 0
            var foliageGreenPixelCount = 0

            val step = 8 // Subsample grid for real-time speed
            val peakingEnabled = computeFocusPeaking()
            val peakPoints = if (peakingEnabled) ArrayList<Pair<Float, Float>>(240) else emptyList<Pair<Float, Float>>()
            var totalGradientSum = 0.0

            yBuffer.rewind()
            uBuffer?.rewind()
            vBuffer?.rewind()

            val yLimit = yBuffer.limit()
            val uLimit = uBuffer?.limit() ?: 0
            val vLimit = vBuffer?.limit() ?: 0

            for (row in step until (height - step) step step) {
                val rowOffset = row * yRowStride
                val uvRowOffset = (row / 2) * uvRowStride

                for (col in step until (width - step) step step) {
                    val yIndex = rowOffset + col * yPixelStride
                    if (yIndex >= yLimit) continue

                    val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                    val bin = (yVal shr 2).coerceIn(0, 63)
                    lumHist[bin]++
                    totalLum += yVal
                    sampleCount++

                    if (yVal >= 248) highlightClipCount++
                    if (yVal <= 7) shadowCrushCount++

                    // Approximate RGB from YUV for chroma histograms & smart scene detection
                    var uVal = 128
                    var vVal = 128
                    if (uBuffer != null && vBuffer != null) {
                        val uvIndex = uvRowOffset + (col / 2) * uvPixelStride
                        if (uvIndex < uLimit && uvIndex < vLimit) {
                            uVal = uBuffer.get(uvIndex).toInt() and 0xFF
                            vVal = vBuffer.get(uvIndex).toInt() and 0xFF
                        }
                    }

                    val c = yVal - 16
                    val d = uVal - 128
                    val e = vVal - 128
                    val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                    rHist[r shr 2]++
                    gHist[g shr 2]++
                    bHist[b shr 2]++

                    // Scene heuristics
                    if (r > g && g > b && r in 95..235 && (r - b) in 25..110) {
                        warmSkinPixelCount++
                    } else if (b > r + 25 && b > g + 10 && row < height / 2) {
                        skyBluePixelCount++
                    } else if (g > r + 15 && g > b + 15) {
                        foliageGreenPixelCount++
                    }

                    // Compute Sobel gradient for sharpness score & Focus Peaking
                    val leftIdx = rowOffset + (col - 2) * yPixelStride
                    val rightIdx = rowOffset + (col + 2) * yPixelStride
                    val topIdx = (row - 2) * yRowStride + col * yPixelStride
                    val bottomIdx = (row + 2) * yRowStride + col * yPixelStride

                    if (leftIdx >= 0 && rightIdx < yLimit && topIdx >= 0 && bottomIdx < yLimit) {
                        val gx = (yBuffer.get(rightIdx).toInt() and 0xFF) - (yBuffer.get(leftIdx).toInt() and 0xFF)
                        val gy = (yBuffer.get(bottomIdx).toInt() and 0xFF) - (yBuffer.get(topIdx).toInt() and 0xFF)
                        val grad = sqrt((gx * gx + gy * gy).toDouble())
                        totalGradientSum += grad

                        if (peakingEnabled && grad > 68.0 && (peakPoints as ArrayList).size < 240) {
                            peakPoints.add((col.toFloat() / width.toFloat()) to (row.toFloat() / height.toFloat()))
                        }
                    }
                }
            }

            val validSamples = sampleCount.coerceAtLeast(1)
            val avgLum = totalLum.toFloat() / validSamples
            val highlightRatio = highlightClipCount.toFloat() / validSamples
            val shadowRatio = shadowCrushCount.toFloat() / validSamples
            val sharpness = (totalGradientSum / validSamples).toFloat()

            val warmRatio = warmSkinPixelCount.toFloat() / validSamples
            val skyRatio = skyBluePixelCount.toFloat() / validSamples
            val foliageRatio = foliageGreenPixelCount.toFloat() / validSamples

            val detectedScene = when {
                avgLum < 46f -> DetectedScene.LOW_LIGHT
                highlightRatio > 0.08f && shadowRatio > 0.12f -> DetectedScene.HIGH_CONTRAST
                skyRatio > 0.18f && foliageRatio > 0.10f -> DetectedScene.LANDSCAPE
                foliageRatio > 0.24f -> DetectedScene.FLORA
                warmRatio > 0.28f && sharpness > 22f -> DetectedScene.FOOD
                warmRatio > 0.16f -> DetectedScene.PORTRAIT
                else -> DetectedScene.NEUTRAL
            }

            onTelemetryUpdated(
                LiveFrameTelemetry(
                    luminanceHistogram = lumHist,
                    rHistogram = rHist,
                    gHistogram = gHist,
                    bHistogram = bHist,
                    highlightClippedRatio = highlightRatio,
                    shadowCrushedRatio = shadowRatio,
                    averageLuminance = avgLum,
                    sharpnessScore = sharpness,
                    detectedScene = detectedScene,
                    focusPeakPoints = peakPoints
                )
            )
        } catch (_: Exception) {
            // Safely ignore transient buffer state changes during camera switching
        } finally {
            image.close()
        }
    }
}
