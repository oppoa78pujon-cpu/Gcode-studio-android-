package com.example.utils

import androidx.compose.ui.geometry.Offset
import java.util.Locale

object GCodeCompiler {

    data class CompilationSettings(
        val toolType: String = "LASER", // "LASER", "SPINDLE", or "ROUTER"
        val laserOnCommand: String = "M4", // "M4" (Dynamic) or "M3" (Constant)
        val maxSValue: Int = 1000,         // S1000 maximum CNC scale or RPM
        val targetPowerS: Int = 1000,      // Cut S-value power (e.g. S800)
        val feedrateCut: Float = 1200f,    // F value inside cut, mm/min
        val feedrateTravel: Float = 3000f, // F value inside rapid travel, mm/min
        val safeZ: Float = 5.0f,           // Spindle safety absolute depth
        val targetCutZ: Float = -2.0f,     // Spindle total depth to cut
        val zStepPerPass: Float = 0.5f,    // Depth cut per pass (multi-pass)
        val feedratePlunge: Float = 200f,  // Plunge speed F for Z-axis deep plunge
        val addCoordinatesOffset: Offset = Offset(0f, 0f), // Manual coordinate zeroing shifts
        val routerBitType: String = "",    // Selected Router bit type
        val multiPassStrategy: String = "DEPTH_FIRST",     // "DEPTH_FIRST" or "LAYER_FIRST"
        val gcodeDecimalPrecision: Int = 3                 // Col koordinat format decimal places: 2, 3, or 4
    )

    /**
     * Compiles multiple independent line paths into one fully functional G-code program block.
     * Easily handles Spindle, Laser, and Router settings with customized feedrates, dynamic power, passes, etc.
     */
    fun compilePaths(
        paths: List<List<Offset>>,
        settings: CompilationSettings
    ): String {
        val code = StringBuilder()
        
        val decimals = settings.gcodeDecimalPrecision
        val formatStr = "%.${decimals}f"
        val sb = { v: Float -> String.format(Locale.US, formatStr, v) }

        // 1. Write standard CNC/FluidNC headers
        code.append("; FLUIDNC G-CODE GENERATOR MULTI-TOOLP\n")
        code.append("; Generated at UTC: 2026-06-03\n")
        code.append("; Tool: ${settings.toolType}\n")
        code.append("; Precision: $decimals decimals\n")
        code.append("; Strategy: ${settings.multiPassStrategy}\n")
        code.append("G21 ; Set units to millimetres\n")
        code.append("G90 ; Use absolute coordinates\n")
        code.append("G17 ; Select XY plane\n")
        
        if (settings.toolType == "LASER") {
            code.append("S0 ; Force laser power off initially\n")
            code.append("G0 F${settings.feedrateTravel.toInt()} ; Set travel feedrate\n")
            code.append("G1 F${settings.feedrateCut.toInt()} ; Set cutting feedrate\n")

            // Traverse each path
            for (path in paths) {
                if (path.isEmpty()) continue
                
                // Rapid travel to first point
                val firstPt = path[0] + settings.addCoordinatesOffset
                code.append("G0 X${sb(firstPt.x)} Y${sb(firstPt.y)}\n")

                // Open Laser: Standard FluidNC uses M4 with explicit G1 lines
                // M4 requires dynamic move. M3 is constant. Let's issue command
                code.append("${settings.laserOnCommand} S${settings.targetPowerS}\n")

                // Cut rest of points
                for (i in 1 until path.size) {
                    val nextPt = path[i] + settings.addCoordinatesOffset
                    code.append("G1 X${sb(nextPt.x)} Y${sb(nextPt.y)}\n")
                }

                // Close Laser
                code.append("M5 S0 ; Laser Off\n")
            }
        } else {
            // SPINDLE / ROUTER MODE (Z axis depth carving)
            if (settings.toolType == "ROUTER" && settings.routerBitType.isNotEmpty()) {
                code.append("; Router Bit: ${settings.routerBitType}\n")
            }
            code.append("; Feedrate Cut: ${settings.feedrateCut} mm/min\n")
            code.append("G0 Z${sb(settings.safeZ)} ; Travel Z safe height\n")
            code.append("G0 F${settings.feedrateTravel.toInt()}\n")
            code.append("G1 F${settings.feedrateCut.toInt()}\n")
            code.append("M3 S${settings.maxSValue} ; Spindle Start Rotate\n")

            // Calculate passes needed for spindle
            val totalDepth = settings.targetCutZ.absoluteValue()
            val stepSize = settings.zStepPerPass.absoluteValue()
            val passCount = if (stepSize > 0) {
                kotlin.math.ceil((totalDepth / stepSize).toDouble()).toInt().coerceAtLeast(1)
            } else 1

            if (settings.multiPassStrategy == "DEPTH_FIRST") {
                code.append("; STRATEGY: DEPTH_FIRST (Selesaikan satu jalur sampai kedalaman penuh baru pindah jalur)\n")
                for (path in paths) {
                    if (path.isEmpty()) continue

                    val startPt = path[0] + settings.addCoordinatesOffset
                    // 1. Travel to START POINT associated with the path at Safe Z Height (Rapid move)
                    code.append("G0 Z${sb(settings.safeZ)}\n")
                    code.append("G0 X${sb(startPt.x)} Y${sb(startPt.y)}\n")

                    // 2. Multi passes on this single path
                    for (pass in 1..passCount) {
                        val currentDepthZ = -(pass * stepSize).coerceAtMost(totalDepth)
                        code.append("; JALUR SEGMENT - PASS $pass (Z Depth: ${sb(currentDepthZ)})\n")

                        if (pass > 1) {
                            // Rise, re-traverse back to start to repeat cut deeper
                            code.append("G0 Z${sb(settings.safeZ)}\n")
                            code.append("G0 X${sb(startPt.x)} Y${sb(startPt.y)}\n")
                        }

                        // Plunge down to current depth at plunge feedrate
                        code.append("G1 Z${sb(currentDepthZ)} F${settings.feedratePlunge.toInt()}\n")
                        // Reset to cutting feedrate
                        code.append("G1 F${settings.feedrateCut.toInt()}\n")

                        // Cut coordinates
                        for (i in 1 until path.size) {
                            val pt = path[i] + settings.addCoordinatesOffset
                            code.append("G1 X${sb(pt.x)} Y${sb(pt.y)}\n")
                        }
                    }

                    // 3. Rise tool to safe height immediately after completing this path segment fully
                    code.append("G0 Z${sb(settings.safeZ)}\n")
                }
            } else {
                code.append("; STRATEGY: LAYER_FIRST (Iris potongan lapis demi lapis seluruh gambar)\n")
                // Execute passes
                for (pass in 1..passCount) {
                    // Calculate current pass depth. Since Z=0 is workpiece top surface, depth goes negative
                    val currentDepthZ = -(pass * stepSize).coerceAtMost(totalDepth)
                    code.append("; PASS $pass (Z Depth: ${sb(currentDepthZ)})\n")

                    for (path in paths) {
                        if (path.isEmpty()) continue

                        val startPt = path[0] + settings.addCoordinatesOffset
                        // 1. Travel to START POINT at Safe Z Height (Rapid move)
                        code.append("G0 Z${sb(settings.safeZ)}\n")
                        code.append("G0 X${sb(startPt.x)} Y${sb(startPt.y)}\n")

                        // 2. Plunge down to current depth at plunge feedrate
                        code.append("G1 Z${sb(currentDepthZ)} F${settings.feedratePlunge.toInt()}\n")
                        // Reset to cutting feedrate
                        code.append("G1 F${settings.feedrateCut.toInt()}\n")

                        // 3. Cut along the coordinates of path
                        for (i in 1 until path.size) {
                            val pt = path[i] + settings.addCoordinatesOffset
                            code.append("G1 X${sb(pt.x)} Y${sb(pt.y)}\n")
                        }

                        // 4. Rise to safe height at the end of path
                        code.append("G0 Z${sb(settings.safeZ)}\n")
                    }
                }
            }
            code.append("M5 ; Spindle Stop rotate\n")
            code.append("G0 Z${sb(settings.safeZ)} ; Lift tool to clearance\n")
        }

        // Program Footer
        code.append("G0 X0 Y0 ; Return home\n")
        code.append("M30 ; End of program\n")

        return sanitize(code.toString())
    }

    fun sanitize(gcode: String): String {
        var s = gcode
        // Remove standard UTF-8 BOM if it starts with it
        while (s.startsWith("\uFEFF")) {
            s = s.substring(1)
        }
        // Also remove structural/Latin1 version of BOM "ï»¿" at start
        while (s.startsWith("ï»¿")) {
            s = s.substring(3)
        }
        // Also remove any control/junk characters or weird encoding anomalies at the start
        s = s.trimStart { it.code == 65279 || it.code == 0xFEFF || (it.code in 127..255 && it != '\n' && it != '\r') }
        
        // Normalize line breaks to standard CNC CRLF (\r\n) so that copy-paste/FATFS does not merge lines
        s = s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n")
        return s
    }

    // Number formatter for standard 2 decimal CNC coordinate point floats
    private fun sb(v: Float): String {
        return String.format(Locale.US, "%.2f", v)
    }

    /**
     * Reorders the toolpath segments (and reverses them when appropriate) using a
     * Nearest Neighbor heuristic. This mimics professional CAM software to minimize
     * "air-travel" G0 rapid moves.
     */
    fun optimizeToolpath(paths: List<List<Offset>>): List<List<Offset>> {
        if (paths.size <= 1) return paths
        val unvisited = paths.filter { it.isNotEmpty() }.toMutableList()
        if (unvisited.isEmpty()) return emptyList()

        val optimized = mutableListOf<List<Offset>>()
        // Start with the first path as the anchor point
        var currentPath = unvisited.removeAt(0)
        optimized.add(currentPath)
        var currentEnd = currentPath.last()

        while (unvisited.isNotEmpty()) {
            var bestIdx = 0
            var bestDist = Float.MAX_VALUE
            var shouldReverse = false

            for (i in unvisited.indices) {
                val path = unvisited[i]
                val distToStart = distance(currentEnd, path.first())
                val distToEnd = distance(currentEnd, path.last())

                if (distToStart < bestDist) {
                    bestDist = distToStart
                    bestIdx = i
                    shouldReverse = false
                }
                if (distToEnd < bestDist) {
                    bestDist = distToEnd
                    bestIdx = i
                    shouldReverse = true
                }
            }

            val nextPath = unvisited.removeAt(bestIdx)
            val finalNext = if (shouldReverse) nextPath.reversed() else nextPath
            optimized.add(finalNext)
            currentEnd = finalNext.last()
        }
        return optimized
    }

    private fun distance(o1: Offset, o2: Offset): Float {
        val dx = o1.x - o2.x
        val dy = o1.y - o2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun Float.absoluteValue(): Float = if (this < 0) -this else this
}
