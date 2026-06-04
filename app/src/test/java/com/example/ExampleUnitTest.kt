package com.example

import com.example.utils.DXFParser
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testParseSimpleLine() {
        val dxfData = """
              0
            SECTION
              2
            ENTITIES
              0
            LINE
             10
            1.5
             20
            2.5
             11
            10.0
             21
            20.0
              0
            ENDSEC
              0
            EOF
        """.trimIndent()

        val inputStream = ByteArrayInputStream(dxfData.toByteArray())
        val paths = DXFParser.parse(inputStream)

        assertEquals(1, paths.size)
        val linePath = paths[0]
        assertEquals(2, linePath.size)
        assertEquals(Offset(1.5f, 2.5f), linePath[0])
        assertEquals(Offset(10.0f, 20.0f), linePath[1])
    }

    @Test
    fun testParseClassicPolylineWithVertices() {
        val dxfData = """
              0
            SECTION
              2
            ENTITIES
              0
            POLYLINE
             70
            1
              0
            VERTEX
             10
            5.0
             20
            5.0
              0
            VERTEX
             10
            15.0
             20
            25.0
              0
            SEQEND
              0
            ENDSEC
              0
            EOF
        """.trimIndent()

        val inputStream = ByteArrayInputStream(dxfData.toByteArray())
        val paths = DXFParser.parse(inputStream)

        assertEquals(1, paths.size)
        val polyPath = paths[0]
        // Since closed flag (70 -> 1) is set, we expect a closed loop: 5,5 -> 15,25 -> 5,5
        assertEquals(3, polyPath.size)
        assertEquals(Offset(5.0f, 5.0f), polyPath[0])
        assertEquals(Offset(15.0f, 25.0f), polyPath[1])
        assertEquals(Offset(5.0f, 5.0f), polyPath[2])
    }

    @Test
    fun testParseEllipse() {
        val dxfData = """
              0
            SECTION
              2
            ENTITIES
              0
            ELLIPSE
             10
            0.0
             20
            0.0
             11
            10.0
             21
            0.0
             40
            0.5
              0
            ENDSEC
              0
            EOF
        """.trimIndent()

        val inputStream = ByteArrayInputStream(dxfData.toByteArray())
        val paths = DXFParser.parse(inputStream)

        assertEquals(1, paths.size)
        val ellipsePoints = paths[0]
        assertTrue(ellipsePoints.size >= 16)
        
        // Center x=0, y=0. Major axis mx=10, my=0. Ratio=0.5
        // At start parameter (0.0): x = 0 + 10 = 10, y = 0
        assertEquals(10.0f, ellipsePoints.first().x, 0.1f)
        assertEquals(0.0f, ellipsePoints.first().y, 0.1f)
    }

    @Test
    fun testParseSpline() {
        val dxfData = """
              0
            SECTION
              2
            ENTITIES
              0
            SPLINE
             70
            1
             10
            1.0
             20
            2.0
             10
            3.0
             20
            4.0
              0
            ENDSEC
              0
            EOF
        """.trimIndent()

        val inputStream = ByteArrayInputStream(dxfData.toByteArray())
        val paths = DXFParser.parse(inputStream)

        assertEquals(1, paths.size)
        val splinePoints = paths[0]
        // Since closed flag (70 -> 1) is set, we expect closed spline: 1,2 -> 3,4 -> 1,2
        assertEquals(3, splinePoints.size)
        assertEquals(Offset(1.0f, 2.0f), splinePoints[0])
        assertEquals(Offset(3.0f, 4.0f), splinePoints[1])
        assertEquals(Offset(1.0f, 2.0f), splinePoints[2])
    }

    @Test
    fun testGCodeHasNoBOM() {
        val compiledGCode = com.example.utils.GCodeCompiler.compilePaths(emptyList(), com.example.utils.GCodeCompiler.CompilationSettings())
        println("COMPILED GCODE FIRST CHARS: ${compiledGCode.take(20).map { it.code }}")
        
        // Also read GCodeCompiler.kt file bytes
        val file = java.io.File("app/src/main/java/com/example/utils/GCodeCompiler.kt")
        if (file.exists()) {
            val bytes = file.readBytes()
            val hexList = bytes.take(20).map { String.format("%02X", it) }
            println("GCodeCompiler.kt FIRST 20 BYTES HEX: $hexList")
        } else {
            println("GCodeCompiler.kt NOT FOUND AT app/src/main/java/com/example/utils/GCodeCompiler.kt")
        }

        // Assert that the first character is not BOM (65279 or \uFEFF)
        assertFalse("GCode starts with BOM character!", compiledGCode.startsWith("\uFEFF"))
        assertFalse("GCode starts with BOM byte characters (unsigned byte form)!", compiledGCode.startsWith("ï»¿"))
    }
}
