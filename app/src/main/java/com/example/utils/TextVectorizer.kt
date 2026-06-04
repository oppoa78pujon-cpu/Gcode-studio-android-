package com.example.utils

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset

object TextVectorizer {

    /**
     * Converts a string of text into a list of disjoint contours.
     * Each contour is a list of relative points (Offset) that should be cut sequentially.
     * This handles any typeface, sizing, spacing, and can extract exact paths of system fonts!
     */
    fun vectorize(
        text: String,
        fontFamily: String, // "SANS_SERIF", "SERIF", "MONOSPACE", or "DEFAULT"
        customTypeface: Typeface? = null,
        textSizeMm: Float,   // Desired height of text in mm on our CNC workspace
        letterSpacingScale: Float = 1.0f
    ): List<List<Offset>> {
        if (text.isEmpty()) return emptyList()

        // 1. Create a Paint object similar to what standard Android uses
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1f
            // Map typeface
            typeface = when {
                customTypeface != null -> customTypeface
                fontFamily.startsWith("/") || fontFamily.endsWith(".ttf", ignoreCase = true) || fontFamily.endsWith(".otf", ignoreCase = true) -> {
                    try {
                        Typeface.createFromFile(fontFamily)
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                }
                else -> when (fontFamily.uppercase()) {
                    "SANS_SERIF" -> Typeface.SANS_SERIF
                    "SERIF" -> Typeface.SERIF
                    "MONOSPACE" -> Typeface.MONOSPACE
                    else -> Typeface.DEFAULT
                }
            }
            // Use a large internal font size for precision, then scale down to mm
            textSize = 1000f
        }

        // 2. Measure bounds of the text to establish accurate scaling
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        
        // Scale factor: how many units represent 1 mm.
        // We want the text height (bounds.height()) to equal textSizeMm.
        val measuredHeight = bounds.height().toFloat()
        val scale = if (measuredHeight > 0) textSizeMm / measuredHeight else 1.0f

        // 3. Get the text outline path
        val textPath = Path()
        // Compute starting coordinates such that the text is centered around (0,0) or start from top-left (0,0)
        val startX = -bounds.left.toFloat()
        val startY = -bounds.top.toFloat() // aligning baseline
        paint.getTextPath(text, 0, text.length, startX, startY, textPath)

        // 4. Trace the contours of the text path using PathMeasure
        val contours = mutableListOf<List<Offset>>()
        val pathMeasure = PathMeasure(textPath, false)

        val stepPx = 10.0f // sample resolution in paint pixel coordinates. 
        // 10px in a 1000px font space represents a step of 0.01 height, which when scaled to e.g. 20mm is 0.2mm resolution. Extremely clean!

        do {
            val length = pathMeasure.length
            if (length > 0) {
                val contourPoints = mutableListOf<Offset>()
                var distance = 0f
                val pos = FloatArray(2)
                val tan = FloatArray(2)

                while (distance <= length) {
                    pathMeasure.getPosTan(distance, pos, tan)
                    // Scale down to mm coordinates
                    val mmX = pos[0] * scale * letterSpacingScale
                    val mmY = -pos[1] * scale // negated to invert Y because CNC Y axis goes UP whereas standard Android paint coordinates go DOWN
                    contourPoints.add(Offset(mmX, mmY))
                    distance += stepPx
                }
                
                // Add final point of the contour if we didn't hit it exactly
                if (distance - stepPx < length) {
                    pathMeasure.getPosTan(length, pos, tan)
                    val mmX = pos[0] * scale * letterSpacingScale
                    val mmY = -pos[1] * scale // negated to invert Y
                    contourPoints.add(Offset(mmX, mmY))
                }

                if (contourPoints.isNotEmpty()) {
                    contours.add(contourPoints)
                }
            }
        } while (pathMeasure.nextContour())

        return contours
    }
}
