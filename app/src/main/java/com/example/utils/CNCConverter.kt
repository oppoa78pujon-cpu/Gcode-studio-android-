package com.example.utils

import java.util.Locale
import java.util.regex.Pattern

object CNCConverter {

    data class ConversionResult(
        val outputGCode: String,
        val logOfChanges: List<String>,
        val originalUnit: String, // "INCH" or "MM" or "UNKNOWN"
        val linesConverted: Int
    )

    /**
     * Main method to compile/convert legacy Mach3 or generic CNC files (TAP, CNC, TXT) to Standard NC compliant format.
     */
    fun convertToStandardNC(
        input: String,
        stripLineNumbers: Boolean = true,
        convertInchToMetric: Boolean = false,
        convertToolChangeToPause: Boolean = true,
        removeAdornments: Boolean = false,
        addCoolantControls: Boolean = true
    ): ConversionResult {
        val lines = input.lines()
        val convertedLines = mutableListOf<String>()
        val logs = mutableListOf<String>()
        var linesProcessedCounter = 0
        var originalUnit = "UNKNOWN"

        // Search for unit signals initially
        for (line in lines) {
            val upper = line.uppercase()
            if (upper.contains("G20")) {
                originalUnit = "INCH"
            } else if (upper.contains("G21")) {
                originalUnit = "MM"
            }
        }

        logs.add("Memulai konversi CNC ke Standar NC...")
        if (originalUnit != "UNKNOWN") {
            logs.add("Mendeteksi unit asal berkas: $originalUnit")
        }

        // We regularize comments first: Convert Mach3 round brackets (comment) to Grbl/FluidNC character ';'
        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty()) {
                convertedLines.add("")
                continue
            }

            linesProcessedCounter++

            // Convert parentheses comments first (Mach3 style: "G0 X10 (this is a comment)")
            if (line.contains("(") && line.contains(")")) {
                val matcher = Pattern.compile("\\((.*?)\\)").matcher(line)
                val sb = StringBuffer()
                while (matcher.find()) {
                    val commentText = matcher.group(1)
                    matcher.appendReplacement(sb, "; $commentText")
                }
                matcher.appendTail(sb)
                line = sb.toString()
                // If it ends up starting with a space before semicolon, trim it down
                line = line.replace(" ;", " ;")
            }

            // If the line is now purely a comment
            if (line.startsWith(";")) {
                convertedLines.add(line)
                continue
            }

            // Remove Nxxxx line numbers (Mach3 usually numbers every row: e.g. N10 G00 X10)
            if (stripLineNumbers) {
                val nMatch = Regex("^[Nn]\\d+\\s*").find(line)
                if (nMatch != null) {
                    line = line.substring(nMatch.value.length).trim()
                    // Log only once in a while or summary
                }
            }

            // Handle Mach3 tool changes: M6 T2 or T2 M6 or M6
            if (convertToolChangeToPause) {
                // Find Tx representation
                val tMatch = Regex("[Tt](\\d+)").find(line)
                val isM6 = line.uppercase().contains("M6") || line.uppercase().contains("M06")
                
                if (isM6 || tMatch != null) {
                    val toolNumber = tMatch?.groupValues?.get(1) ?: "1"
                    
                    if (isM6) {
                        logs.add("Baris $linesProcessedCounter: Mengganti pergantian alat otomatis (M6 T$toolNumber) dengan jeda manual M0.")
                        line = "M0 ; JEDA MANUAL: Silakan ganti alat ke T$toolNumber, lalu tekan RESUME setelah selesai"
                    } else {
                        // T2 alone: prepare tool but comment it out to avoid errors on basic GRBL/FluidNC configurations
                        line = "; T$toolNumber ; Siapkan alat nomor $toolNumber (M6 otomatis dinonaktifkan)"
                    }
                }
            }

            // Handle Mach3 specific commands mapping
            // G43 H1: Tool length offset compensation. GRBL does not support G43 / H offsets properly or crashes.
            if (line.uppercase().contains("G43")) {
                logs.add("Baris $linesProcessedCounter: Menonaktifkan kompensasi panjang alat G43 H (tidak didukung GRBL/FluidNC standar).")
                line = line.replace(Regex("G43\\s*[Hh]\\d+", RegexOption.IGNORE_CASE), "; G43 (Kompensasi alat dihapus)")
            }

            // M998: Go to tool change height (Mach3 macro) -> Translate to G0 Z safe clearance height
            if (line.uppercase().contains("M998")) {
                logs.add("Baris $linesProcessedCounter: Memetakan makro Mach3 M998 (titik ganti pahat) ke G0 Z10 (posisi aman).")
                line = "G00 Z10.0 ; Titik aman pengganti M998"
            }

            // If requested to convert G20 Inch to G21 Metric (highly popular for global CNC templates)
            if (convertInchToMetric && (originalUnit == "INCH" || line.uppercase().contains("G20"))) {
                // If it is setting the unit mode
                if (line.uppercase().contains("G20")) {
                    line = line.replace("G20", "G21 ; Diubah ke Metrik (mm) dari Inci")
                    logs.add("Menerjemahkan unit G20 ke G21 (Inci ke Metrik mm)")
                }
                
                // Parse coordinates and scale by 25.4
                // We parse parameters: X, Y, Z, I, J, K, F and scale them
                val words = line.split(Regex("\\s+"))
                val rebuiltWords = mutableListOf<String>()
                
                for (word in words) {
                    if (word.startsWith(";")) {
                        // Keep the rest of the line as a comment untouched
                        val commentIndex = line.indexOf(word)
                        if (commentIndex != -1) {
                            rebuiltWords.add(line.substring(commentIndex))
                        }
                        break
                    }
                    
                    val letter = word.take(1).uppercase()
                    if (letter in listOf("X", "Y", "Z", "I", "J", "K", "F")) {
                        val numStr = word.drop(1)
                        val value = numStr.toFloatOrNull()
                        if (value != null) {
                            val scaledValue = if (letter == "F") {
                                // Scale feedrate (Inch/min to mm/min: multiply by 25.4)
                                value * 25.4f
                            } else {
                                // Scale position coordinates
                                value * 25.4f
                            }
                            // Limit to 4 decimal places for precision NC styling
                            val formatted = String.format(Locale.US, "%.4f", scaledValue)
                            rebuiltWords.add("$letter$formatted")
                        } else {
                            rebuiltWords.add(word)
                        }
                    } else {
                        rebuiltWords.add(word)
                    }
                }
                if (rebuiltWords.isNotEmpty()) {
                    line = rebuiltWords.joinToString(" ")
                }
            }

            // Beautify word spacing (e.g. Ensure clear splits like G01X10.23Y50.44 to G1 X10.23 Y50.44)
            if (removeAdornments) {
                // Clean superfluous spaces and trailing comments option
                line = line.replace(Regex(";.*$"), "").trim()
            } else {
                // Insert spacing before letters if mashed together
                // Regular expression to insert spacing between parameters
                val pattern = Pattern.compile("(?<=[A-Za-z0-9])(?=[XxYyZzIiJjKkFfSsMmGgTtHh])")
                val matcher = pattern.matcher(line)
                var spacedLine = matcher.replaceAll(" ")
                // Repair broken comments starting with ';'
                spacedLine = spacedLine.replace("; ", ";").replace(";", "; ")
                spacedLine = spacedLine.replace("G 20", "G20").replace("G 21", "G21")
                spacedLine = spacedLine.replace("G 00", "G00").replace("G 01", "G01").replace("G 02", "G02").replace("G 03", "G03")
                spacedLine = spacedLine.replace("M 0", "M0").replace("M 06", "M06").replace("M 03", "M03").replace("M 04", "M04").replace("M 05", "M05")
                line = spacedLine
            }

            // Uppercase major CNC codes but keep comments unchanged
            val commentSplit = line.indexOf(";")
            if (commentSplit != -1) {
                val codePort = line.substring(0, commentSplit).trim().uppercase(Locale.ROOT)
                val commentPort = line.substring(commentSplit)
                line = if (codePort.isEmpty()) commentPort else "$codePort $commentPort"
            } else {
                line = line.uppercase(Locale.ROOT)
            }

            convertedLines.add(line)
        }

        // Add G21 Metric header if G20 translation happened but wasn't active
        if (convertInchToMetric && originalUnit == "INCH") {
            // Put G21 at index 0
            convertedLines.add(0, "G21 ; Header Metrik Ditambahkan Otomatis")
        }

        // Count total line completions
        if (stripLineNumbers) {
            logs.add("Berhasil menghapus nomor baris (Nxxx) untuk membersihkan pengiriman buffer.")
        }

        logs.add("Konversi berhasil diselesaikan! Memproses $linesProcessedCounter baris kode.")

        return ConversionResult(
            outputGCode = convertedLines.joinToString("\n"),
            logOfChanges = logs,
            originalUnit = originalUnit,
            linesConverted = linesProcessedCounter
        )
    }
}
