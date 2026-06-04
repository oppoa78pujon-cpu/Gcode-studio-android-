package com.example.utils

import androidx.compose.ui.geometry.Offset
import java.util.Locale

object GCodeParser {

    enum class MoveType {
        TRAVEL, // G0 movement (usually represented as dashed grey/red)
        CUT     // G1/G2/G3 cut movement (represented as solid line)
    }

    data class GCodeSegment(
        val start: Offset,
        val end: Offset,
        val startZ: Float = 0f,
        val endZ: Float = 0f,
        val type: MoveType,
        val sPower: Int = 0,
        val gcodeLine: String = ""
    )

    /**
     * Parses raw G-code text and reconstructs the toolpath as drawn segments.
     * Keeps track of relative or absolute coordinates, tracking x and y state.
     */
    fun parseGCode(rawGCode: String): List<GCodeSegment> {
        val segments = mutableListOf<GCodeSegment>()
        if (rawGCode.isEmpty()) return segments

        var cx = 0f
        var cy = 0f
        var cz = 0f
        var currentMoveType = MoveType.TRAVEL
        var currentPower = 0

        val lines = rawGCode.lines()
        for (rawLine in lines) {
            val line = cleanLine(rawLine)
            if (line.isEmpty() || line.startsWith(";")) continue

            val cmd = extractCommand(line) ?: continue

            // Determine move status
            val isG0 = cmd == "G00" || cmd == "G0"
            val isG1 = cmd == "G01" || cmd == "G1"
            val isG2 = cmd == "G02" || cmd == "G2"
            val isG3 = cmd == "G03" || cmd == "G3"

            // Keep track of power variables
            if (line.contains("M5")) {
                currentPower = 0
            } else {
                val sMatch = Regex("[Ss](\\d+)").find(line)
                if (sMatch != null) {
                    currentPower = sMatch.groupValues[1].toIntOrNull() ?: currentPower
                }
            }

            if (isG0 || isG1 || isG2 || isG3) {
                val startPt = Offset(cx, cy)
                val startZVal = cz
                
                // Extract coordinates
                val xMatch = Regex("[Xx](-?\\d+\\.?\\d*)").find(line)
                val yMatch = Regex("[Yy](-?\\d+\\.?\\d*)").find(line)
                val zMatch = Regex("[Zz](-?\\d+\\.?\\d*)").find(line)

                var hasCoord = false
                if (xMatch != null) {
                    cx = xMatch.groupValues[1].toFloat()
                    hasCoord = true
                }
                if (yMatch != null) {
                    cy = yMatch.groupValues[1].toFloat()
                    hasCoord = true
                }
                if (zMatch != null) {
                    cz = zMatch.groupValues[1].toFloat()
                    hasCoord = true
                }

                val endPt = Offset(cx, cy)
                val endZVal = cz

                if (hasCoord) {
                    // Decide if move is CUT (G1/2/3 and laser power ON, or Spindle carving down Z <= 0)
                    // If Z is negative, Spindle is carved in depth, so it's a cut. If laser power > 0, it is a cut
                    val type = if (isG0) {
                        MoveType.TRAVEL
                    } else if (cz > 0.05f) { // spindle is traveling above workpiece
                        MoveType.TRAVEL
                    } else {
                        MoveType.CUT
                    }

                    // Avoid registering zero-distance segments
                    if (startPt != endPt || Math.abs(startZVal - endZVal) > 0.001f) {
                        segments.add(
                            GCodeSegment(
                                start = startPt,
                                end = endPt,
                                startZ = startZVal,
                                endZ = endZVal,
                                type = type,
                                sPower = currentPower,
                                gcodeLine = rawLine.trim()
                            )
                        )
                    }
                }
            }
        }

        return segments
    }

    private fun cleanLine(line: String): String {
        // Strip out comments
        val commentIdx = line.indexOf(';')
        var clean = if (commentIdx != -1) line.substring(0, commentIdx) else line
        val parenIdx = clean.indexOf('(')
        if (parenIdx != -1) {
            clean = clean.substring(0, parenIdx)
        }
        return clean.trim().uppercase(Locale.US)
    }

    private fun extractCommand(line: String): String? {
        val parts = line.split(Regex("\\s+"))
        if (parts.isEmpty()) return null
        val first = parts[0]
        if (first.startsWith("G") || first.startsWith("M")) {
            return first
        }
        // Sometimes command G1 is embedded e.g. "X10 Y10 G1"
        for (p in parts) {
            if (p.startsWith("G") || p.startsWith("M")) {
                return p
            }
        }
        return null
    }
}
