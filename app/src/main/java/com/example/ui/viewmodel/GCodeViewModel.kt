package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.data.ProjectRepository
import com.example.data.RouterBit
import com.example.data.RouterBitRepository
import com.example.service.GeminiService
import com.example.utils.GCodeCompiler
import com.example.utils.GCodeParser
import com.example.utils.ImageToGCodeConverter
import com.example.utils.TextVectorizer
import com.example.utils.VectorShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class GCodeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository
    private val routerBitRepository: RouterBitRepository
    val allRouterBits: StateFlow<List<RouterBit>>

    // Global settings and state
    var language by mutableStateOf("id") // "id" for Indonesian, "en" for English

    private var _isBeginnerMode by mutableStateOf(true)
    var isBeginnerMode: Boolean
        get() = _isBeginnerMode
        set(value) {
            _isBeginnerMode = value
            val prefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_beginner_mode", value).apply()
        }

    var isDarkTheme: Boolean
        get() = com.example.ui.theme.ThemeState.isDarkTheme
        set(value) {
            com.example.ui.theme.ThemeState.isDarkTheme = value
            val prefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_dark_theme", value).apply()
        }

    var selectedTheme: String
        get() = com.example.ui.theme.ThemeState.selectedTheme
        set(value) {
            com.example.ui.theme.ThemeState.selectedTheme = value
            val prefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_theme", value).apply()
        }

    data class MaterialSample(
        val category: String, // "Wood", "Acrylic", "Aluminum"
        val subCategory: String, // e.g. "Plywood", "Hardwood", "Softwood", "MDF"
        val descId: String,
        val descIdIndo: String,
        val laserPower: Int,
        val laserFeedrate: Float,
        val spindleRPM: Int,
        val spindleFeedrate: Float,
        val cutDepth: Float,
        val stepDown: Float,
        val bitType: String
    )

    val materialDatabase = listOf(
        MaterialSample(
            category = "Wood",
            subCategory = "Plywood (3mm)",
            descId = "Excellent choice for laser cutout crafts and light router engraving/carving.",
            descIdIndo = "Pilihan sangat bagus untuk kerajinan potongan laser dan pengukiran router ringan.",
            laserPower = 1000,
            laserFeedrate = 300f,
            spindleRPM = 12000,
            spindleFeedrate = 1200f,
            cutDepth = -3.2f,
            stepDown = 1.0f,
            bitType = "Straight Flute (3.175mm)"
        ),
        MaterialSample(
            category = "Wood",
            subCategory = "Hardwood (Teak/Kayu Jati)",
            descId = "Dense natural lumber. Requires slow, high-torque spindle passes or high-power multi-pass laser engrave.",
            descIdIndo = "Kayu keras padat alami. Membutuhkan jalur spindel lambat torsi tinggi atau pengukiran laser multi-pass daya tinggi.",
            laserPower = 950,
            laserFeedrate = 1200f,
            spindleRPM = 16000,
            spindleFeedrate = 900f,
            cutDepth = -6.0f,
            stepDown = 1.5f,
            bitType = "Upcut Spiral (2.0mm)"
        ),
        MaterialSample(
            category = "Wood",
            subCategory = "MDF Board",
            descId = "Highly homogeneous engineered wood. Great for clean router profiles, but produces fine dust.",
            descIdIndo = "Kayu olahan seragam berkualitas tinggi. Sangat baik untuk profil router bersih, menghasilkan debu halus.",
            laserPower = 1000,
            laserFeedrate = 400f,
            spindleRPM = 15000,
            spindleFeedrate = 1500f,
            cutDepth = -5.0f,
            stepDown = 2.0f,
            bitType = "Straight Flute (3.175mm)"
        ),
        MaterialSample(
            category = "Wood",
            subCategory = "Softwood (Sengon/Pine)",
            descId = "Very easy to mill and laser engrave at high speeds without burning.",
            descIdIndo = "Sangat mudah difraing dan diukir laser pada kecepatan tinggi tanpa gosong terbakar.",
            laserPower = 600,
            laserFeedrate = 1800f,
            spindleRPM = 11000,
            spindleFeedrate = 1400f,
            cutDepth = -4.0f,
            stepDown = 2.0f,
            bitType = "Straight Flute (3.175mm)"
        ),
        MaterialSample(
            category = "Acrylic",
            subCategory = "Cast Acrylic (Akrilik Cor)",
            descId = "Best for elegant engraving. Lasers produce a beautiful frosted white finish; routers route clean glassy cuts.",
            descIdIndo = "Terbaik untuk pengukiran elegan. Laser menghasilkan efek putih buram yang indah; router menghasilkan irisan kaca bersih.",
            laserPower = 500,
            laserFeedrate = 1400f,
            spindleRPM = 10000,
            spindleFeedrate = 600f,
            cutDepth = -3.0f,
            stepDown = 0.5f,
            bitType = "Straight Flute (3.175mm)"
        ),
        MaterialSample(
            category = "Acrylic",
            subCategory = "Extruded Acrylic (Ekstrusi)",
            descId = "More prone to melting. Requires fast feedrates or cooled setups with spindle/router.",
            descIdIndo = "Lebih mudah meleleh karena panas. Perlu feedrate cepat atau pemotongan dingin dengan spindle/router.",
            laserPower = 400,
            laserFeedrate = 1200f,
            spindleRPM = 9000,
            spindleFeedrate = 800f,
            cutDepth = -2.0f,
            stepDown = 0.5f,
            bitType = "Upcut Spiral (2.0mm)"
        ),
        MaterialSample(
            category = "Aluminum",
            subCategory = "Soft Al Sheet (Type 1100)",
            descId = "Thin soft sheet. Engraves beautifully with diamond drag or small spindle bits with low depth.",
            descIdIndo = "Lembaran tipis lunak. Pengukiran sangat indah dengan penyeretan berlian atau pisau spindle kecil kedalaman rendah.",
            laserPower = 1000,
            laserFeedrate = 100f,
            spindleRPM = 16000,
            spindleFeedrate = 300f,
            cutDepth = -0.5f,
            stepDown = 0.1f,
            bitType = "V-Bit (60-Degree Engraving)"
        ),
        MaterialSample(
            category = "Aluminum",
            subCategory = "Hard Al Plate (Type 6061)",
            descId = "Structural hard grade. Requires robust spindle milling, rich lubrication, and very light increments.",
            descIdIndo = "Tingkat kekerasan struktural. Membutuhkan duga spindle yang kokoh, pelumasan cairan, dan pemakanan sangat tipis.",
            laserPower = 1000,
            laserFeedrate = 50f,
            spindleRPM = 18000,
            spindleFeedrate = 200f,
            cutDepth = -1.0f,
            stepDown = 0.05f,
            bitType = "Ball Nose Mill (1.5mm)"
        )
    )

    fun applyMaterialSample(sample: MaterialSample, tool: String) {
        toolType = tool
        if (tool == "LASER") {
            targetPowerS = sample.laserPower
            feedrateCut = sample.laserFeedrate
        } else {
            maxSValue = sample.spindleRPM
            feedrateCut = sample.spindleFeedrate
            targetCutZ = sample.cutDepth
            zStepPerPass = sample.stepDown
            if (sample.bitType.isNotEmpty()) {
                routerBitType = sample.bitType
            }
        }
        selectedPresetName = "${sample.subCategory} ($tool)"
        compileCurrentPaths()
    }
    
    // CNC Workspace size limit (in mm)
    var workspaceWidth by mutableStateOf(150f)
    var workspaceHeight by mutableStateOf(150f)
    var materialThickness by mutableStateOf(5.0f)

    // Current compilation settings
    var toolType by mutableStateOf("LASER") // "LASER", "SPINDLE", or "ROUTER"
    var laserOnCommand by mutableStateOf("M4") // "M4" or "M3"
    var maxSValue by mutableStateOf(1000)
    var targetPowerS by mutableStateOf(1000)
    var feedrateCut by mutableStateOf(1200f)
    var feedrateTravel by mutableStateOf(3000f)
    var routerBitType by mutableStateOf("Straight Flute (3.175mm)")
    
    // Spindle variables
    var safeZ by mutableStateOf(5.0f)
    var targetCutZ by mutableStateOf(-1.5f)
    var zStepPerPass by mutableStateOf(0.5f)
    var feedratePlunge by mutableStateOf(200f)
    var multiPassStrategy by mutableStateOf("DEPTH_FIRST") // "DEPTH_FIRST" or "LAYER_FIRST"
    var gcodeDecimalPrecision by mutableStateOf(3)        // 2, 3, or 4 decimals

    fun updateMultiPassStrategy(strategy: String) {
        multiPassStrategy = strategy
        val appPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appPrefs.edit().putString("multi_pass_strategy", strategy).apply()
        compileCurrentPaths()
    }

    fun updateGCodeDecimalPrecision(decimals: Int) {
        gcodeDecimalPrecision = decimals
        val appPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appPrefs.edit().putInt("gcode_decimal_precision", decimals).apply()
        compileCurrentPaths()
    }

    // Starting point offset from workpiece origin (Work Coordinate System Offset)
    var startOffsetX by mutableStateOf(0f)
    var startOffsetY by mutableStateOf(0f)

    // User-defined G-Code generation defaults (applied globally unless overridden)
    var defaultFeedrateCut by mutableStateOf(1200f)
    var defaultFeedrateTravel by mutableStateOf(3000f)
    var defaultFeedratePlunge by mutableStateOf(200f)
    var defaultSafeZ by mutableStateOf(5.0f)

    fun updateDefaultSettings(cut: Float, travel: Float, plunge: Float, safeZVal: Float) {
        val appPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        appPrefs.edit().apply {
            putFloat("default_feedrate_cut", cut)
            putFloat("default_feedrate_travel", travel)
            putFloat("default_feedrate_plunge", plunge)
            putFloat("default_safe_z", safeZVal)
        }.apply()

        defaultFeedrateCut = cut
        defaultFeedrateTravel = travel
        defaultFeedratePlunge = plunge
        defaultSafeZ = safeZVal

        // Apply immediately to the active working states
        feedrateCut = cut
        feedrateTravel = travel
        feedratePlunge = plunge
        safeZ = safeZVal

        compileCurrentPaths()
    }

    // Toolpath optimization control
    var optimizeToolpathOn by mutableStateOf(true)

    // AI inputs configuration
    var aiIncludeImage by mutableStateOf(false)

    // Preset configurations structures
    var activePresetsList by mutableStateOf<List<CNCParamPreset>>(emptyList())
    var selectedPresetName by mutableStateOf("")

    // Font list configuration
    var importedFontsList by mutableStateOf<List<Pair<String, String>>>(emptyList()) // Clean name, absolute path

    private val fontsDir = File(application.filesDir, "fonts")

    // Current editor paths
    var activePaths by mutableStateOf<List<List<Offset>>>(emptyList())
    private var _currentGCode by mutableStateOf("")
    var currentGCode: String
        get() = _currentGCode
        set(value) {
            _currentGCode = com.example.utils.GCodeCompiler.sanitize(value)
        }
    var parsedSegments by mutableStateOf<List<GCodeParser.GCodeSegment>>(emptyList())

    // Mach3/Multi-format CNC to Standard NC Converter State
    var converterRawInput by mutableStateOf("")
    var converterOutput by mutableStateOf("")
    var converterLog by mutableStateOf<List<String>>(emptyList())
    var converterStripLineNums by mutableStateOf(true)
    var converterScaleInchToMm by mutableStateOf(true)
    var converterChangeToolPause by mutableStateOf(true)
    var converterRemoveAdornments by mutableStateOf(false)
    var converterOriginalUnit by mutableStateOf("UNKNOWN")
    var hasConvertedOnce by mutableStateOf(false)

    // Design Inputs: Text Tab
    var creatorText by mutableStateOf("FluidNC")
    var creatorTextSize by mutableStateOf(20f) // height in mm
    var selectedFont by mutableStateOf("SANS_SERIF") // SANS_SERIF, SERIF, MONOSPACE, or imported file paths
    var letterSpacing by mutableStateOf(1.0f)

    // Design Inputs: Image Tab
    var importedBitmap by mutableStateOf<Bitmap?>(null)
    var imageProcessMode by mutableStateOf(ImageToGCodeConverter.ProcessMode.OUTLINE)
    var imageResolution by mutableStateOf(2.0f) // pixels per mm (dpmm)
    var imageThreshold by mutableStateOf(0.5f)
    var imageInvertColors by mutableStateOf(false)
    var isProcessingImage by mutableStateOf(false)
    var imageProcessError by mutableStateOf<String?>(null)

    // Design Inputs: AI Tab
    var aiPrompt by mutableStateOf("")
    var aiChatHistory by mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) // text to isUser
    var aiIsLoading by mutableStateOf(false)

    // Simulation / Playback variables for real-time live previewing
    var isSimulating by mutableStateOf(false)
    var simulationSegmentProgress by mutableStateOf(0f) // 0.0 to 1.0 of current segment
    var maxSegmentsToDraw by mutableStateOf(0)

    val allProjects: StateFlow<List<Project>>

    data class CNCParamPreset(
        val name: String,
        val isUserPreset: Boolean = false,
        val toolType: String, // "LASER", "SPINDLE", or "ROUTER"
        val laserPowerS: Int = 1000,
        val laserOnCommand: String = "M4",
        val laserFrequencyHz: Float = 5000f,
        val spindleMaxRPM: Int = 10000,
        val spindleDirection: String = "M3",
        val feedrateCut: Float = 1200f,
        val feedrateTravel: Float = 3000f,
        val feedratePlunge: Float = 200f,
        val safeZ: Float = 5.0f,
        val targetCutZ: Float = -1.5f,
        val zStepPerPass: Float = 0.5f,
        val materialThickness: Float = 5.0f,
        val routerBitType: String = "Straight Flute (3.175mm)"
    )

    private val seedPresets = listOf(
        CNCParamPreset(
            name = "Plywood Cut (Laser)",
            isUserPreset = false,
            toolType = "LASER",
            laserPowerS = 1000,
            laserOnCommand = "M3",
            laserFrequencyHz = 2000f,
            feedrateCut = 300f,
            feedrateTravel = 2500f,
            materialThickness = 3.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "Plywood Engrave (Laser)",
            isUserPreset = false,
            toolType = "LASER",
            laserPowerS = 600,
            laserOnCommand = "M4",
            laserFrequencyHz = 5000f,
            feedrateCut = 1800f,
            feedrateTravel = 3500f,
            materialThickness = 3.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "Acrylic Engrave (Laser)",
            isUserPreset = false,
            toolType = "LASER",
            laserPowerS = 450,
            laserOnCommand = "M4",
            laserFrequencyHz = 4000f,
            feedrateCut = 1500f,
            feedrateTravel = 3000f,
            materialThickness = 2.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "Softwood Carve (Spindle)",
            isUserPreset = false,
            toolType = "SPINDLE",
            spindleMaxRPM = 12000,
            spindleDirection = "M3",
            feedrateCut = 1000f,
            feedrateTravel = 2000f,
            feedratePlunge = 150f,
            safeZ = 5.0f,
            targetCutZ = -3.5f,
            zStepPerPass = 1.0f,
            materialThickness = 12.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "Acrylic Cut (Spindle)",
            isUserPreset = false,
            toolType = "SPINDLE",
            spindleMaxRPM = 9000,
            spindleDirection = "M3",
            feedrateCut = 600f,
            feedrateTravel = 2000f,
            feedratePlunge = 100f,
            safeZ = 4.0f,
            targetCutZ = -6.0f,
            zStepPerPass = 0.5f,
            materialThickness = 6.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "Aluminium Engrave (Spindle)",
            isUserPreset = false,
            toolType = "SPINDLE",
            spindleMaxRPM = 16000,
            spindleDirection = "M3",
            feedrateCut = 250f,
            feedrateTravel = 1500f,
            feedratePlunge = 50f,
            safeZ = 3.0f,
            targetCutZ = -0.3f,
            zStepPerPass = 0.05f,
            materialThickness = 5.0f,
            routerBitType = ""
        ),
        CNCParamPreset(
            name = "MDF Cut (Router)",
            isUserPreset = false,
            toolType = "ROUTER",
            spindleMaxRPM = 18000,
            spindleDirection = "M3",
            feedrateCut = 1500f,
            feedrateTravel = 3000f,
            feedratePlunge = 300f,
            safeZ = 6.0f,
            targetCutZ = -5.5f,
            zStepPerPass = 1.5f,
            materialThickness = 5.0f,
            routerBitType = "Straight Flute (3.175mm)"
        ),
        CNCParamPreset(
            name = "Hardwood V-Carve (Router)",
            isUserPreset = false,
            toolType = "ROUTER",
            spindleMaxRPM = 16000,
            spindleDirection = "M3",
            feedrateCut = 1200f,
            feedrateTravel = 2500f,
            feedratePlunge = 200f,
            safeZ = 5.0f,
            targetCutZ = -2.0f,
            zStepPerPass = 1.0f,
            materialThickness = 10.0f,
            routerBitType = "V-Bit (60-Degree Engraving)"
        )
    )

    init {
        val db = AppDatabase.getDatabase(application)
        val projectDao = db.projectDao()
        repository = ProjectRepository(projectDao)
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val routerBitDao = db.routerBitDao()
        routerBitRepository = RouterBitRepository(routerBitDao)
        allRouterBits = routerBitRepository.allRouterBits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            if (routerBitRepository.getCount() == 0) {
                val defaults = listOf(
                    RouterBit(name = "Straight Flute (3.175mm)", type = "Straight Flute", diameter = 3.175f, flutes = 2, isCustom = false),
                    RouterBit(name = "Upcut Spiral (2.0mm)", type = "Upcut Spiral", diameter = 2.0f, flutes = 1, isCustom = false),
                    RouterBit(name = "Downcut Spiral (3.175mm)", type = "Downcut Spiral", diameter = 3.175f, flutes = 2, isCustom = false),
                    RouterBit(name = "V-Bit (60-Degree Engraving)", type = "V-Bit", diameter = 3.175f, flutes = 1, isCustom = false),
                    RouterBit(name = "V-Bit (90-Degree Chamfer)", type = "V-Bit", diameter = 6.35f, flutes = 2, isCustom = false),
                    RouterBit(name = "Ball Nose Mill (1.5mm)", type = "Ball Nose", diameter = 1.5f, flutes = 2, isCustom = false),
                    RouterBit(name = "Ball Nose Mill (3.175mm)", type = "Ball Nose", diameter = 3.175f, flutes = 2, isCustom = false),
                    RouterBit(name = "Compression Bit (3.175mm)", type = "Compression", diameter = 3.175f, flutes = 2, isCustom = false),
                    RouterBit(name = "T-Slot Router Bit (9.5mm)", type = "T-Slot", diameter = 9.5f, flutes = 2, isCustom = false),
                    RouterBit(name = "Core Box Bit (6.35mm)", type = "Core Box", diameter = 6.35f, flutes = 2, isCustom = false)
                )
                defaults.forEach {
                    routerBitRepository.insertRouterBit(it)
                }
            }
        }

        // Load theme choice
        val appPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        com.example.ui.theme.ThemeState.isDarkTheme = appPrefs.getBoolean("is_dark_theme", true) // Default to dark mode!
        com.example.ui.theme.ThemeState.selectedTheme = appPrefs.getString("selected_theme", "SLATE") ?: "SLATE"
        _isBeginnerMode = appPrefs.getBoolean("is_beginner_mode", true)

        // Load G-Code compile defaults
        defaultFeedrateCut = appPrefs.getFloat("default_feedrate_cut", 1200f)
        defaultFeedrateTravel = appPrefs.getFloat("default_feedrate_travel", 3000f)
        defaultFeedratePlunge = appPrefs.getFloat("default_feedrate_plunge", 200f)
        defaultSafeZ = appPrefs.getFloat("default_safe_z", 5.0f)

        // Initialize active variables to their stored defaults
        feedrateCut = defaultFeedrateCut
        feedrateTravel = defaultFeedrateTravel
        feedratePlunge = defaultFeedratePlunge
        safeZ = defaultSafeZ

        multiPassStrategy = appPrefs.getString("multi_pass_strategy", "DEPTH_FIRST") ?: "DEPTH_FIRST"
        gcodeDecimalPrecision = appPrefs.getInt("gcode_decimal_precision", 3)

        // Ensure folders exist and load dependencies
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
        loadImportedFonts()
        loadPresetsCombined()

        // Seed default drawing
        generateTextToolpath()
    }

    fun loadImportedFonts() {
        val files = fontsDir.listFiles()?.filter { it.extension.lowercase() in listOf("ttf", "otf") } ?: emptyList()
        importedFontsList = files.map { file ->
            val dispName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                .split(" ").joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
            Pair(dispName, file.absolutePath)
        }
    }

    fun importFont(fileName: String, inputStream: java.io.InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanName = fileName.replace(" ", "_").lowercase()
                val destFile = File(fontsDir, cleanName)
                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loadImportedFonts()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFont(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
                loadImportedFonts()
                if (selectedFont == filePath) {
                    selectedFont = "SANS_SERIF"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPresetsCombined() {
        val userPresets = getUserPresetsFromPrefs()
        activePresetsList = seedPresets + userPresets
    }

    private fun getUserPresetsFromPrefs(): List<CNCParamPreset> {
        val prefs = getApplication<Application>().getSharedPreferences("cnc_presets", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("user_presets_list", "[]") ?: "[]"
        val result = mutableListOf<CNCParamPreset>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    CNCParamPreset(
                        name = obj.optString("name", "Preset"),
                        isUserPreset = true,
                        toolType = obj.optString("toolType", "LASER"),
                        laserPowerS = obj.optInt("laserPowerS", 1000),
                        laserOnCommand = obj.optString("laserOnCommand", "M4"),
                        laserFrequencyHz = obj.optDouble("laserFrequencyHz", 5000.0).toFloat(),
                        spindleMaxRPM = obj.optInt("spindleMaxRPM", 10000),
                        spindleDirection = obj.optString("spindleDirection", "M3"),
                        feedrateCut = obj.optDouble("feedrateCut", 1200.0).toFloat(),
                        feedrateTravel = obj.optDouble("feedrateTravel", 3000.0).toFloat(),
                        feedratePlunge = obj.optDouble("feedratePlunge", 200.0).toFloat(),
                        safeZ = obj.optDouble("safeZ", 5.0).toFloat(),
                        targetCutZ = obj.optDouble("targetCutZ", -1.5).toFloat(),
                        zStepPerPass = obj.optDouble("zStepPerPass", 0.5).toFloat(),
                        materialThickness = obj.optDouble("materialThickness", 5.0).toFloat(),
                        routerBitType = obj.optString("routerBitType", "Straight Flute (3.175mm)")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun saveUserPresetsToPrefs(list: List<CNCParamPreset>) {
        val prefs = getApplication<Application>().getSharedPreferences("cnc_presets", Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("name", item.name)
            obj.put("toolType", item.toolType)
            obj.put("laserPowerS", item.laserPowerS)
            obj.put("laserOnCommand", item.laserOnCommand)
            obj.put("laserFrequencyHz", item.laserFrequencyHz)
            obj.put("spindleMaxRPM", item.spindleMaxRPM)
            obj.put("spindleDirection", item.spindleDirection)
            obj.put("feedrateCut", item.feedrateCut)
            obj.put("feedrateTravel", item.feedrateTravel)
            obj.put("feedratePlunge", item.feedratePlunge)
            obj.put("safeZ", item.safeZ)
            obj.put("targetCutZ", item.targetCutZ)
            obj.put("zStepPerPass", item.zStepPerPass)
            obj.put("materialThickness", item.materialThickness)
            obj.put("routerBitType", item.routerBitType)
            array.put(obj)
        }
        prefs.edit().putString("user_presets_list", array.toString()).apply()
    }

    fun saveNewUserPreset(presetName: String) {
        val newPreset = CNCParamPreset(
            name = presetName,
            isUserPreset = true,
            toolType = toolType,
            laserPowerS = targetPowerS,
            laserOnCommand = laserOnCommand,
            laserFrequencyHz = 5000f,
            spindleMaxRPM = maxSValue,
            spindleDirection = "M3",
            feedrateCut = feedrateCut,
            feedrateTravel = feedrateTravel,
            feedratePlunge = feedratePlunge,
            safeZ = safeZ,
            targetCutZ = targetCutZ,
            zStepPerPass = zStepPerPass,
            materialThickness = materialThickness,
            routerBitType = routerBitType
        )
        val list = getUserPresetsFromPrefs().toMutableList()
        list.removeAll { it.name.uppercase() == presetName.uppercase() }
        list.add(newPreset)
        saveUserPresetsToPrefs(list)
        loadPresetsCombined()
    }

    fun applyPreset(preset: CNCParamPreset) {
        selectedPresetName = preset.name
        toolType = preset.toolType
        
        targetPowerS = preset.laserPowerS
        laserOnCommand = preset.laserOnCommand
        
        maxSValue = preset.spindleMaxRPM
        
        feedrateCut = preset.feedrateCut
        feedrateTravel = preset.feedrateTravel
        feedratePlunge = preset.feedratePlunge
        
        safeZ = preset.safeZ
        targetCutZ = preset.targetCutZ
        zStepPerPass = preset.zStepPerPass
        materialThickness = preset.materialThickness
        if (preset.routerBitType.isNotEmpty()) {
            routerBitType = preset.routerBitType
        }
        
        compileCurrentPaths()
    }

    fun deleteUserPreset(preset: CNCParamPreset) {
        val list = getUserPresetsFromPrefs().toMutableList()
        list.removeAll { it.name == preset.name }
        saveUserPresetsToPrefs(list)
        loadPresetsCombined()
        if (selectedPresetName == preset.name) {
            selectedPresetName = ""
        }
    }

    /**
     * Converts Creator Text into structural paths and compiles to G-code
     */
    fun generateTextToolpath() {
        viewModelScope.launch {
            val paths = TextVectorizer.vectorize(
                text = creatorText,
                fontFamily = selectedFont,
                textSizeMm = creatorTextSize,
                letterSpacingScale = letterSpacing
            )
            // Center the text toolpath in the workspace
            val centeredPaths = centerPathsInWorkspace(paths)
            activePaths = centeredPaths
            compileCurrentPaths()
        }
    }

    /**
     * Converts Imported Image into structural paths and compiles to G-code
     */
    fun generateImageToolpath() {
        val bmp = importedBitmap ?: return
        isProcessingImage = true
        imageProcessError = null
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ImageToGCodeConverter.processImage(
                        bitmap = bmp,
                        targetWidthMm = workspaceWidth * 0.8f,
                        targetHeightMm = workspaceHeight * 0.8f,
                        mode = imageProcessMode,
                        resolutionDpmm = imageResolution,
                        threshold = imageThreshold,
                        invertColors = imageInvertColors
                    )
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val centeredPaths = centerPathsInWorkspace(result.previewPaths)
                    activePaths = centeredPaths
                    
                    val settings = GCodeCompiler.CompilationSettings(
                        toolType = toolType,
                        laserOnCommand = laserOnCommand,
                        maxSValue = maxSValue,
                        targetPowerS = targetPowerS,
                        feedrateCut = feedrateCut,
                        feedrateTravel = feedrateTravel,
                        safeZ = safeZ,
                        targetCutZ = targetCutZ,
                        zStepPerPass = zStepPerPass,
                        feedratePlunge = feedratePlunge,
                        routerBitType = routerBitType
                    )
                    val pathsToCompile = if (optimizeToolpathOn) {
                        GCodeCompiler.optimizeToolpath(activePaths)
                    } else {
                        activePaths
                    }
                    val compiled = GCodeCompiler.compilePaths(pathsToCompile, settings)
                    val parsed = GCodeParser.parseGCode(compiled)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        currentGCode = compiled
                        parsedSegments = parsed
                        maxSegmentsToDraw = parsed.size
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    imageProcessError = e.localizedMessage ?: "Unknown Image Conversion Error"
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isProcessingImage = false
                }
            }
        }
    }

    /**
     * Submits Prompt to Gemini, adds AI Chat to log, parses shapes, and updates paths.
     */
    fun generateAiDesign() {
        val prompt = aiPrompt.trim()
        if (prompt.isEmpty()) return

        // Add user chat
        aiChatHistory = aiChatHistory + Pair(prompt, true)
        aiPrompt = ""
        aiIsLoading = true

        val bmpToPass = if (aiIncludeImage) importedBitmap else null

        viewModelScope.launch {
            val res = GeminiService.generateDesign(
                prompt = prompt,
                bitmap = bmpToPass,
                widthLimit = workspaceWidth,
                heightLimit = workspaceHeight,
                languageCode = language
            )

            // Add AI response
            aiChatHistory = aiChatHistory + Pair(res.first, false)
            aiIsLoading = false

            val shapes = res.second
            if (shapes.isNotEmpty()) {
                val paths = shapes.flatMap { shape ->
                    listOf(shape.toPoints())
                }
                activePaths = centerPathsInWorkspace(paths)
                compileCurrentPaths()
            }
        }
    }

    /**
     * Compiles current paths in activePaths using compilation settings and triggers backplot parser.
     */
    fun compileCurrentPaths() {
        val settings = GCodeCompiler.CompilationSettings(
            toolType = toolType,
            laserOnCommand = laserOnCommand,
            maxSValue = maxSValue,
            targetPowerS = targetPowerS,
            feedrateCut = feedrateCut,
            feedrateTravel = feedrateTravel,
            safeZ = safeZ,
            targetCutZ = targetCutZ,
            zStepPerPass = zStepPerPass,
            feedratePlunge = feedratePlunge,
            addCoordinatesOffset = Offset(startOffsetX, startOffsetY),
            routerBitType = routerBitType,
            multiPassStrategy = multiPassStrategy,
            gcodeDecimalPrecision = gcodeDecimalPrecision
        )
        // Optimize toolpath based on proximity if turned on
        val pathsToCompile = if (optimizeToolpathOn) {
            GCodeCompiler.optimizeToolpath(activePaths)
        } else {
            activePaths
        }
        currentGCode = GCodeCompiler.compilePaths(pathsToCompile, settings)
        parsedSegments = GCodeParser.parseGCode(currentGCode)
        maxSegmentsToDraw = parsedSegments.size
    }

    /**
     * Saves the current G-code work as a project in the database.
     */
    fun saveProject(projectName: String) {
        viewModelScope.launch {
            val proj = Project(
                name = projectName,
                gcode = currentGCode,
                toolType = toolType,
                modeSettings = "S:$targetPowerS,F:$feedrateCut,LaserMode:$laserOnCommand,CutZ:$targetCutZ,SafeZ:$safeZ",
                width = workspaceWidth,
                height = workspaceHeight
            )
            repository.insertProject(proj)
        }
    }

    /**
     * Loads a project from the database back into the active working state.
     */
    fun loadProject(project: Project) {
        currentGCode = project.gcode
        toolType = project.toolType
        workspaceWidth = project.width
        workspaceHeight = project.height
        
        // Parse segments for preview
        parsedSegments = GCodeParser.parseGCode(currentGCode)
        maxSegmentsToDraw = parsedSegments.size
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun clearAllProjects() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun insertRouterBit(bit: RouterBit) {
        viewModelScope.launch {
            routerBitRepository.insertRouterBit(bit)
        }
    }

    fun updateRouterBit(bit: RouterBit) {
        viewModelScope.launch {
            routerBitRepository.updateRouterBit(bit)
        }
    }

    fun deleteRouterBit(bit: RouterBit) {
        viewModelScope.launch {
            routerBitRepository.deleteRouterBit(bit)
            if (routerBitType == bit.name) {
                routerBitType = "Straight Flute (3.175mm)"
            }
        }
    }

    /**
     * Utility to translate mathematical paths so they sit beautifully in the center of the viewport
     */
    fun centerPathsInWorkspace(paths: List<List<Offset>>): List<List<Offset>> {
        if (paths.isEmpty()) return emptyList()

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (contour in paths) {
            for (p in contour) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
        }

        val designWidth = maxX - minX
        val designHeight = maxY - minY

        val offsetX = (workspaceWidth - designWidth) / 2f - minX
        val offsetY = (workspaceHeight - designHeight) / 2f - minY

        return paths.map { contour ->
            contour.map { pt ->
                Offset(pt.x + offsetX, pt.y + offsetY)
            }
        }
    }

    /**
     * Executes Mach3 / Multi-CNC conversion with active switches.
     */
    fun runMach3Conversion() {
        val result = com.example.utils.CNCConverter.convertToStandardNC(
            input = converterRawInput,
            stripLineNumbers = converterStripLineNums,
            convertInchToMetric = converterScaleInchToMm,
            convertToolChangeToPause = converterChangeToolPause,
            removeAdornments = converterRemoveAdornments
        )
        converterOutput = result.outputGCode
        converterLog = result.logOfChanges
        converterOriginalUnit = result.originalUnit
        hasConvertedOnce = true
    }

    /**
     * Loads the converted G-code straight into the Live Preview area and compiles segments for view.
     */
    fun loadConvertedGCodeIntoApp() {
        if (converterOutput.isNotEmpty()) {
            currentGCode = converterOutput
            parsedSegments = GCodeParser.parseGCode(currentGCode)
            maxSegmentsToDraw = parsedSegments.size
            activePaths = emptyList() // Clear raw canvas shapes since we are in file mode
        }
    }

    // ==========================================
    // FLUIDNC OFFLINE CONTROL CENTER APIS & STATE
    // ==========================================
    var fluidNCIpAddress by mutableStateOf("192.168.0.1")
    
    val cleanIP: String
        get() = fluidNCIpAddress.trim()
            .replace("https://", "")
            .replace("http://", "")
            .trimEnd('/')

    var fluidNCConnectionStatus by mutableStateOf("UNKNOWN") // "CONNECTED", "FAILED", "UNKNOWN", "TESTING"
    var fluidNCJogDistance by mutableStateOf(10f) // in mm
    var fluidNCFeedrate by mutableStateOf(2000f) // in mm/min
    var fluidNCConsoleResponse by mutableStateOf<List<String>>(listOf("Offline Controller Siap. Sambungkan ke FluidNC (192.168.0.1)"))
    var isStreamingToFluidNC by mutableStateOf(false)
    var fluidNCStreamProgress by mutableStateOf(0f)
    var fluidNCStreamCurrentIndex by mutableStateOf(0)
    var fluidNCStreamTotal by mutableStateOf(0)

    // CNC Z-Probe States
    var probeFeedrate by mutableStateOf(100f)
    var probeMaxDistance by mutableStateOf(-15f)
    var probeThickness by mutableStateOf(1.5f)
    var isProbing by mutableStateOf(false)

    fun startZAxisProbe() {
        if (isProbing) return
        isProbing = true
        appendConsoleLog("Memulai Proses Probe Z-Axis Z...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Send G38.2 Z-Probe Command
                appendConsoleLog("Mengirim G38.2: Mencari permukaan touchplate dng kecepatan ${probeFeedrate.toInt()}mm/min...")
                val probeCmd = "G38.2 Z${String.format(java.util.Locale.US, "%.3f", probeMaxDistance)} F${probeFeedrate.toInt()}"
                
                val encoded1 = java.net.URLEncoder.encode(probeCmd, "UTF-8")
                val url1 = java.net.URL("http://$cleanIP/gcode?gcode=$encoded1")
                val conn1 = url1.openConnection() as java.net.HttpURLConnection
                conn1.connectTimeout = 4000
                conn1.requestMethod = "GET"
                val code1 = conn1.responseCode
                val res1 = if (code1 == 200) conn1.inputStream.bufferedReader().use { it.readText() }.trim() else "Error HTTP $code1"
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("G38.2 Respon: $res1")
                }
                
                kotlinx.coroutines.delay(1000) // Settle
                
                // 2. Set coordinate zero offset G92 Z[thickness]
                appendConsoleLog("Mengatur koordinat benda kerja Z ke tebal touchplate: ${probeThickness}mm...")
                val zeroCmd = "G92 Z${String.format(java.util.Locale.US, "%.3f", probeThickness)}"
                val encoded2 = java.net.URLEncoder.encode(zeroCmd, "UTF-8")
                val url2 = java.net.URL("http://$cleanIP/gcode?gcode=$encoded2")
                val conn2 = url2.openConnection() as java.net.HttpURLConnection
                conn2.connectTimeout = 4000
                conn2.requestMethod = "GET"
                val code2 = conn2.responseCode
                val res2 = if (code2 == 200) conn2.inputStream.bufferedReader().use { it.readText() }.trim() else "Error HTTP $code2"
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("G92 Respon: $res2")
                }
                
                kotlinx.coroutines.delay(600)
                
                // 3. Lift tool up to safety height
                appendConsoleLog("Menaikkan pahat Z aman ke +5.0mm...")
                val liftCmd = "G0 Z5.0"
                val encoded3 = java.net.URLEncoder.encode(liftCmd, "UTF-8")
                val url3 = java.net.URL("http://$cleanIP/gcode?gcode=$encoded3")
                val conn3 = url3.openConnection() as java.net.HttpURLConnection
                conn3.connectTimeout = 4000
                conn3.requestMethod = "GET"
                val code3 = conn3.responseCode
                val res3 = if (code3 == 200) conn3.inputStream.bufferedReader().use { it.readText() }.trim() else "Error HTTP $code3"
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("G0 Lift Respon: $res3")
                    appendConsoleLog("★ PROBE BERHASIL SELESAI Sempurna!")
                    isProbing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("PROBE GAGAL: ${e.localizedMessage ?: "timeout/gagal menyambung"}")
                    isProbing = false
                }
            }
        }
    }

    fun testFluidConnection() {
        fluidNCConnectionStatus = "TESTING"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$cleanIP/status")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                
                val code = conn.responseCode
                if (code == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        fluidNCConnectionStatus = "CONNECTED"
                        appendConsoleLog("Terhubung ke FluidNC! Status: $responseText")
                    }
                } else {
                    val alternativeUrl = java.net.URL("http://$cleanIP/")
                    val altConn = alternativeUrl.openConnection() as java.net.HttpURLConnection
                    altConn.connectTimeout = 2000
                    altConn.requestMethod = "GET"
                    val altCode = altConn.responseCode
                    if (altCode == 200) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            fluidNCConnectionStatus = "CONNECTED"
                            appendConsoleLog("Terhubung ke FluidNC Web Server!")
                        }
                    } else {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            fluidNCConnectionStatus = "FAILED"
                            appendConsoleLog("Gagal menyambung ke $cleanIP (Kode: $code)")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    fluidNCConnectionStatus = "FAILED"
                    appendConsoleLog("Koneksi gagal ke $cleanIP: ${e.localizedMessage ?: "Timeout"}")
                }
            }
        }
    }

    fun appendConsoleLog(line: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        fluidNCConsoleResponse = (fluidNCConsoleResponse + "[$time] $line").takeLast(50)
    }

    fun sendFluidCommand(gcode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedGCode = java.net.URLEncoder.encode(gcode, "UTF-8")
                val url = java.net.URL("http://$cleanIP/gcode?gcode=$encodedGCode")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.requestMethod = "GET"
                
                val code = conn.responseCode
                val responseText = if (code == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }.trim()
                } else {
                    "HTTP Error $code"
                }
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("Kirim: \"$gcode\" -> Respon: $responseText")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    appendConsoleLog("Kirim: \"$gcode\" -> Gagal: ${e.localizedMessage ?: "Timeout"}")
                }
            }
        }
    }

    private var streamJob: kotlinx.coroutines.Job? = null

    fun startStreamingGCode() {
        if (currentGCode.isBlank()) {
            appendConsoleLog("Gagal Memulai: Kode G-Code kosong!")
            return
        }
        val lines = currentGCode.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith(";") }
        if (lines.isEmpty()) {
            appendConsoleLog("Gagal Memulai: Tidak ada instruksi G-code valid")
            return
        }
        
        isStreamingToFluidNC = true
        fluidNCStreamTotal = lines.size
        fluidNCStreamCurrentIndex = 0
        fluidNCStreamProgress = 0f
        
        appendConsoleLog("Mengirim program CNC... (${lines.size} baris)")
        
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            for ((index, line) in lines.withIndex()) {
                if (!isStreamingToFluidNC) break
                
                try {
                    val encoded = java.net.URLEncoder.encode(line, "UTF-8")
                    val url = java.net.URL("http://$cleanIP/gcode?gcode=$encoded")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val res = if (code == 200) {
                        conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    } else "Gagal"
                    
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        fluidNCStreamCurrentIndex = index + 1
                        fluidNCStreamProgress = (index + 1).toFloat() / lines.size.toFloat()
                        appendConsoleLog("[$fluidNCStreamCurrentIndex/$fluidNCStreamTotal] $line -> $res")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        appendConsoleLog("Gagal \"$line\": ${e.localizedMessage}")
                    }
                }
                kotlinx.coroutines.delay(150)
            }
            
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                isStreamingToFluidNC = false
                appendConsoleLog("Pengiriman program selesai!")
            }
        }
    }

    fun stopStreamingGCode() {
        isStreamingToFluidNC = false
        streamJob?.cancel()
        appendConsoleLog("Pengiriman dihentikan operator.")
    }

    // ==========================================
    // DXF OFFLINE PLOTTER & CONVERTER STATES
    // ==========================================
    var dxfFileName by mutableStateOf<String?>(null)
    var dxfScaleFactor by mutableStateOf(1.0f)
    var dxfUnitIsInch by mutableStateOf(false)
    var isProcessingDxf by mutableStateOf(false)
    var dxfProcessError by mutableStateOf<String?>(null)
    var dxfRawPaths by mutableStateOf<List<List<Offset>>>(emptyList())

    fun importDxfFromStream(inputStream: java.io.InputStream, fileName: String) {
        dxfFileName = fileName
        isProcessingDxf = true
        dxfProcessError = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val parsed = com.example.utils.DXFParser.parse(inputStream)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (parsed.isEmpty()) {
                        dxfProcessError = if (language == "id") "Gagal mengurai DXF: File kosong atau format tidak didukung" else "DXF Parsing failed: Empty file or unsupported format"
                        isProcessingDxf = false
                    } else {
                        dxfRawPaths = parsed
                        applyDxfPathsAndCompile()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    dxfProcessError = e.localizedMessage ?: "Unknown DXF parse error"
                    isProcessingDxf = false
                }
            }
        }
    }

    fun applyDxfPathsAndCompile() {
        if (dxfRawPaths.isEmpty()) return
        isProcessingDxf = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val unitMultiplier = if (dxfUnitIsInch) 25.4f else 1.0f
                val totalScale = dxfScaleFactor * unitMultiplier
                
                val scaledPaths = dxfRawPaths.map { contour ->
                    contour.map { pt ->
                        Offset(pt.x * totalScale, pt.y * totalScale)
                    }
                }

                val centered = centerPathsInWorkspace(scaledPaths)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    activePaths = centered
                    compileCurrentPaths()
                    isProcessingDxf = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    dxfProcessError = e.localizedMessage ?: "Scaling or compiling failed"
                    isProcessingDxf = false
                }
            }
        }
    }
}

