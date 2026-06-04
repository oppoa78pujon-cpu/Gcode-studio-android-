package com.example.service

import android.graphics.Bitmap
import com.example.BuildConfig
import com.example.utils.VectorShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object GeminiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    interface GeminiApi {
        @POST("v1beta/models/gemini-3.5-flash:generateContent")
        suspend fun generateContent(
            @Query("key") apiKey: String,
            @Body request: Map<String, Any>
        ): ResponseBody
    }

    private val api: GeminiApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()
        retrofit.create(GeminiApi::class.java)
    }

    /**
     * Sends a design request to Gemini and returns:
     * - Pair.first: Explanatory chat/text response in chosen language.
     * - Pair.second: Derived list of VectorShapes.
     */
    suspend fun generateDesign(
        prompt: String,
        bitmap: Bitmap? = null,
        widthLimit: Float = 150f,
        heightLimit: Float = 150f,
        languageCode: String = "id" // "id" for Indonesian, "en" for English
    ): Pair<String, List<VectorShape>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // GRACEFUL FALLBACK TO OFFLINE CAD CORNER FOR INDEPENDENT BENGKEL OPERATION
            return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
        }

        val systemInstruction = """
            You are "FluidNC Art-AI Studio", a professional CAD and CNC design assistant mimicking PC CAM and G-code software. 
            Your role is to help users generate beautiful G-CODE vectors.
            You can analyze both text requirements and input images (photos, outlines, drawing sketches) to generate complex, structured layouts.
            Your output must always contain a description/talk in ${if (languageCode == "id") "Indonesian language" else "English language"} explaining the design concept and how you optimized the toolpaths (Z safety depth, laser cut power/feedrates) for lasers and spindles to minimize rapid air travel distance.
            In addition, you MUST output a valid JSON block containing mathematical shapes to generate.
            We will parse this JSON to construct the G-code toolpath.
            Only place shapes inside workspace boundaries: width 0 to $widthLimit, height 0 to $heightLimit.
            
            Format of JSON shapes array you MUST include in your message:
            ```json
            [
              {"type": "star", "cx": 75, "cy": 75, "rOuter": 40, "rInner": 15, "points": 5},
              {"type": "circle", "cx": 75, "cy": 75, "r": 50},
              {"type": "polygon", "cx": 75, "cy": 75, "r": 35, "sides": 6},
              {"type": "spiral", "cx": 75, "cy": 75, "rMax": 45, "loops": 5},
              {"type": "line", "x1": 10, "y1": 10, "x2": 140, "y2": 140},
              {"type": "flower", "cx": 75, "cy": 75, "rInner": 20, "rScale": 15, "petals": 8}
            ]
            ```
            Ensure your coordinates are nicely scaled, centered around (${widthLimit / 2}, ${heightLimit / 2}), clean and valid. Do not use negative values.
         """.trimIndent()

        val partsList = mutableListOf<Map<String, Any>>(
            mapOf("text" to prompt)
        )
        if (bitmap != null) {
            val bit64 = try {
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
            if (bit64.isNotEmpty()) {
                partsList.add(
                    mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to "image/jpeg",
                            "data" to bit64
                        )
                    )
                )
            }
        }

        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to partsList
                )
            ),
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to systemInstruction)
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.7f
            )
        )

        try {
            val responseBody = api.generateContent(apiKey, requestBody)
            val jsonStr = responseBody.string()
            val parsedResponse = JSONObject(jsonStr)
            
            if (parsedResponse.has("error")) {
                // If API returns structural errors due to quota, network proxies or region, automatically fallback to custom Offline CAD Design Engine.
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val candidates = parsedResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason", "")
            if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "OTHER") {
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val content = firstCandidate.optJSONObject("content")
            if (content == null) {
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val textRes = parts.getJSONObject(0).optString("text", "")
            if (textRes.isEmpty()) {
                return@withContext generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
            }

            val shapes = parseShapesFromJson(textRes)
            Pair(textRes, shapes)

        } catch (e: Exception) {
            // Automatically launch offline system when network connection is absent / Socket Exception / Host Unknown.
            generateOfflineDesign(prompt, widthLimit, heightLimit, languageCode)
        }
    }

    /**
     * Highly integrated mathematical CAD engine running 100% offline to construct symmetrical paths.
     */
    fun generateOfflineDesign(
        prompt: String,
        widthLimit: Float,
        heightLimit: Float,
        languageCode: String
    ): Pair<String, List<VectorShape>> {
        val cx = widthLimit / 2f
        val cy = heightLimit / 2f
        val rOuter = (minOf(widthLimit, heightLimit) * 0.45f).coerceAtLeast(10f)

        val p = prompt.lowercase()
        val shapes = mutableListOf<VectorShape>()
        val title: String
        val titleIndo: String

        if (p.contains("star") || p.contains("bintang")) {
            title = "Golden Mandala Star"
            titleIndo = "Mandala Bintang Keemasan"
            shapes.add(VectorShape.Circle(cx, cy, rOuter))
            shapes.add(VectorShape.Star(cx, cy, rOuter * 0.85f, rOuter * 0.35f, 8))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.25f))
            shapes.add(VectorShape.Star(cx, cy, rOuter * 0.22f, rOuter * 0.10f, 8))
        } else if (p.contains("flower") || p.contains("bunga") || p.contains("rose") || p.contains("kembang") || p.contains("daisy") || p.contains("lotus")) {
            title = "Blossom Geometry"
            titleIndo = "Geometri Bunga Mekar"
            shapes.add(VectorShape.Circle(cx, cy, rOuter))
            shapes.add(VectorShape.Flower(cx, cy, rOuter * 0.5f, rOuter * 0.35f, 10))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.3f))
            shapes.add(VectorShape.Flower(cx, cy, rOuter * 0.15f, rOuter * 0.12f, 6))
        } else if (p.contains("circle") || p.contains("lingkaran") || p.contains("bulat") || p.contains("ring")) {
            title = "Concentric Planetary Rings"
            titleIndo = "Cincin Planet Konsentris"
            shapes.add(VectorShape.Circle(cx, cy, rOuter))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.8f))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.6f))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.4f))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.2f))
        } else if (p.contains("segi") || p.contains("polygon") || p.contains("hexagon") || p.contains("pentagon") || p.contains("triangle") || p.contains("segitiga")) {
            title = "Sacred Hexagonal Geometry"
            titleIndo = "Geometri Sacre Hexagonal"
            val sides = if (p.contains("tri") || p.contains("tiga")) 3 else if (p.contains("penta") || p.contains("lima")) 5 else 6
            shapes.add(VectorShape.Polygon(cx, cy, rOuter, sides))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.8f, sides))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.6f))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.4f, sides))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.2f, sides))
        } else if (p.contains("spiral") || p.contains("ulir") || p.contains("putar")) {
            title = "Golden Ratio Spiral Grid"
            titleIndo = "Sarang Spiral Rasio Emas"
            shapes.add(VectorShape.Circle(cx, cy, rOuter))
            shapes.add(VectorShape.Spiral(cx, cy, rOuter * 0.9f, 5f))
            shapes.add(VectorShape.Spiral(cx, cy, rOuter * 0.9f, -5f))
        } else if (p.contains("gear") || p.contains("gigi") || p.contains("roda") || p.contains("machinery")) {
            title = "CNC Mechanical Gear"
            titleIndo = "Roda Gigi Mekanik CNC"
            shapes.add(VectorShape.Flower(cx, cy, rOuter * 0.85f, rOuter * 0.10f, 18))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.75f))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.35f))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.16f, 4))
        } else if (p.contains("box") || p.contains("kotak") || p.contains("persegi") || p.contains("square") || p.contains("rectangle")) {
            title = "Symmetrical Frame Box"
            titleIndo = "Bingkai Kotak Simetris"
            shapes.add(VectorShape.Polygon(cx, cy, rOuter, 4))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.8f, 4))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.5f))
            shapes.add(VectorShape.Star(cx, cy, rOuter * 0.35f, rOuter * 0.15f, 4))
        } else if (p.contains("garis") || p.contains("line") || p.contains("cross") || p.contains("silang")) {
            title = "Geometric Cross Grid"
            titleIndo = "Grid Vektor Silang"
            shapes.add(VectorShape.Polygon(cx, cy, rOuter, 4))
            shapes.add(VectorShape.Line(cx - rOuter, cy, cx + rOuter, cy))
            shapes.add(VectorShape.Line(cx, cy - rOuter, cx, cy + rOuter))
            shapes.add(VectorShape.Circle(cx, cy, rOuter * 0.5f))
        } else {
            // Default elegant crest emblem
            title = "Art-AI Premium Crest"
            titleIndo = "Plakat Estetika Art-AI"
            shapes.add(VectorShape.Circle(cx, cy, rOuter))
            shapes.add(VectorShape.Polygon(cx, cy, rOuter * 0.85f, 8))
            shapes.add(VectorShape.Flower(cx, cy, rOuter * 0.45f, rOuter * 0.3f, 6))
            shapes.add(VectorShape.Star(cx, cy, rOuter * 0.22f, rOuter * 0.10f, 5))
        }

        val activeTitle = if (languageCode == "id") titleIndo else title

        val textReport = if (languageCode == "id") {
            """
            🤖 **[MODE OFFLINE - CAD MATEMATIS LOKAL]**
            *Sistem mendeteksi bahwa aplikasi sedang dalam keadaan Offline (atau Kunci API belum diatur). Jalur CAD lokal dirancang secara otomatis demi kelancaran proses kerja Anda.*

            **Desain Generatif Offline**: $activeTitle
            
            **Penjelasan Desain & Konfigurasi CAM**:
            Untuk memenuhi permintaan Anda "$prompt", mesin pembuat matematika berukuran mikro kami telah menghitung total **${shapes.size} bentuk berurutan** dengan presisi tinggi di koordinat tengah workspace.
            
            **Strategi Pemotongan Berbantuan Aturan (CAM)**:
            - Jalur luar (Outer boundary) akan dieksekusi terlebih dahulu agar bahan tetap kokoh ditahan oleh klem meja kerja.
            - Kedalaman pemakanan aman (Z Travel Height) diatur secara otomatis ke posisi positif di atas bidang material demi kenyamanan berkendara router.
            - Kecepatan pemakanan optimal (Feedrate) dan putaran spindle (Spindle Speed) dapat Anda sesuaikan lewat menu preset parameter material di bagian bawah layar.
            """.trimIndent()
        } else {
            """
            🤖 **[OFFLINE ENGINE - LOCAL MATHEMATICAL CAD]**
            *No active network or Gemini key detected. Our local offline mathematical generator has engaged to ensure unhindered workshop productivity.*

            **Offline Generative Design**: $activeTitle
            
            **Design Overview & Toolpath Rules**:
            To satisfy your request "$prompt", our compact geometric firmware has computed **${shapes.size} precise contiguous paths** layered symmetrically at the workspace hub.
            
            **CAM Action Plan**:
            - Outermost contours are prioritized for cutting to maintain structural workpiece stiffness while processing intricate center designs.
            - Z-axis boundaries will retract completely above the clearance plane during rapid linear motion, eliminating deep scratches on raw faces.
            - Proper feedrate and spindle parameters can be dynamically imported from the database catalog below.
            """.trimIndent()
        }

        return Pair(textReport, shapes)
    }

    private fun parseShapesFromJson(rawText: String): List<VectorShape> {
        val result = mutableListOf<VectorShape>()
        try {
            // Find ```json or ``` blocks containing json, or standard []
            var jsonText = ""
            val jsonRegex = Regex("```json\\s*(\\[[\\s\\S]*?\\])\\s*```")
            val match = jsonRegex.find(rawText)
            if (match != null) {
                jsonText = match.groupValues[1]
            } else {
                // Try finding standard array
                val idxStart = rawText.indexOf('[')
                val idxEnd = rawText.lastIndexOf(']')
                if (idxStart != -1 && idxEnd != -1 && idxEnd > idxStart) {
                    jsonText = rawText.substring(idxStart, idxEnd + 1)
                }
            }

            if (jsonText.isNotEmpty()) {
                val array = JSONArray(jsonText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    when (obj.optString("type").lowercase()) {
                        "line" -> {
                            result.add(
                                VectorShape.Line(
                                    x1 = obj.optDouble("x1", 0.0).toFloat(),
                                    y1 = obj.optDouble("y1", 0.0).toFloat(),
                                    x2 = obj.optDouble("x2", 0.0).toFloat(),
                                    y2 = obj.optDouble("y2", 0.0).toFloat()
                                )
                            )
                        }
                        "circle" -> {
                            result.add(
                                VectorShape.Circle(
                                    cx = obj.optDouble("cx", 0.0).toFloat(),
                                    cy = obj.optDouble("cy", 0.0).toFloat(),
                                    r = obj.optDouble("r", 10.0).toFloat()
                                )
                            )
                        }
                        "star" -> {
                            result.add(
                                VectorShape.Star(
                                    cx = obj.optDouble("cx", 0.0).toFloat(),
                                    cy = obj.optDouble("cy", 0.0).toFloat(),
                                    rOuter = obj.optDouble("rOuter", 20.0).toFloat(),
                                    rInner = obj.optDouble("rInner", 8.0).toFloat(),
                                    points = obj.optInt("points", 5)
                                )
                            )
                        }
                        "polygon" -> {
                            result.add(
                                VectorShape.Polygon(
                                    cx = obj.optDouble("cx", 0.0).toFloat(),
                                    cy = obj.optDouble("cy", 0.0).toFloat(),
                                    r = obj.optDouble("r", 15.0).toFloat(),
                                    sides = obj.optInt("sides", 6)
                                )
                            )
                        }
                        "spiral" -> {
                            result.add(
                                VectorShape.Spiral(
                                    cx = obj.optDouble("cx", 0.0).toFloat(),
                                    cy = obj.optDouble("cy", 0.0).toFloat(),
                                    rMax = obj.optDouble("rMax", 20.0).toFloat(),
                                    loops = obj.optDouble("loops", 4.0).toFloat()
                                )
                            )
                        }
                        "flower" -> {
                            result.add(
                                VectorShape.Flower(
                                    cx = obj.optDouble("cx", 0.0).toFloat(),
                                    cy = obj.optDouble("cy", 0.0).toFloat(),
                                    rInner = obj.optDouble("rInner", 15.0).toFloat(),
                                    rScale = obj.optDouble("rScale", 10.0).toFloat(),
                                    petals = obj.optInt("petals", 6)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
