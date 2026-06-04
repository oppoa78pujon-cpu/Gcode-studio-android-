package com.example.utils

import androidx.compose.ui.geometry.Offset
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

object DXFParser {
    // A pair representing a group code and its text value
    data class Group(val code: Int, val value: String)

    fun parse(inputStream: InputStream): List<List<Offset>> {
        val groups = mutableListOf<Group>()
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line1: String? = reader.readLine()
                while (line1 != null) {
                    val codeStr = line1.trim()
                    val line2 = reader.readLine() ?: break
                    val valStr = line2.trim()
                    if (codeStr.isNotEmpty()) {
                        try {
                            val code = codeStr.toInt()
                            groups.add(Group(code, valStr))
                        } catch (e: Exception) {
                            // ignore malformed codes
                        }
                    }
                    line1 = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

        val paths = mutableListOf<List<Offset>>()
        var idx = 0
        val size = groups.size

        // Try the standard SECTION/ENTITIES parsing first
        var parsedViaSection = false
        while (idx < size) {
            val group = groups[idx]
            if (group.code == 0 && group.value == "SECTION") {
                val nextGroup = if (idx + 1 < size) groups[idx + 1] else null
                if (nextGroup?.code == 2 && nextGroup.value == "ENTITIES") {
                    idx += 2
                    while (idx < size) {
                        val entGroup = groups[idx]
                        if (entGroup.code == 0 && entGroup.value == "ENDSEC") {
                            break
                        }
                        if (entGroup.code == 0) {
                            val entityType = entGroup.value
                            val entityGroups = mutableListOf<Group>()
                            idx++
                            while (idx < size && groups[idx].code != 0) {
                                entityGroups.add(groups[idx])
                                idx++
                            }
                            
                            val path = if (entityType == "POLYLINE") {
                                val (polyPoints, nextIdx) = parsePolylineWithVertices(idx, groups, entityGroups)
                                idx = nextIdx
                                polyPoints
                            } else {
                                parseEntity(entityType, entityGroups)
                            }

                            if (path != null && path.isNotEmpty()) {
                                paths.add(path)
                            }
                            parsedViaSection = true
                            continue
                        }
                        idx++
                    }
                }
            }
            idx++
        }

        // Defensive Fallback styling: If standard SECTION approach found nothing,
        // scan everywhere for continuous entity blocks. This ensures custom DXFs open.
        if (!parsedViaSection || paths.isEmpty()) {
            idx = 0
            while (idx < size) {
                val group = groups[idx]
                if (group.code == 0 && (
                    group.value == "LINE" || 
                    group.value == "CIRCLE" || 
                    group.value == "ARC" || 
                    group.value == "LWPOLYLINE" || 
                    group.value == "POLYLINE" || 
                    group.value == "ELLIPSE" || 
                    group.value == "SPLINE"
                )) {
                    val entityType = group.value
                    val entityGroups = mutableListOf<Group>()
                    idx++
                    while (idx < size && groups[idx].code != 0) {
                        entityGroups.add(groups[idx])
                        idx++
                    }
                    
                    val path = if (entityType == "POLYLINE") {
                        val (polyPoints, nextIdx) = parsePolylineWithVertices(idx, groups, entityGroups)
                        idx = nextIdx
                        polyPoints
                    } else {
                        parseEntity(entityType, entityGroups)
                    }

                    if (path != null && path.isNotEmpty()) {
                        paths.add(path)
                    }
                    continue
                }
                idx++
            }
        }

        return paths
    }

    private fun parsePolylineWithVertices(
        initialIdx: Int,
        groups: List<Group>,
        entityGroups: List<Group>
    ): Pair<List<Offset>, Int> {
        var idx = initialIdx
        val size = groups.size
        val polylinePoints = mutableListOf<Offset>()
        var closedPolyline = false
        
        for (g in entityGroups) {
            if (g.code == 70) {
                val valInt = g.value.toIntOrNull() ?: 0
                closedPolyline = (valInt and 1) != 0
            }
        }
        
        // Loop vertices
        while (idx < size && groups[idx].code == 0 && groups[idx].value == "VERTEX") {
            idx++ // Skip 0 VERTEX
            val vertexGroups = mutableListOf<Group>()
            while (idx < size && groups[idx].code != 0) {
                vertexGroups.add(groups[idx])
                idx++
            }
            var vx = 0f
            var vy = 0f
            for (vg in vertexGroups) {
                if (vg.code == 10) vx = vg.value.toFloatOrNull() ?: 0f
                if (vg.code == 20) vy = vg.value.toFloatOrNull() ?: 0f
            }
            polylinePoints.add(Offset(vx, vy))
        }
        
        // Consume SEQEND
        if (idx < size && groups[idx].code == 0 && groups[idx].value == "SEQEND") {
            idx++
            while (idx < size && groups[idx].code != 0) {
                idx++
            }
        }
        
        if (polylinePoints.isNotEmpty() && closedPolyline) {
            polylinePoints.add(polylinePoints[0])
        }
        
        return Pair(polylinePoints, idx)
    }

    private fun parseEntity(type: String, groups: List<Group>): List<Offset>? {
        return when (type) {
            "LINE" -> parseLine(groups)
            "CIRCLE" -> parseCircle(groups)
            "ARC" -> parseArc(groups)
            "LWPOLYLINE" -> parseLwPolyline(groups)
            "POLYLINE" -> parsePolyline(groups)
            "ELLIPSE" -> parseEllipse(groups)
            "SPLINE" -> parseSpline(groups)
            else -> null
        }
    }

    private fun parseLine(groups: List<Group>): List<Offset> {
        var x1 = 0f
        var y1 = 0f
        var x2 = 0f
        var y2 = 0f
        for (g in groups) {
            when (g.code) {
                10 -> x1 = g.value.toFloatOrNull() ?: 0f
                20 -> y1 = g.value.toFloatOrNull() ?: 0f
                11 -> x2 = g.value.toFloatOrNull() ?: 0f
                21 -> y2 = g.value.toFloatOrNull() ?: 0f
            }
        }
        return listOf(Offset(x1, y1), Offset(x2, y2))
    }

    private fun parseCircle(groups: List<Group>): List<Offset> {
        var cx = 0f
        var cy = 0f
        var r = 0f
        for (g in groups) {
            when (g.code) {
                10 -> cx = g.value.toFloatOrNull() ?: 0f
                20 -> cy = g.value.toFloatOrNull() ?: 0f
                40 -> r = g.value.toFloatOrNull() ?: 0f
            }
        }
        if (r <= 0f) return emptyList()
        val points = mutableListOf<Offset>()
        val segments = 64
        for (s in 0..segments) {
            val angle = (2.0 * Math.PI * s / segments).toFloat()
            points.add(Offset(cx + r * cos(angle), cy + r * sin(angle)))
        }
        return points
    }

    private fun parseArc(groups: List<Group>): List<Offset> {
        var cx = 0f
        var cy = 0f
        var r = 0f
        var startAngle = 0f
        var endAngle = 360f
        for (g in groups) {
            when (g.code) {
                10 -> cx = g.value.toFloatOrNull() ?: 0f
                20 -> cy = g.value.toFloatOrNull() ?: 0f
                40 -> r = g.value.toFloatOrNull() ?: 0f
                50 -> startAngle = g.value.toFloatOrNull() ?: 0f
                51 -> endAngle = g.value.toFloatOrNull() ?: 0f
            }
        }
        if (r <= 0f) return emptyList()
        val points = mutableListOf<Offset>()
        
        var startRad = Math.toRadians(startAngle.toDouble())
        var endRad = Math.toRadians(endAngle.toDouble())
        if (endRad < startRad) {
            endRad += 2.0 * Math.PI
        }
        val diff = endRad - startRad
        val segments = (16 + (diff / (2.0 * Math.PI)) * 48).toInt().coerceIn(16, 64)
        
        for (s in 0..segments) {
            val progress = s.toDouble() / segments
            val angle = startRad + diff * progress
            points.add(Offset((cx + r * cos(angle)).toFloat(), (cy + r * sin(angle)).toFloat()))
        }
        return points
    }

    private fun parseLwPolyline(groups: List<Group>): List<Offset> {
        val points = mutableListOf<Offset>()
        var isClosed = false
        var currentX: Float? = null
        var currentY: Float? = null
        
        for (g in groups) {
            when (g.code) {
                70 -> {
                    val flags = g.value.toIntOrNull() ?: 0
                    isClosed = (flags and 1) != 0
                }
                10 -> {
                    if (currentX != null && currentY != null) {
                        points.add(Offset(currentX, currentY))
                        currentY = null
                    }
                    currentX = g.value.toFloatOrNull()
                }
                20 -> {
                    currentY = g.value.toFloatOrNull()
                    if (currentX != null && currentY != null) {
                        points.add(Offset(currentX, currentY))
                        currentX = null
                        currentY = null
                    }
                }
            }
        }
        if (currentX != null && currentY != null) {
            points.add(Offset(currentX, currentY))
        }

        if (points.isNotEmpty() && isClosed) {
            points.add(points[0])
        }
        return points
    }

    private fun parsePolyline(groups: List<Group>): List<Offset> {
        val points = mutableListOf<Offset>()
        var currentX: Float? = null
        var currentY: Float? = null
        for (g in groups) {
            when (g.code) {
                10 -> {
                    if (currentX != null && currentY != null) {
                        points.add(Offset(currentX, currentY))
                        currentY = null
                    }
                    currentX = g.value.toFloatOrNull()
                }
                20 -> {
                    currentY = g.value.toFloatOrNull()
                    if (currentX != null && currentY != null) {
                        points.add(Offset(currentX, currentY))
                        currentX = null
                        currentY = null
                    }
                }
            }
        }
        if (currentX != null && currentY != null) {
            points.add(Offset(currentX, currentY))
        }
        return points
    }

    private fun parseEllipse(groups: List<Group>): List<Offset> {
        var cx = 0f
        var cy = 0f
        var mx = 0f
        var my = 0f
        var ratio = 1f
        var startParam = 0f
        var endParam = (2.0 * Math.PI).toFloat()

        for (g in groups) {
            when (g.code) {
                10 -> cx = g.value.toFloatOrNull() ?: 0f
                20 -> cy = g.value.toFloatOrNull() ?: 0f
                11 -> mx = g.value.toFloatOrNull() ?: 0f
                21 -> my = g.value.toFloatOrNull() ?: 0f
                40 -> ratio = g.value.toFloatOrNull() ?: 1f
                41 -> startParam = g.value.toFloatOrNull() ?: 0f
                42 -> endParam = g.value.toFloatOrNull() ?: (2.0 * Math.PI).toFloat()
            }
        }

        val a = sqrt(mx * mx + my * my)
        if (a <= 0f) return emptyList()
        val theta = atan2(my.toDouble(), mx.toDouble())
        val b = a * ratio

        val points = mutableListOf<Offset>()
        
        var start = startParam.toDouble()
        var end = endParam.toDouble()
        if (end < start) {
            end += 2.0 * Math.PI
        }
        val diff = end - start
        val segments = (16 + (diff / (2.0 * Math.PI)) * 48).toInt().coerceIn(16, 64)

        val cosTheta = cos(theta)
        val sinTheta = sin(theta)

        for (s in 0..segments) {
            val progress = s.toDouble() / segments
            val t = start + diff * progress
            val xPrime = a * cos(t)
            val yPrime = b * sin(t)
            
            val rx = cx + xPrime * cosTheta - yPrime * sinTheta
            val ry = cy + xPrime * sinTheta + yPrime * cosTheta
            points.add(Offset(rx.toFloat(), ry.toFloat()))
        }
        return points
    }

    private fun parseSpline(groups: List<Group>): List<Offset> {
        val fitPoints = mutableListOf<Offset>()
        val controlPoints = mutableListOf<Offset>()
        var currentFitX: Float? = null
        var currentFitY: Float? = null
        var currentCtrlX: Float? = null
        var currentCtrlY: Float? = null
        var isClosed = false

        for (g in groups) {
            when (g.code) {
                70 -> {
                    val flags = g.value.toIntOrNull() ?: 0
                    isClosed = (flags and 1) != 0
                }
                10 -> {
                    if (currentCtrlX != null && currentCtrlY != null) {
                        controlPoints.add(Offset(currentCtrlX, currentCtrlY))
                        currentCtrlY = null
                    }
                    currentCtrlX = g.value.toFloatOrNull()
                }
                20 -> {
                    currentCtrlY = g.value.toFloatOrNull()
                    if (currentCtrlX != null && currentCtrlY != null) {
                        controlPoints.add(Offset(currentCtrlX, currentCtrlY))
                        currentCtrlX = null
                        currentCtrlY = null
                    }
                }
                11 -> {
                    if (currentFitX != null && currentFitY != null) {
                        fitPoints.add(Offset(currentFitX, currentFitY))
                        currentFitY = null
                    }
                    currentFitX = g.value.toFloatOrNull()
                }
                21 -> {
                    currentFitY = g.value.toFloatOrNull()
                    if (currentFitX != null && currentFitY != null) {
                        fitPoints.add(Offset(currentFitX, currentFitY))
                        currentFitX = null
                        currentFitY = null
                    }
                }
            }
        }
        if (currentCtrlX != null && currentCtrlY != null) {
            controlPoints.add(Offset(currentCtrlX, currentCtrlY))
        }
        if (currentFitX != null && currentFitY != null) {
            fitPoints.add(Offset(currentFitX, currentFitY))
        }

        val points = if (fitPoints.isNotEmpty()) fitPoints else controlPoints
        if (points.isNotEmpty() && isClosed) {
            points.add(points[0])
        }
        return points
    }
}
