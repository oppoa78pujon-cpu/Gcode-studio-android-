package com.example.utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

private const val PI = 3.1415927f

sealed class VectorShape {

    abstract fun toPoints(resolution: Float = 1.0f): List<Offset>

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            return listOf(Offset(x1, y1), Offset(x2, y2))
        }
    }

    data class Circle(val cx: Float, val cy: Float, val r: Float) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            val points = mutableListOf<Offset>()
            // segment count depends on size and resolution, approx 36 to 120 segments
            val segments = (2.0f * PI * r * resolution).coerceIn(36f, 180f).toInt()
            for (i in 0..segments) {
                val angle = (2.0f * PI * i) / segments
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                points.add(Offset(x, y))
            }
            return points
        }
    }

    data class Star(val cx: Float, val cy: Float, val rOuter: Float, val rInner: Float, val points: Int) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            val list = mutableListOf<Offset>()
            val totalSteps = points * 2
            for (i in 0..totalSteps) {
                val angle = (i * PI / points).toFloat()
                val r = if (i % 2 == 0) rOuter else rInner
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                list.add(Offset(x, y))
            }
            return list
        }
    }

    data class Polygon(val cx: Float, val cy: Float, val r: Float, val sides: Int) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            val list = mutableListOf<Offset>()
            val sideCount = sides.coerceAtLeast(3)
            for (i in 0..sideCount) {
                val angle = (2.0f * PI * i / sideCount)
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                list.add(Offset(x, y))
            }
            return list
        }
    }

    data class Spiral(val cx: Float, val cy: Float, val rMax: Float, val loops: Float) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            val list = mutableListOf<Offset>()
            val steps = (loops * 100).toInt().coerceAtLeast(20)
            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val angle = fraction * loops * 2.0f * PI
                val r = fraction * rMax
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                list.add(Offset(x, y))
            }
            return list
        }
    }

    data class Flower(val cx: Float, val cy: Float, val rInner: Float, val rScale: Float, val petals: Int) : VectorShape() {
        override fun toPoints(resolution: Float): List<Offset> {
            val list = mutableListOf<Offset>()
            val steps = 360
            for (i in 0..steps) {
                val angle = (i * PI / 180).toFloat()
                // flower polar formula: r = a + b * sin(k * theta)
                val r = rInner + rScale * cos(petals * angle)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                list.add(Offset(x, y))
            }
            return list
        }
    }
}
