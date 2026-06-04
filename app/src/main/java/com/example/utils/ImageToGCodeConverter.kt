package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

object ImageToGCodeConverter {

    enum class ProcessMode {
        RASTER,     // Grayscale Line Engraving (dynamic S laser power)
        DITHER,     // Dithered dot patterns (pulsed or drilled dots)
        OUTLINE     // Simple edge-triggered threshold line tracing
    }

    /**
     * Converts a source android Bitmap into a custom series of G-code commands or
     * vector paths for preview/export.
     * Dimensions are scaled to fit targetWidthMm and targetHeightMm.
     */
    fun processImage(
        bitmap: Bitmap,
        targetWidthMm: Float,
        targetHeightMm: Float,
        mode: ProcessMode,
        resolutionDpmm: Float = 2.0f, // dots per mm (e.g. 2 means 1 pixel = 0.5mm)
        threshold: Float = 0.5f,      // B/W threshold
        invertColors: Boolean = false
    ): ImageProcessResult {
        // 1. Calculate desired size in pixels based on resolution and physical dimensions
        val desiredWidthPx = (targetWidthMm * resolutionDpmm).toInt().coerceAtLeast(10).coerceAtMost(300)
        val desiredHeightPx = (targetHeightMm * resolutionDpmm).toInt().coerceAtLeast(10).coerceAtMost(300)

        // Scale bitmap down to a manageable size to prevent memory errors and keep G-code generation fast
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, desiredWidthPx, desiredHeightPx, true)
        val width = scaledBitmap.width
        val height = scaledBitmap.height

        val dX = targetWidthMm / width.toFloat()
        val dY = targetHeightMm / height.toFloat()

        val generatedGcode = StringBuilder()
        val previewPaths = mutableListOf<List<Offset>>() // paths for live canvas preview

        when (mode) {
            ProcessMode.RASTER -> {
                // Horizontal Boustrophedon (snake-scan) raster engraving
                // We scan left-to-right, then right-to-left, to save travel time.
                for (y in 0 until height) {
                    val actualYMm = (height - 1 - y) * dY // transform system Y downwards to cartesian Y upwards for standard CNC coordinate space
                    val isLeftToRight = (y % 2 == 0)

                    val xRange = if (isLeftToRight) 0 until width else (width - 1) downTo 0
                    var isLaserActive = false
                    val currentLinePath = mutableListOf<Offset>()

                    for (x in xRange) {
                        val pixel = scaledBitmap.getPixel(x, y)
                        var brightness = getGrayscale(pixel)
                        if (invertColors) brightness = 1.0f - brightness

                        // Threshold to see if we should engraves. We engrave dark parts (brightness close to 0)
                        val isDark = brightness < 0.95f // If pixel is 95% white, skip it (no cut needed)

                        val actualXMm = x * dX

                        if (isDark) {
                            val powerScalar = 1.0f - brightness // dark is full power
                            if (!isLaserActive) {
                                // Travel G0 to start position
                                generatedGcode.append("G0 X${formatCoordinates(actualXMm)} Y${formatCoordinates(actualYMm)}\n")
                                isLaserActive = true
                                currentLinePath.clear()
                            }
                            // G1 Cut with laser power S scaled by powerScalar (0.0 to 1000)
                            val sVal = (powerScalar * 1000).toInt().coerceIn(100, 1000)
                            generatedGcode.append("G1 X${formatCoordinates(actualXMm)} Y${formatCoordinates(actualYMm)} S$sVal\n")
                            currentLinePath.add(Offset(actualXMm, actualYMm))
                        } else {
                            if (isLaserActive) {
                                // Turn off laser G1 S0
                                generatedGcode.append("G1 S0\n")
                                isLaserActive = false
                                if (currentLinePath.isNotEmpty()) {
                                    previewPaths.add(currentLinePath.toList())
                                }
                            }
                        }
                    }
                    if (isLaserActive && currentLinePath.isNotEmpty()) {
                        generatedGcode.append("G1 S0\n")
                        previewPaths.add(currentLinePath.toList())
                    }
                }
            }

            ProcessMode.DITHER -> {
                // Simple ordered Dithering / Floyd-Steinberg error approximation
                // We scan and produce independent drill/lasering coordinates
                val matrix = Array(height) { FloatArray(width) }
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = scaledBitmap.getPixel(x, y)
                        var b = getGrayscale(pixel)
                        if (invertColors) b = 1.0f - b
                        matrix[y][x] = b
                    }
                }

                // Apply simple 2D half-tone / dither step
                val currentPathPoints = mutableListOf<Offset>()
                for (y in 0 until height) {
                    val actualYMm = (height - 1 - y) * dY
                    val isLeftToRight = (y % 2 == 0)
                    val xRange = if (isLeftToRight) 0 until width else (width - 1) downTo 0

                    for (x in xRange) {
                        val valVal = matrix[y][x]
                        if (valVal < threshold) {
                            // Produce dot
                            val actualXMm = x * dX
                            generatedGcode.append("G0 X${formatCoordinates(actualXMm)} Y${formatCoordinates(actualYMm)}\n")
                            generatedGcode.append("G1 S1000\nG1 S0\n") // Pulse laser
                            currentPathPoints.add(Offset(actualXMm, actualYMm))
                            
                            // If dots path accumulates, save in segments to draw as small individual path loops or pixels
                            if (currentPathPoints.size >= 2) {
                                previewPaths.add(currentPathPoints.toList())
                                currentPathPoints.clear()
                            }
                        }
                    }
                }
                if (currentPathPoints.isNotEmpty()) {
                    previewPaths.add(currentPathPoints)
                }
            }

            ProcessMode.OUTLINE -> {
                // Edge tracing: Detect transition edges (horizontal and vertical differences)
                val isEdge = Array(height) { BooleanArray(width) }
                for (y in 1 until height - 1) {
                    for (x in 1 until width - 1) {
                        val current = getGrayscale(scaledBitmap.getPixel(x, y))
                        val right = getGrayscale(scaledBitmap.getPixel(x + 1, y))
                        val bottom = getGrayscale(scaledBitmap.getPixel(x, y + 1))
                        
                        val diffX = current - right
                        val diffY = current - bottom
                        val grad = sqrt((diffX * diffX + diffY * diffY).toDouble()).toFloat()

                        isEdge[y][x] = grad > 0.15f // edge threshold
                    }
                }

                // Group adjacent edges into lines to minimize traveling
                val visited = Array(height) { BooleanArray(width) }
                for (y in 1 until height - 1) {
                    val actualYMm = (height - 1 - y) * dY
                    for (x in 1 until width - 1) {
                        if (isEdge[y][x] && !visited[y][x]) {
                            // Trace simple continuous contour line
                            val segment = mutableListOf<Offset>()
                            var cx = x
                            var cy = y
                            var foundNext = true
                            
                            while (foundNext) {
                                visited[cy][cx] = true
                                segment.add(Offset(cx * dX, (height - 1 - cy) * dY))
                                
                                // Search 8 neighbors
                                foundNext = false
                                for (dy in -1..1) {
                                    for (dx in -1..1) {
                                        if (dx == 0 && dy == 0) continue
                                        val nx = cx + dx
                                        val ny = cy + dy
                                        if (nx in 0 until width && ny in 0 until height) {
                                            if (isEdge[ny][nx] && !visited[ny][nx]) {
                                                cx = nx
                                                cy = ny
                                                foundNext = true
                                                break
                                            }
                                        }
                                    }
                                    if (foundNext) break
                                }
                            }

                            if (segment.size > 2) {
                                previewPaths.add(segment)
                                // Write G-code for this contour
                                generatedGcode.append("G0 X${formatCoordinates(segment[0].x)} Y${formatCoordinates(segment[0].y)}\n")
                                for (i in 1 until segment.size) {
                                    generatedGcode.append("G1 X${formatCoordinates(segment[i].x)} Y${formatCoordinates(segment[i].y)} S1000\n")
                                }
                                generatedGcode.append("G1 S0\n")
                            }
                        }
                    }
                }
            }
        }

        return ImageProcessResult(
            gcodeChunk = generatedGcode.toString(),
            previewPaths = previewPaths
        )
    }

    private fun getGrayscale(color: Int): Float {
        val r = Color.red(color) / 255.0f
        val g = Color.green(color) / 255.0f
        val b = Color.blue(color) / 255.0f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun formatCoordinates(value: Float): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }
}

data class ImageProcessResult(
    val gcodeChunk: String,
    val previewPaths: List<List<Offset>>
)
