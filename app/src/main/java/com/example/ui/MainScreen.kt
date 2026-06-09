package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.PathEffect
import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Project
import com.example.ui.theme.*
import com.example.ui.viewmodel.GCodeViewModel
import com.example.utils.GCodeParser
import com.example.utils.ImageToGCodeConverter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.Locale

// =========================================================================
// RESPONSIVE FLEXBOX & ADAPTIVE COMPANION (MEDIA-QUERY BEHAVIOR LIKE CSS FLEXBOX)
// =========================================================================
@Composable
fun ResponsiveFlexLayout(
    modifier: Modifier = Modifier,
    breakpointDp: androidx.compose.ui.unit.Dp = 600.dp,
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ResponsiveFlexScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth < breakpointDp
        if (isCompact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                ResponsiveFlexScope(isCompact = true, columnScope = this).content()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                ResponsiveFlexScope(isCompact = false, rowScope = this).content()
            }
        }
    }
}

class ResponsiveFlexScope(
    val isCompact: Boolean,
    val rowScope: RowScope? = null,
    val columnScope: ColumnScope? = null
) {
    @Composable
    fun FlexItem(
        modifier: Modifier = Modifier,
        compactWeight: Float = 1f,
        expandedWeight: Float = 1f,
        compactHeight: androidx.compose.ui.unit.Dp? = null,
        expandedHeight: androidx.compose.ui.unit.Dp? = null,
        content: @Composable () -> Unit
    ) {
        val calculatedModifier = if (isCompact) {
            var m = modifier.fillMaxWidth()
            if (compactHeight != null) m = m.height(compactHeight)
            m
        } else {
            val baseMod = rowScope?.let {
                with(it) { modifier.weight(expandedWeight) }
            } ?: modifier
            var m = baseMod
            if (expandedHeight != null) m = m.height(expandedHeight)
            m
        }
        Box(modifier = calculatedModifier) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: GCodeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf("ASPIRE") } // ASPIRE, TEXT, SKETCH, AI_STUDIO, PREVIEW, FILES, SETTINGS
    
    // Multi-Language Strings dictionary
    val lang = viewModel.language
    val tAppTitle = if (lang == "id") "FluidNC G-code Studio" else "FluidNC G-Code Studio"
    val tAspireTab = if (lang == "id") "Vectric Aspire" else "Vectric Aspire"
    val tTextTab = if (lang == "id") "Teks" else "Text"
    val tSketchTab = if (lang == "id") "Sketsa/Gambar" else "Sketch/Image"
    val tAiTab = if (lang == "id") "Galeri Preset" else "Preset Gallery"
    val tPreviewTab = if (lang == "id") "Pratinjau" else "Live Preview"
    val tFilesTab = if (lang == "id") "File & Projek" else "Projects"
    val tSettingsTab = if (lang == "id") "Setelan" else "Settings"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SlateDark,
        topBar = {
            // Tabs Navigation Row (Outlined Material 3 style row bottom-border with high contrast)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(0.5.dp, BorderCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern compact toggle chip for Beginner/Pro
                    Button(
                        onClick = { viewModel.isBeginnerMode = !viewModel.isBeginnerMode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.isBeginnerMode) SpindleGold else SlateDark,
                            contentColor = if (viewModel.isBeginnerMode) Color.Black else UnselectedGrey
                        ),
                        border = BorderStroke(1.dp, if (viewModel.isBeginnerMode) SpindleGold else BorderCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = if (viewModel.isBeginnerMode) Icons.Default.Info else Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (viewModel.isBeginnerMode) {
                                if (lang == "id") "MODE: PEMULA 🎓" else "MODE: BEGINNER 🎓"
                            } else {
                                if (lang == "id") "MODE: PRO ⚡" else "MODE: PRO ⚡"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val tabs = listOf(
                            Triple("ASPIRE", tAspireTab, Icons.Default.Create),
                            Triple("TEXT", tTextTab, Icons.Default.Edit),
                            Triple("SKETCH", tSketchTab, Icons.Default.Share),
                            Triple("AI_STUDIO", tAiTab, Icons.Default.Star),
                            Triple("CONVERTER", if (lang == "id") "Konverter CNC" else "CNC Converter", Icons.Default.Refresh),
                            Triple("DXF", if (lang == "id") "Konverter DXF" else "DXF Plotter", Icons.Default.Build),
                            Triple("PREVIEW", tPreviewTab, Icons.Default.PlayArrow),
                            Triple("FLUIDNC", if (lang == "id") "FluidNC Wi-Fi" else "FluidNC Control", Icons.Default.Menu),
                            Triple("FILES", tFilesTab, Icons.Default.List),
                            Triple("SETTINGS", tSettingsTab, Icons.Default.Settings)
                        )

                        tabs.forEach { (key, label, icon) ->
                            val isSelected = activeTab == key
                            Button(
                                onClick = { activeTab = key },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) PrimaryCyan else Color.Transparent,
                                    contentColor = if (isSelected) SlateDark else UnselectedGrey
                                ),
                                border = if (isSelected) null else BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("tab_$key")
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
    ) { paddingValues ->
        // Render current active tab pane with sleek transition animations
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SlateDark)
        ) {
            if (viewModel.isBeginnerMode) {
                BeginnerGuideBanner(activeTab = activeTab, lang = lang)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    "ASPIRE" -> AspireCADCAMPane(viewModel)
                    "TEXT" -> TextCreatorPane(viewModel)
                    "SKETCH" -> SketchAndImagePane(viewModel)
                    "AI_STUDIO" -> PatternPresetPane(viewModel)
                    "CONVERTER" -> CNCConverterPane(viewModel)
                    "DXF" -> DXFConverterPane(viewModel)
                    "PREVIEW" -> LivePreviewPane(viewModel)
                    "FLUIDNC" -> FluidNCControlPane(viewModel)
                    "FILES" -> ProjectsPane(viewModel)
                    "SETTINGS" -> SettingsPane(viewModel)
                }
            }
        }
    }
}

// ==========================================
// SMART INTERACTIVE BEGINNER GUIDE TUTORIAL
// ==========================================
@Composable
fun BeginnerGuideBanner(activeTab: String, lang: String) {
    var isExpanded by remember { mutableStateOf(false) } // Collapsed by default to avoid taking too much height, customizable by tap!
    
    val title = when (activeTab) {
        "TEXT" -> if (lang == "id") "✏️ Pembuat Ukiran Teks" else "✏️ Text Engraver Wizard"
        "SKETCH" -> if (lang == "id") "🎨 Sketsa Tangan Bebas" else "🎨 Freehand Drawing Sketcher"
        "AI_STUDIO" -> if (lang == "id") "🌟 Galeri Pola Instan (Preset)" else "🌟 Instant Design Catalog"
        "CONVERTER" -> if (lang == "id") "📷 Konverter Foto ke CNC" else "📷 Photo to Engraving Trace"
        "DXF" -> if (lang == "id") "📐 Pembaca File Desain DXF" else "📐 DXF CAD Vector Import"
        "PREVIEW" -> if (lang == "id") "👁️ Simulator & Cek Lintasan" else "👁️ Dry-run Visual Simulator"
        "FLUIDNC" -> if (lang == "id") "🎮 Remote & Sensor Z-Probe" else "🎮 WiFi Controller & Z-Probe"
        "FILES" -> if (lang == "id") "💾 Pustaka File G-Code Anda" else "💾 Saved Projects Library"
        "SETTINGS" -> if (lang == "id") "⚙️ Setelan Aman Mesin Anda" else "⚙️ Core Machine Governors"
        else -> ""
    }

    val steps = when (activeTab) {
        "TEXT" -> listOf(
            if (lang == "id") "1. Tulis kata/nama" else "1. Write any text",
            if (lang == "id") "2. Atur tinggi & spasi" else "2. Adjust height / width",
            if (lang == "id") "3. Klik HASILKAN TEKS" else "3. Click GENERATE G-CODE"
        )
        "SKETCH" -> listOf(
            if (lang == "id") "1. Usap jari di kanvas" else "1. Drag finger on canvas",
            if (lang == "id") "2. Gunakan Hapus/Reset" else "2. Change brush / reset",
            if (lang == "id") "3. Klik GENERATE CODE" else "3. Convert into toolpaths"
        )
        "AI_STUDIO" -> listOf(
            if (lang == "id") "1. Pilih pola geometri" else "1. Pick a geometric style",
            if (lang == "id") "2. Geser ukuran diameter" else "2. Adjust target diameter",
            if (lang == "id") "3. Klik GENERATE POLA" else "3. Click BUILD PATTERN"
        )
        "CONVERTER" -> listOf(
            if (lang == "id") "1. Upload foto JPG/PNG" else "1. Pick image file",
            if (lang == "id") "2. Sesuaikan Threshold" else "2. Dial threshold / dither",
            if (lang == "id") "3. Klik GENERATE RASTER" else "3. Process image to tool"
        )
        "DXF" -> listOf(
            if (lang == "id") "1. Upload file CAD .dxf" else "1. Select .dxf vector file",
            if (lang == "id") "2. Cek garis merah/putih" else "2. View line contours",
            if (lang == "id") "3. Klik EKSPOR G-code" else "3. Convert into coordinates"
        )
        "PREVIEW" -> listOf(
            if (lang == "id") "1. Geser kanvas 3D" else "1. Swipe to rotate 3D view",
            if (lang == "id") "2. Perhatikan rute pisau" else "2. Trace toolpath limits",
            if (lang == "id") "3. Titik ⊕ Kuning = Mulai" else "3. Yellow cross indicates play"
        )
        "FLUIDNC" -> listOf(
            if (lang == "id") "1. Colok wifi CNC/Laser" else "1. Connect phone to CNC AP",
            if (lang == "id") "2. Klik SAMBUNG di sini" else "2. Click Connect -> Green light",
            if (lang == "id") "3. Tekan manual/Probe" else "3. Press D-Pad jog / Z-Probe"
        )
        "FILES" -> listOf(
            if (lang == "id") "1. Lihat projek tersimpan" else "1. View stored G-Code jobs",
            if (lang == "id") "2. Bagikan / Edit file" else "2. Click edit / share / save",
            if (lang == "id") "3. Kirim Wifi ke FluidNC" else "3. Cast directly to CNC board"
        )
        "SETTINGS" -> listOf(
            if (lang == "id") "1. Feedrate = Sinyal speed" else "1. Feedrate refers to speed",
            if (lang == "id") "2. Safe Z = Tinggi terbang" else "2. Safe Z = clearance height",
            if (lang == "id") "3. Gunakan database kayu" else "3. Load verified factory preset"
        )
        else -> emptyList()
    }

    val desc = when (activeTab) {
        "TEXT" -> if (lang == "id") {
            "Selamat datang! Fitur ini mengubah kata-kata/tulisan biasa Anda menjadi ukiran CNC indah secara otomatis. Anda tidak perlu repot menggambar rute pahat, asisten langsung menerjemahkan baris tulisan Anda."
        } else {
            "Welcome! This converts your keyboard text into real physical engraving routes automatically. Simple, clean, and customized in physical mm dimensions."
        }
        "SKETCH" -> if (lang == "id") {
            "Ekspresikan karya seni Anda! Gambar bebas apa saja langsung menggunakan jari tangan di layar, asisten pintar akan merekam jejak jari Anda lalu mengubahnya menjadi baris rute potong CNC/Laser."
        } else {
            "Unleash your creativity! Freehand draw using your finger; the smart processor digitizes your exact drawings into coordinates automatically."
        }
        "AI_STUDIO" -> if (lang == "id") {
            "Butuh cepat? Pilih berbagai pola jadi (tatakan gelas, roda gigi, asbak, bunga hiasan mandala). Anda tinggal menggeser diameter benda kerja di layar tanpa perlu mendesain dari awal."
        } else {
            "Need presets? Select beautiful geometric shapes like coasters, mechanical gearboxes, boxes, or elegant stars. Set sizes dynamically without external CAD."
        }
        "CONVERTER" -> if (lang == "id") {
            "Mengubah potret foto wajah, logo, atau draf gambar biasa menjadi ribuan titik-titik ukiran laser (raster). Sangat cocok untuk kado ukiran kayu gantungan kunci."
        } else {
            "Convert JPG/PNG pictures or camera shots into beautiful dithered engraving lines. Perfect for wooden laser portrait frame gifts!"
        }
        "DXF" -> if (lang == "id") {
            "Membaca file format industri .dxf dari AutoCAD/CorelDraw. Membantu Anda yang ingin langsung mengeksekusi gambar CAD profesional tanpa perlu laptop di lapangan."
        } else {
            "Import clean vector lines using .dxf standard. Load precise engineering drawings exported from CAD and send to carving instantly."
        }
        "PREVIEW" -> if (lang == "id") {
            "Jangan langsung mencolok mesin! Simulasikan gerakan kering rute di layar ini secara 3D interaktif. Anda bisa mencubit layar untuk Zoom atau menggesernya guna melihat rute potong Z."
        } else {
            "Play it safe! Try a virtual dry-run first in the 3D-Isometric viewer. Drag to pan and tilt depth views to make sure nothing crashes."
        }
        "FLUIDNC" -> if (lang == "id") {
            "Pusat pengendali wireless mesin! Geser sumbu X/Y ke titik awal kayu Anda, lalu klik 'Z-PROBE' agar sensor menyentuh logam untuk mendeteksi tinggi nol pisau se-presisi 0.01mm."
        } else {
            "Wireless dashboard center. Move coordinates, check connection logs, and run automated Z-Axis calibration probe safely."
        }
        "FILES" -> if (lang == "id") {
            "gudang penyimpanan file CNC Anda. Anda bisa membagikan desain ke teman, mengunggah rute, menyunting isinya secara langsung, atau menyimpannya kembali."
        } else {
            "Your workspace catalog. Hold list items to export, share, edit text blocks or manage your past cutting achievements."
        }
        "SETTINGS" -> if (lang == "id") {
            "Kunci keselamatan mesin Anda! Gunakan database material rekomendasi di bawah untuk mengisi kecepatan secara otomatis agar pisau tidak tumpul/mesin tidak slip."
        } else {
            "The central protection system of your machine. Use the recommended material presets to load safe speeds automatically."
        }
        else -> ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { isExpanded = !isExpanded } // Toggle on card body click too! Easy control
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        title,
                        color = PrimaryCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        if (isExpanded) "" else (if (lang == "id") "(Sentuh untuk bantuan)" else "(Tap for help)"),
                        color = UnselectedGrey,
                        fontSize = 8.5.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Guide",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    desc,
                    color = TextLight.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    lineHeight = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Horizontal step cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    steps.forEach { stepText ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, PrimaryCyan.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    stepText,
                                    color = TextLight,
                                    fontSize = 8.5.sp,
                                    lineHeight = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PANE 1: TEXT CREATOR PANE
// ==========================================
@Composable
fun TextCreatorPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val scope = rememberCoroutineScope()
    val tPrompt = if (lang == "id") "Masukkan Teks Desain Anda" else "Enter Your Design Text"
    val tPlaceholder = if (lang == "id") "Tulis teks di sini (misal: CNC)" else "Write text here (e.g., CNC)"
    val tHeight = if (lang == "id") "Tinggi Huruf (mm): ${viewModel.creatorTextSize.toInt()} mm" else "Font Height (mm): ${viewModel.creatorTextSize.toInt()} mm"
    val tFont = if (lang == "id") "Pilih Gaya Font" else "Select Font Family"
    val tSpacing = if (lang == "id") "Penyesuaian Jarak Karakter" else "Letter Spacing Adjustment"
    val tGenerate = if (lang == "id") "HASILKAN G-CODE TEKS" else "GENERATE TEXT G-CODE"
    val tGeneratedOk = if (lang == "id") "G-code teks berhasil dibuat! Silakan buka tab Pratinjau." else "Text G-code created successfully! Please open Preview tab."
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Design Title Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, Color(0x1A00E5FF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    tPrompt,
                    color = PrimaryCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = viewModel.creatorText,
                    onValueChange = { viewModel.creatorText = it },
                    placeholder = { Text(tPlaceholder, color = UnselectedGrey) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = UnselectedGrey,
                        cursorColor = PrimaryCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_input_field"),
                    maxLines = 2
                )
            }
        }

        // Sliders & Customization Options
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Size slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tHeight, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${viewModel.creatorTextSize.toInt()} mm",
                            color = PrimaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = viewModel.creatorTextSize,
                        onValueChange = { viewModel.creatorTextSize = it },
                        valueRange = 5f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryCyan,
                            activeTrackColor = PrimaryCyan,
                            inactiveTrackColor = TravelGray
                        ),
                        modifier = Modifier.testTag("text_size_slider")
                    )
                }

                // Spacing slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tSpacing, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            String.format("%.1fx", viewModel.letterSpacing),
                            color = PrimaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = viewModel.letterSpacing,
                        onValueChange = { viewModel.letterSpacing = it },
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryCyan,
                            activeTrackColor = PrimaryCyan,
                            inactiveTrackColor = TravelGray
                        ),
                        modifier = Modifier.testTag("letter_spacing_slider")
                    )
                }
            }
        }

        // Font selection
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    tFont,
                    color = TextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                val fonts = listOf(
                    Pair("SANS_SERIF", "Gothic Sans Line"),
                    Pair("SERIF", "Industrial Serif Line"),
                    Pair("MONOSPACE", "Modern Engrave Monospace")
                )

                fonts.forEach { (fontKey, fontName) ->
                    val isSelected = viewModel.selectedFont == fontKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectedFont = fontKey }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.selectedFont = fontKey },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            fontName,
                            color = if (isSelected) PrimaryCyan else TextLight,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = when (fontKey) {
                                "SANS_SERIF" -> FontFamily.SansSerif
                                "SERIF" -> FontFamily.Serif
                                "MONOSPACE" -> FontFamily.Monospace
                                else -> FontFamily.Default
                            }
                        )
                    }
                }

                // Show custom fonts if any are imported
                if (viewModel.importedFontsList.isNotEmpty()) {
                    Divider(color = Color(0xFF49454F), modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        if (lang == "id") "Font Kustom yang Diimpor:" else "Imported Custom Fonts:",
                        color = PrimaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    viewModel.importedFontsList.forEach { (dispName, filePath) ->
                        val isSelected = viewModel.selectedFont == filePath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.selectedFont = filePath }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedFont = filePath },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryCyan)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    dispName,
                                    color = if (isSelected) PrimaryCyan else TextLight,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Default
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteFont(filePath) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus Font",
                                    tint = LaserCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Font file copier launcher
                val fontPickLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val resolver = context.contentResolver
                                var fileName = "user_font.ttf"
                                val cursor = resolver.query(uri, null, null, null, null)
                                cursor?.use { c ->
                                    if (c.moveToFirst()) {
                                        val nameIdx = c.getColumnIndex("display_name")
                                        if (nameIdx != -1) {
                                            fileName = c.getString(nameIdx)
                                        }
                                    }
                                }
                                val isStream = resolver.openInputStream(uri)
                                if (isStream != null) {
                                    viewModel.importFont(fileName, isStream)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, if (lang == "id") "Font berhasil diimpor!" else "Font imported successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Could not read font file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, "Error importing: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { fontPickLauncher.launch("*/*") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                    border = BorderStroke(1.dp, PrimaryCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Font", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (lang == "id") "Impor Font Kustom (.ttf/.otf)" else "Import Font (.ttf/.otf)", fontSize = 12.sp)
                }
            }
        }

        // Action Trigger Button
        Button(
            onClick = {
                viewModel.generateTextToolpath()
                Toast.makeText(context, tGeneratedOk, Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_text_gcode_button")
        ) {
            Icon(Icons.Default.Build, contentDescription = tGenerate)
            Spacer(modifier = Modifier.width(8.dp))
            Text(tGenerate, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ==========================================
// PANE 2: SKETCH & IMAGE PROCESSING PANE
// ==========================================
@Composable
fun SketchAndImagePane(viewModel: GCodeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = viewModel.language
    val tSketchHeader = if (lang == "id") "A. Kanvas Sketsa Manual" else "A. Freehand CAD Sketching Area"
    val tSketchSub = if (lang == "id") "Gambarkan pola garis Anda langsung dengan sentuhan:" else "Draw custom toolpaths directly using touch events:"
    val tBtnClear = if (lang == "id") "Hapus Sketsa" else "Clear Sketch"
    val tBtnGencodeSketch = if (lang == "id") "KOMPILASI SKETSA KE G-CODE" else "COMPILE SKETCH TO G-CODE"
    val tImageHeader = if (lang == "id") "B. Unggah & Konversi Gambar" else "B. Upload & Extract From Image"
    val tChooseFile = if (lang == "id") "PILIH GAMBAR DARI PUSTAKA" else "SELECT IMAGE FROM GALLERY"
    val tImgMode = if (lang == "id") "Mode Pemrosesan Gambar" else "Image Grayscale Engraving Type"
    val tInvert = if (lang == "id") "Inversi Warna Gelap/Terang" else "Invert Brightness Canvas"
    val tResValue = if (lang == "id") "Resolusi Grafis: ${String.format("%.1f", viewModel.imageResolution)} dpi" else "Engrave density: ${String.format("%.1f", viewModel.imageResolution)} dpi"
    val tContrastThreshold = if (lang == "id") "Ambang Contrast (Threshold): ${String.format("%.2f", viewModel.imageThreshold)}" else "B&O Contrast Threshold: ${String.format("%.2f", viewModel.imageThreshold)}"
    val tGenerateImg = if (lang == "id") "PROSES GAMBAR KE G-CODE" else "EXTRACT G-CODE FROM IMAGE"
    val tOkCompiled = if (lang == "id") "G-code gambar berhasil dibuat! Silakan buka tab Pratinjau." else "Image G-code successfully created! View in Preview tab."

    // Multi-touch sketch drawing local coordinates container
    val drawPoints = remember { mutableStateListOf<List<Offset>>() }
    var currentDrawLine = remember { mutableStateListOf<Offset>() }

    // Setup photo/gallery picker with IO context & automatic memory-safe scaling
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Query dimensions first to avoid loading massive camera files directly into memory
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }

                    val reqHeight = 1200
                    val reqWidth = 1200
                    var inSampleSize = 1
                    val height = options.outHeight
                    val width = options.outWidth
                    if (height > reqHeight || width > reqWidth) {
                        val halfHeight = height / 2
                        val halfWidth = width / 2
                        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        this.inSampleSize = inSampleSize
                    }

                    val finalBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (finalBmp != null) {
                            viewModel.importedBitmap = finalBmp
                            Toast.makeText(
                                context,
                                if (lang == "id") "Gambar berhasil dimuat!" else "Image loaded successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(context, "Error: Could not decode image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Error decoding image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ResponsiveFlexLayout(spacing = 16.dp) {
            FlexItem(expandedWeight = 1f) {
                // --- 1. FREEHAND SKETCH CAD AREA ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            tSketchHeader,
                            color = PrimaryCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(tSketchSub, color = TextLight, fontSize = 12.sp)

                        // Interactive Drawing Canvas Frame
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(SlateDark)
                                .border(BorderStroke(1.dp, BorderCyan), RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentDrawLine.clear()
                                            currentDrawLine.add(offset)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            // Scale coordinates to fit visual box boundaries
                                            currentDrawLine.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (currentDrawLine.isNotEmpty()) {
                                                drawPoints.add(currentDrawLine.toList())
                                                currentDrawLine.clear()
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Draw lines onto the actual canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Previous completed paths
                                for (line in drawPoints) {
                                    if (line.size > 1) {
                                        val p = Path()
                                        p.moveTo(line[0].x, line[0].y)
                                        for (i in 1 until line.size) {
                                            p.lineTo(line[i].x, line[i].y)
                                        }
                                        drawPath(
                                            path = p,
                                            color = PrimaryCyan,
                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                // Current active path drawing
                                if (currentDrawLine.size > 1) {
                                    val p = Path()
                                    p.moveTo(currentDrawLine[0].x, currentDrawLine[0].y)
                                    for (i in 1 until currentDrawLine.size) {
                                        p.lineTo(currentDrawLine[i].x, currentDrawLine[i].y)
                                    }
                                    drawPath(
                                        path = p,
                                        color = PrimaryCyan,
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }

                            // Display hint if empty
                            if (drawPoints.isEmpty() && currentDrawLine.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (lang == "id") "[ Gambarkan Sesuatu Di Sini ]" else "[ Draw Something Here ]",
                                        color = UnselectedGrey,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Row of Sketch tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { drawPoints.clear(); currentDrawLine.clear() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LaserCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tBtnClear, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (drawPoints.isNotEmpty()) {
                                        // Translate canvas points to mm workspace units
                                        val designWidthPx = 300f // approx imaginary pixel size
                                        val designHeightPx = 180f
                                        val scaleX = viewModel.workspaceWidth / designWidthPx
                                        val scaleY = viewModel.workspaceHeight / designHeightPx

                                        val convertedPaths = drawPoints.map { path ->
                                            path.map { pt ->
                                                Offset(pt.x * scaleX, (designHeightPx - pt.y) * scaleY) // Flip Y to cartesian
                                            }
                                        }
                                        viewModel.activePaths = convertedPaths
                                        viewModel.compileCurrentPaths()
                                        Toast.makeText(context, tOkCompiled, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, if (lang == "id") "Silakan gambar terlebih dahulu!" else "Please draw something first!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(tBtnGencodeSketch, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            FlexItem(expandedWeight = 1f) {
                // --- 2. IMAGE UPLOAD & PROCESSING CONTROL ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            tImageHeader,
                            color = PrimaryCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Select image button
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = PrimaryCyan),
                            border = BorderStroke(1.dp, PrimaryCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tChooseFile, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Render thumbnail of uploaded image
                        viewModel.importedBitmap?.let { bmp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateDark)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Imported Image Thumbnail",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, PrimaryCyan)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        if (lang == "id") "Status: Gambar Dimuat" else "Status: Image Ready",
                                        color = SpindleGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${bmp.width} x ${bmp.height} px",
                                        color = UnselectedGrey,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Divider(color = TravelGray, thickness = 0.5.dp)

                        // Process mode selector Radio buttons
                        Column {
                            Text(
                                tImgMode,
                                color = TextLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val modes = listOf(
                                Triple(ImageToGCodeConverter.ProcessMode.OUTLINE, "Outline Deteksi Tepi (Vektor)", "Cuts edges only"),
                                Triple(ImageToGCodeConverter.ProcessMode.RASTER, "Sapu Raster S-Power (Laser)", "Grayscale high-speed scan"),
                                Triple(ImageToGCodeConverter.ProcessMode.DITHER, "Dithered Titik Dot (Ketukan)", "Dotted engraving pattern")
                            )

                            modes.forEach { (mode, title, desc) ->
                                val isSelected = viewModel.imageProcessMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.imageProcessMode = mode }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.imageProcessMode = mode },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryCyan)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            title,
                                            color = if (isSelected) PrimaryCyan else TextLight,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(desc, color = UnselectedGrey, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Invert Colors Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tInvert, color = TextLight, fontSize = 13.sp)
                            Switch(
                                checked = viewModel.imageInvertColors,
                                onCheckedChange = { viewModel.imageInvertColors = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                            )
                        }

                        // Image processing variables sliders
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tResValue, color = TextLight, fontSize = 13.sp)
                                Text(
                                    "${String.format("%.1f", viewModel.imageResolution)} lines/mm",
                                    color = PrimaryCyan,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Slider(
                                value = viewModel.imageResolution,
                                onValueChange = { viewModel.imageResolution = it },
                                valueRange = 0.5f..5.0f,
                                colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                            )
                        }

                        if (viewModel.imageProcessMode != ImageToGCodeConverter.ProcessMode.RASTER) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(tContrastThreshold, color = TextLight, fontSize = 13.sp)
                                    Text(
                                        String.format("%.2f", viewModel.imageThreshold),
                                        color = PrimaryCyan,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Slider(
                                    value = viewModel.imageThreshold,
                                    onValueChange = { viewModel.imageThreshold = it },
                                    valueRange = 0.1f..0.9f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                                )
                            }
                        }

                        if (viewModel.imageProcessError != null) {
                            Text(
                                text = "Error: ${viewModel.imageProcessError}",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // Process Action Button
                        Button(
                            onClick = {
                                if (viewModel.importedBitmap != null) {
                                    viewModel.generateImageToolpath()
                                } else {
                                    Toast.makeText(context, if (lang == "id") "Silakan pilih alternatif gambar terlebih dahulu!" else "Please upload/select an image first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !viewModel.isProcessingImage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryCyan,
                                contentColor = SlateDark,
                                disabledContainerColor = PrimaryCyan.copy(alpha = 0.5f),
                                disabledContentColor = SlateDark.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (viewModel.isProcessingImage) {
                                CircularProgressIndicator(
                                    color = SlateDark,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (lang == "id") "MENERJEMAHKAN... (Sabar ya)" else "EXTRACTING... (Please wait)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tGenerateImg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PANE 3: PATTERN PRESETS CAD PANEL
// ==========================================
@Composable
fun PatternPresetPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current
    var successPresetLoaded by remember { mutableStateOf<String?>(null) }
    
    // Sliders for preset personalization
    var patternScale by remember { mutableFloatStateOf(0.85f) }
    var outerRingByBoundary by remember { mutableStateOf(true) }

    val tTitle = if (lang == "id") "Galeri Preset Pola CAD" else "CAD Pattern Preset Gallery"
    val tSub = if (lang == "id") 
        "Pilih salah satu ornamen geometris presisi tinggi berikut untuk dihasilkan secara instan 100% offline tanpa menggunakan koneksi internet." 
        else "Select any high-precision geometric ornament below to generate immediately 100% offline without needing an active internet connection."
    
    val tSuccessMsg = if (lang == "id") 
        "Pola $successPresetLoaded berhasil dimuat! Buka tab Pratinjau untuk melihat lintasan potong." 
        else "Pattern $successPresetLoaded loaded successfully! Go to Live Preview tab to inspect toolpaths."

    // Define 8 beautiful mathematical designs
    data class PresetItem(
        val key: String,
        val nameEn: String,
        val nameId: String,
        val descEn: String,
        val descId: String,
        val nodesCount: String,
        val estCuts: String,
        val icon: ImageVector
    )

    val presetsList = listOf(
        PresetItem(
            key = "star",
            nameId = "Mandala Bintang Keemasan",
            nameEn = "Golden Mandala Star",
            descId = "Pola bintang 8 sudut tumpuk dikelilingi ornamen cincin luar ganda.",
            descEn = "Layered 8-point geometric stars nested within dual circular boundaries.",
            nodesCount = "4 Paths",
            estCuts = "32 Nodes",
            icon = Icons.Default.Star
        ),
        PresetItem(
            key = "flower",
            nameId = "Geometri Bunga Mekar",
            nameEn = "Blossom Geometry",
            descId = "Kelopak bunga melingkar simetris presisi tinggi dengan 10 sirip fusi.",
            descEn = "Aesthetic 10-petal floral loops calculated using trigonometric arcs.",
            nodesCount = "4 Paths",
            estCuts = "64 Nodes",
            icon = Icons.Default.Favorite
        ),
        PresetItem(
            key = "circle",
            nameId = "Cincin Planet Konsentris",
            nameEn = "Concentric Planetary Rings",
            descId = "Garis lintasan 5 lingkaran konsentris presisi tinggi untuk kalibrasi klem.",
            descEn = "5 High-contrast concentrically nested loops for flat plane alignment.",
            nodesCount = "5 Paths",
            estCuts = "120 Nodes",
            icon = Icons.Default.Refresh
        ),
        PresetItem(
            key = "polygon",
            nameId = "Geometri Sacre Hexagonal",
            nameEn = "Sacred Hexagonal Geometry",
            descId = "Rasio emas sarang lebah modular bersarang dengan inti ornamen bundar.",
            descEn = "Sacred nesting hexagon loops centering a circular concentric node.",
            nodesCount = "5 Paths",
            estCuts = "48 Nodes",
            icon = Icons.Default.Menu
        ),
        PresetItem(
            key = "spiral",
            nameId = "Sarang Spiral Rasio Emas",
            nameEn = "Golden Ratio Spiral Grid",
            descId = "Gulungan spiral ganda berlawanan arah menciptakan matriks jaring kosmik.",
            descEn = "Twin spirals curved in opposite directions, forming a vector grid.",
            nodesCount = "3 Paths",
            estCuts = "150 Nodes",
            icon = Icons.Default.PlayArrow
        ),
        PresetItem(
            key = "gear",
            nameId = "Roda Gigi Mekanik CNC",
            nameEn = "CNC Mechanical Gear",
            descId = "Ornamen roda gigi pitch transmisi fusi lengkap dengan poros tengah fiting.",
            descEn = "True profile clock gear detailing with center axle clearance.",
            nodesCount = "4 Paths",
            estCuts = "72 Nodes",
            icon = Icons.Default.Settings
        ),
        PresetItem(
            key = "box",
            nameId = "Bingkai Kotak Simetris",
            nameEn = "Symmetrical Frame Box",
            descId = "Dua bingkai kotak dalam mengurung ornamen bintang 4-sudut retro.",
            descEn = "Double nesting rectangular profile with inner 4-point design points.",
            nodesCount = "4 Paths",
            estCuts = "24 Nodes",
            icon = Icons.Default.Add
        ),
        PresetItem(
            key = "crest",
            nameId = "Plakat Estetika Art-AI",
            nameEn = "Art-AI Premium Crest",
            descId = "Perisai tameng penghargaan modern didekorasi bunga geometri pusat.",
            descEn = "Premium corporate shield emblem detailing mixed decorative stars.",
            nodesCount = "5 Paths",
            estCuts = "90 Nodes",
            icon = Icons.Default.Build
        )
    )

    // Reset indicator after few seconds
    LaunchedEffect(successPresetLoaded) {
        if (successPresetLoaded != null) {
            delay(4000)
            successPresetLoaded = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Introduction Title
        Column {
            Text(
                tTitle,
                color = PrimaryCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(tSub, color = TextLight, fontSize = 11.sp)
        }

        // Connection Banner: 100% Offline Active Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RouterGreen.copy(alpha = 0.12f))
                .border(BorderStroke(1.dp, RouterGreen.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(RouterGreen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    if (lang == "id") "Sistem Pola Offline Aktif" else "Offline Design Mode Engaged",
                    color = RouterGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (lang == "id") "Sistem menghitung bentuk secara lokal. Tidak memerlukan akun atau kuota!" 
                    else "Coordinates computed 100% locally. Zero cloud latency or quotas!",
                    color = TextLight.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
        }

        // Success Toast Notification Sheet inside pane
        AnimatedVisibility(
            visible = successPresetLoaded != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryCyan.copy(alpha = 0.08f))
                    .border(BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    tSuccessMsg,
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Custom Scaling & Adjustment Controls Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (lang == "id") "Konfigurasi Personal Pola" else "Live Pattern Personalization",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                // Sliders for Custom Scaling Factor
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (lang == "id") "Skala Ukuran Desain" else "Design Pattern Scale", 
                            color = TextLight, 
                            fontSize = 12.sp
                        )
                        Text(
                            "${(patternScale * 100).toInt()}%", 
                            color = PrimaryCyan, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = patternScale,
                        onValueChange = { patternScale = it },
                        valueRange = 0.3f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                    )
                }

                // Add or omit Outer Circular Frame Bound
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Sertakan Cincin Pengaman Luar" else "Include Circular Outer Hoop", 
                            color = TextLight, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (lang == "id") "Mempermudah pemotongan batas lingkaran" else "Adds outer contour circle for holding workpiece", 
                            color = UnselectedGrey, 
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = outerRingByBoundary,
                        onCheckedChange = { outerRingByBoundary = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }
            }
        }

        // Grid List of Preset Patterns
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (lang == "id") "Pilih Desain Pola Geometrik" else "Choose Geometric Pattern Core",
                color = TextLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            presetsList.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { item ->
                        val itemTitle = if (lang == "id") item.nameId else item.nameEn
                        val itemDesc = if (lang == "id") item.descId else item.descEn

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            border = BorderStroke(1.dp, BorderCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(210.dp)
                                .clickable {
                                    val res = com.example.service.GeminiService.generateOfflineDesign(
                                        prompt = item.key,
                                        widthLimit = viewModel.workspaceWidth,
                                        heightLimit = viewModel.workspaceHeight,
                                        languageCode = lang
                                    )
                                    val shapes = res.second
                                    if (shapes.isNotEmpty()) {
                                        var paths = shapes.map { shape -> shape.toPoints() }
                                        
                                        // Apply scaling filter from sliders
                                        if (patternScale < 1f) {
                                            val cx = viewModel.workspaceWidth / 2f
                                            val cy = viewModel.workspaceHeight / 2f
                                            paths = paths.map { contour ->
                                                contour.map { pt ->
                                                    Offset(
                                                        x = cx + (pt.x - cx) * patternScale,
                                                        y = cy + (pt.y - cy) * patternScale
                                                    )
                                                }
                                            }
                                        }

                                        // Apply or remove outer ring
                                        val finalPaths = if (!outerRingByBoundary) {
                                            if (paths.size > 1) paths.drop(1) else paths
                                        } else {
                                            paths
                                        }

                                        viewModel.activePaths = viewModel.centerPathsInWorkspace(finalPaths)
                                        viewModel.compileCurrentPaths()
                                        successPresetLoaded = itemTitle
                                        Toast.makeText(context, if (lang == "id") "Berhasil memuat $itemTitle!" else "Loaded $itemTitle successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Header Icon badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(PrimaryCyan.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = PrimaryCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .background(BorderCyan, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(item.nodesCount, color = TextLight, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        itemTitle,
                                        color = TextLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        itemDesc,
                                        color = UnselectedGrey,
                                        fontSize = 9.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 11.sp
                                    )
                                }

                                // Interactive Click CTA button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PrimaryCyan)
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Build, 
                                        contentDescription = null, 
                                        tint = SlateDark, 
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (lang == "id") "MUAT PRESET" else "LOAD PATTERN",
                                        color = SlateDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        }
}

// ==========================================
// PANE 3.5: MACH3 & CNC MULTI-FORMAT NC CONVERTER
// ==========================================
@Composable
fun CNCConverterPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveProjectName by remember { mutableStateOf("Converted Mach3 NC") }

    val tTitle = if (lang == "id") "Konverter Multi-Format CNC ke NC" else "Mach3 & CNC Multi-Format Transpiler"
    val tDesc = if (lang == "id") 
        "Ubah file G-code legacy (Mach3, TAP, CNC, TXT) ke format NC standar yang dioptimalkan untuk Grbl / FluidNC. Lakukan penskalaan koordinat inci-ke-metrik secara otomatis."
        else "Translate legacy CNC files (.tap, .cnc, .gcode, .txt) into clean Grbl/FluidNC standard-compliant .nc codes. Perform Imperial-to-Metric (G20 to G21) scaling on-the-fly."

    val sampleMach3Code = """(Mach3 CNC sample - Engraving design)
N10 G90 G20 G80 G49 (Absolute, Inches)
N20 M06 T2 (Tool change)
N30 G43 H2 (Tool length compensation)
N40 G00 X1.0 Y1.0 Z0.2 F50
N50 G01 Z-0.1 F10
N60 G01 X2.0 Y1.0 Z-0.1 F15
N70 G01 X2.0 Y2.0 Z-0.1 F15
N80 G01 X1.0 Y2.0 Z-0.1
N90 G01 X1.0 Y1.0 Z-0.1
N100 G00 Z0.2
N110 M998 (Tool change height)
N120 M30"""

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    if (lang == "id") "Simpan sebagai Projek NC" else "Save Converted NC Project",
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (lang == "id") "Masukkan nama baru untuk menyimpan kode CNC yang telah dikonversi:" 
                        else "Provide a file name to save the converted NC standard code locally:",
                        color = UnselectedGrey,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = saveProjectName,
                        onValueChange = { saveProjectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = UnselectedGrey
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveProject(saveProjectName)
                        showSaveDialog = false
                        Toast.makeText(
                            context,
                            if (lang == "id") "Projek '$saveProjectName' disimpan di folder riwayat lokal!" 
                            else "Project '$saveProjectName' saved in local workspace history!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark)
                ) {
                    Text(if (lang == "id") "SIMPAN" else "SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(if (lang == "id") "BATAL" else "CANCEL", color = PrimaryCyan)
                }
            },
            containerColor = CardDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Header Title section
        Column {
            Text(
                tTitle,
                color = PrimaryCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                tDesc,
                color = TextLight.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        // Configuration Card with Swappable toggles
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (lang == "id") "PARAMETER KONVERSI STANDAR" else "TRANSCRIPTION CONFIGURATION",
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Toggle 1: Strip N-Line Numbers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Hapus Nomor Baris (Nxxx)" else "Strip Line Numbers (Nxxx)",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (lang == "id") "Mengurangi ukuran file G-code agar pengiriman mikrokontroler lancar" else "Deletes Nxxx prefixes to maximize transmission bandwidth on small buffers",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = viewModel.converterStripLineNums,
                        onCheckedChange = { viewModel.converterStripLineNums = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }

                HorizontalDivider(color = BorderCyan.copy(alpha = 0.5f))

                // Toggle 2: Scale G20 Inch to G21 Metric (25.4 multiplication)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Skala G20 Inci ke G21 mm (Metrik)" else "Scale G20 Inches to G21 Metric (mm)",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (lang == "id") "Mendeteksi G20 & mengalikan koordinat X/Y/Z/F dengan 25.4 otomatis" else "Auto-multiplies coordinate integers by 25.4 to fit metric-only routers",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = viewModel.converterScaleInchToMm,
                        onCheckedChange = { viewModel.converterScaleInchToMm = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }

                HorizontalDivider(color = BorderCyan.copy(alpha = 0.5f))

                // Toggle 3: Convert Tool Changes to manual pause
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Ganti M6 dengan Jeda Manual M0" else "Translate M6 to Manual Pause M0",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (lang == "id") "Menghentikan spindle agar operator dapat mengganti mata bor dengan aman" else "Inserts M0 pause cues instead of crashing on multi-tool command blocks",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = viewModel.converterChangeToolPause,
                        onCheckedChange = { viewModel.converterChangeToolPause = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }

                HorizontalDivider(color = BorderCyan.copy(alpha = 0.5f))

                // Toggle 4: Remove Adornments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Bersihkan Semua Komentar / Header" else "Omit Comments & Superfluous Metadata",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (lang == "id") "Menghapus komentar sampingan agar file NC menjadi sangat ringkas" else "Safely drops trailing parentheses strings and blank spacers",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = viewModel.converterRemoveAdornments,
                        onCheckedChange = { viewModel.converterRemoveAdornments = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }
            }
        }

        // Two Column Workspaces: Input Raw & Output NC Standard side-by-side (if size suits, stacked here)
        ResponsiveFlexLayout(spacing = 16.dp) {
            FlexItem(expandedWeight = 1f) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (lang == "id") "MASUKKAN G-CODE ASAL (PASTE / KETIK)" else "INPUT CNC/MACH3 G-CODE WORKSPACE",
                                color = PrimaryCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Load sample command
                            Button(
                                onClick = {
                                    viewModel.converterRawInput = sampleMach3Code
                                    Toast.makeText(context, if (lang == "id") "Contoh berkas Mach3 dimuat!" else "Mach3 sample code loaded!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.15f), contentColor = PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    if (lang == "id") "MUAT CONTOH DESIGN" else "LOAD SAMPLE CODE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        OutlinedTextField(
                            value = viewModel.converterRawInput,
                            onValueChange = { viewModel.converterRawInput = it },
                            placeholder = {
                                Text(
                                    if (lang == "id") "Tempel kode Mach3 (.tap / .txt / .cnc) Anda di sini..." 
                                    else "Paste legacy Mach3 G-code instructions here (example: N10 G20 G00 X1.5 Y2.3)...",
                                    fontSize = 11.sp,
                                    color = UnselectedGrey
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .testTag("converter_raw_input_textfield"),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLight),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderCyan
                            ),
                            maxLines = 15
                        )

                        // Large Primary Conversion Trigger Button
                        Button(
                            onClick = {
                                if (viewModel.converterRawInput.isBlank()) {
                                    Toast.makeText(context, if (lang == "id") "Input raw G-code masih kosong!" else "Input raw G-code Workspace is empty!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.runMach3Conversion()
                                    Toast.makeText(context, if (lang == "id") "Berhasil mengompilasi ke NC Standar!" else "Standard NC file compiled!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("trigger_conversion_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (lang == "id") "TERJEMAHKAN KE NC STANDAR" else "TRANSCOMPILE TO HIGH-PRECISION .NC",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (viewModel.hasConvertedOnce) {
                FlexItem(expandedWeight = 1f) {
                    // Output Result Section (Only visible after compilation once)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Metrics & Log Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (lang == "id") "BERKAS NC STANDAR DIHASILKAN (SIAP CNC)" else "STANDARD .NC OUTPUT CODE (CNC READY)",
                                    color = RouterGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Box(
                                    modifier = Modifier
                                        .background(RouterGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (lang == "id") "UNIT ASAL: ${viewModel.converterOriginalUnit}" else "ORIGINAL UNIT: ${viewModel.converterOriginalUnit}",
                                        color = RouterGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Display scrollable output NC text field
                            OutlinedTextField(
                                value = viewModel.converterOutput,
                                onValueChange = { viewModel.converterOutput = it },
                                readOnly = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .testTag("converter_processed_output"),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLight),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RouterGreen,
                                    unfocusedBorderColor = BorderCyan
                                ),
                                maxLines = 15
                            )

                            // Logs scrollable readout
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateDark, RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    if (lang == "id") "Laporan Log Perubahan Transpiler:" else "Transpiler Change Audit Log:",
                                    color = PrimaryCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (viewModel.converterLog.isEmpty()) {
                                    Text("- " + if (lang == "id") "Semua baris sudah mematuhi standar standar." else "All instruction parameters match standard syntax.", color = UnselectedGrey, fontSize = 9.sp)
                                } else {
                                    viewModel.converterLog.take(6).forEach { log ->
                                        Text("• $log", color = TextLight.copy(alpha = 0.8f), fontSize = 9.sp, lineHeight = 11.sp)
                                    }
                                    if (viewModel.converterLog.size > 6) {
                                        Text("• ... dan ${viewModel.converterLog.size - 6} perubahan log sekunder lainnya.", color = UnselectedGrey, fontSize = 9.sp)
                                    }
                                }
                            }

                            // Action buttons: Clipboard, Simulate / Load Preview, Save
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Copy to clipboard Button
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Standard_NC_Converted_GCode", viewModel.converterOutput)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, if (lang == "id") "NC G-code disalin ke clipboard!" else "NC G-code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f), contentColor = TextLight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(if (lang == "id") "SALIN KODE" else "COPY NC CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // Load and Simulate inside direct app preview window
                                Button(
                                    onClick = {
                                        viewModel.loadConvertedGCodeIntoApp()
                                        Toast.makeText(context, if (lang == "id") "Dimuat! Mengalihkan ke tab simulator..." else "NC toolpath loaded into simulation!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RouterGreen, contentColor = SlateDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.2f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (lang == "id") "SIMULASI LIVE" else "LIVE SIMULATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // Save as new local File Project in database
                                Button(
                                    onClick = {
                                        saveProjectName = "Converted Mach3 NC ${System.currentTimeMillis() % 1000}"
                                        showSaveDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(if (lang == "id") "SIMPAN DATA" else "SAVE PROJECT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PANE 4: LIVE PREVIEW & GCODE EDITOR
// ==========================================
@Composable
fun LivePreviewPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gcodeVisualTransformation = remember { GCodeVisualTransformation() }
    
    // Launcher to save compile/converted G-code physical file onto the SD-card or Downloads directory directly.
    val saveGCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(viewModel.currentGCode.toByteArray(kotlin.text.Charsets.UTF_8))
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (lang == "id") "Berkas G-code (.nc) berhasil disimpan!" else "G-code file (.nc) saved successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )
    
    val tTitle = if (lang == "id") "G-code Real-time Backplotter" else "Live G-Code Simulator"
    val tSimSpeed = if (lang == "id") "Kecepatan Simulasi" else "Simulation Speed"
    val tEditorHeadline = if (lang == "id") "Coretan Kode G-Code (Terminal Editor)" else "Raw G-Code Editor Terminal"
    val tCopy = if (lang == "id") "Salin Kode" else "Copy Code"
    val tShare = if (lang == "id") "Bagikan File" else "Share File"
    val tSaveProj = if (lang == "id") "Simpan Projek" else "Save Project"
    val tEnterName = if (lang == "id") "Beri nama projek Anda:" else "Enter name for your project:"

    var showSaveDialog by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("Desain Baru 1") }

    // Navigation and interactive camera visualizer states for rendering toolpath lines
    var is3DView by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val pulseValue by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    var canvasZoom by remember { mutableStateOf(1.0f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    // Start simulation loop ticker inside G-code Parser coordinates when active
    LaunchedEffect(viewModel.isSimulating) {
        if (viewModel.isSimulating) {
            viewModel.maxSegmentsToDraw = 0
            while (viewModel.isSimulating) {
                if (viewModel.maxSegmentsToDraw < viewModel.parsedSegments.size) {
                    viewModel.maxSegmentsToDraw++
                    delay(30) // speed delay tick
                } else {
                    viewModel.maxSegmentsToDraw = 0 // loop
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            tTitle,
            color = PrimaryCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        ResponsiveFlexLayout(spacing = 16.dp) {
            FlexItem(expandedWeight = 1.2f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // --- THE INTERACTIVE COORDINATE NAVIGATION TOOLBAR (D3-Style Native Android Visualizer) ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { is3DView = !is3DView },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (is3DView) PrimaryCyan else SlateDark,
                                contentColor = if (is3DView) Color.Black else TextLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (is3DView) "3D Isometric" else "2D Flat View", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { canvasZoom = (canvasZoom - 0.25f).coerceAtLeast(0.5f) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp, 36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextLight, fontFamily = FontFamily.Monospace)
                        }

                        Text(
                            "zoom: x${String.format(java.util.Locale.US, "%.2f", canvasZoom)}",
                            color = TextLight,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = { canvasZoom = (canvasZoom + 0.25f).coerceAtMost(5.0f) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp, 36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextLight, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                canvasZoom = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.9f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (lang == "id") "Reset" else "Reset", fontSize = 10.sp, color = TextLight, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // --- THE GRAPHICAL SIMULATION CANVAS WINDOW ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141218)),
                        border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panOffsetX += dragAmount.x
                                    panOffsetY += dragAmount.y
                                }
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                val canvasW = size.width
                                val canvasH = size.height

                                // 1. Math scale representing the coordinate bounds
                                val spaceScaleX = canvasW / viewModel.workspaceWidth
                                val spaceScaleY = canvasH / viewModel.workspaceHeight
                                val baseScale = minOf(spaceScaleX, spaceScaleY) * 0.85f
                                val scale = baseScale * canvasZoom

                                // Coordinate projection transform supporting flat 2D and tilted isometric 3D depths
                                val transform: (Float, Float, Float) -> Offset = { x, y, z ->
                                    val dx = x - viewModel.workspaceWidth / 2f
                                    val dy = y - viewModel.workspaceHeight / 2f
                                    
                                    if (is3DView) {
                                        val angleRad = java.lang.Math.toRadians(30.0)
                                        val cosA = kotlin.math.cos(angleRad).toFloat()
                                        val sinA = kotlin.math.sin(angleRad).toFloat()
                                        
                                        val projX = (dx - dy) * cosA
                                        val projY = (dx + dy) * sinA - (z * 2.2f)
                                        
                                        Offset(
                                            (canvasW / 2f) + panOffsetX + projX * scale,
                                            (canvasH / 2f) + panOffsetY - projY * scale
                                        )
                                    } else {
                                        Offset(
                                            (canvasW / 2f) + panOffsetX + dx * scale,
                                            (canvasH / 2f) + panOffsetY - dy * scale
                                        )
                                    }
                                }

                                // Render workspace borders
                                val p00 = transform(0f, 0f, 0f)
                                val pW0 = transform(viewModel.workspaceWidth, 0f, 0f)
                                val pWH = transform(viewModel.workspaceWidth, viewModel.workspaceHeight, 0f)
                                val p0H = transform(0f, viewModel.workspaceHeight, 0f)

                                drawLine(color = BorderCyan.copy(alpha = 0.4f), start = p00, end = pW0, strokeWidth = 1.5.dp.toPx())
                                drawLine(color = BorderCyan.copy(alpha = 0.4f), start = pW0, end = pWH, strokeWidth = 1.5.dp.toPx())
                                drawLine(color = BorderCyan.copy(alpha = 0.4f), start = pWH, end = p0H, strokeWidth = 1.5.dp.toPx())
                                drawLine(color = BorderCyan.copy(alpha = 0.4f), start = p0H, end = p00, strokeWidth = 1.5.dp.toPx())

                                // Render mesh subdivisions (50mm increments)
                                val gridStep = 50f
                                var gx = 0f
                                while (gx <= viewModel.workspaceWidth) {
                                    drawLine(
                                        color = TravelGray.copy(alpha = 0.15f),
                                        start = transform(gx, 0f, 0f),
                                        end = transform(gx, viewModel.workspaceHeight, 0f),
                                        strokeWidth = 1f
                                    )
                                    gx += gridStep
                                }
                                var gy = 0f
                                while (gy <= viewModel.workspaceHeight) {
                                    drawLine(
                                        color = TravelGray.copy(alpha = 0.15f),
                                        start = transform(0f, gy, 0f),
                                        end = transform(viewModel.workspaceWidth, gy, 0f),
                                        strokeWidth = 1f
                                    )
                                    gy += gridStep
                                }

                                // Draw corner workspace text tags
                                val pnt = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 8.dp.toPx()
                                    isAntiAlias = true
                                }
                                drawContext.canvas.nativeCanvas.drawText("X0 Y0 Z0 (WCS)", p00.x + 4.dp.toPx(), p00.y - 4.dp.toPx(), pnt)
                                drawContext.canvas.nativeCanvas.drawText("X${viewModel.workspaceWidth.toInt()} Y0", pW0.x - 44.dp.toPx(), pW0.y - 4.dp.toPx(), pnt)
                                drawContext.canvas.nativeCanvas.drawText("X${viewModel.workspaceWidth.toInt()} Y${viewModel.workspaceHeight.toInt()}", pWH.x - 44.dp.toPx(), pWH.y + 11.dp.toPx(), pnt)
                                drawContext.canvas.nativeCanvas.drawText("X0 Y${viewModel.workspaceHeight.toInt()}", p0H.x + 4.dp.toPx(), p0H.y + 11.dp.toPx(), pnt)

                                // Draw Work Coordinate System Offset Start pointer target crosshair
                                val startPtWCS = transform(viewModel.startOffsetX, viewModel.startOffsetY, 0f)
                                drawCircle(color = Color.Yellow.copy(alpha = 0.8f), radius = 5.dp.toPx(), center = startPtWCS, style = Stroke(width = 1.5.dp.toPx()))
                                drawLine(color = Color.Yellow.copy(alpha = 0.8f), start = Offset(startPtWCS.x - 10.dp.toPx(), startPtWCS.y), end = Offset(startPtWCS.x + 10.dp.toPx(), startPtWCS.y), strokeWidth = 1.5f)
                                drawLine(color = Color.Yellow.copy(alpha = 0.8f), start = Offset(startPtWCS.x, startPtWCS.y - 10.dp.toPx()), end = Offset(startPtWCS.x, startPtWCS.y + 10.dp.toPx()), strokeWidth = 1.5f)

                                // 2. Draw parsed segments with backplot
                                val drawLimit = viewModel.maxSegmentsToDraw.coerceAtMost(viewModel.parsedSegments.size)
                                for (i in 0 until drawLimit) {
                                    val segment = viewModel.parsedSegments[i]
                                    val startDraw = transform(segment.start.x, segment.start.y, segment.startZ)
                                    val endDraw = transform(segment.end.x, segment.end.y, segment.endZ)

                                    // G0 Travel vs Cut lines rendering
                                    if (segment.type == GCodeParser.MoveType.TRAVEL) {
                                        drawLine(
                                            color = TravelGray,
                                            start = startDraw,
                                            end = endDraw,
                                            strokeWidth = 1.2.dp.toPx()
                                        )
                                    } else {
                                        val glowingCyan = when (viewModel.toolType) {
                                            "LASER" -> {
                                                val intensity = (segment.sPower / 1000f).coerceIn(0.2f, 1.0f)
                                                PrimaryCyan.copy(alpha = intensity)
                                            }
                                            "SPINDLE" -> SpindleGold
                                            else -> RouterGreen
                                        }
                                        drawLine(
                                            color = glowingCyan,
                                            start = startDraw,
                                            end = endDraw,
                                            strokeWidth = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )

                                        // Draw beautiful directional chevrons/arrowheads along the CUT lines so the path direction is clear
                                        if (i % 8 == 0) {
                                            val dx = endDraw.x - startDraw.x
                                            val dy = endDraw.y - startDraw.y
                                            val len = kotlin.math.hypot(dx, dy)
                                            if (len > 14f) {
                                                val midX = (startDraw.x + endDraw.x) / 2f
                                                val midY = (startDraw.y + endDraw.y) / 2f
                                                
                                                val ux = dx / len
                                                val uy = dy / len
                                                
                                                val ox = -uy
                                                val oy = ux
                                                
                                                val wingOffset = 3.dp.toPx()
                                                val arrowBaseX = midX - ux * wingOffset
                                                val arrowBaseY = midY - uy * wingOffset
                                                
                                                val leftWingX = arrowBaseX - ox * wingOffset
                                                val leftWingY = arrowBaseY - oy * wingOffset
                                                
                                                val rightWingX = arrowBaseX + ox * wingOffset
                                                val rightWingY = arrowBaseY + oy * wingOffset
                                                
                                                // Left wing of chevron
                                                drawLine(
                                                    color = glowingCyan.copy(alpha = 0.8f),
                                                    start = Offset(midX, midY),
                                                    end = Offset(leftWingX, leftWingY),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                                // Right wing of chevron
                                                drawLine(
                                                    color = glowingCyan.copy(alpha = 0.8f),
                                                    start = Offset(midX, midY),
                                                    end = Offset(rightWingX, rightWingY),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }

                                    // Render active CNC drawing cursor dot with concentric pulsing glow
                                    if (i == drawLimit - 1) {
                                        val cursorColor = when (viewModel.toolType) {
                                            "LASER" -> LaserCrimson
                                            "SPINDLE" -> SpindleGold
                                            else -> RouterGreen
                                        }

                                        // Pulsing outer halo matching the active laser/spindle power setting
                                        drawCircle(
                                            color = cursorColor.copy(alpha = (2.0f - pulseValue).coerceIn(0f, 0.45f)),
                                            radius = 11.dp.toPx() * pulseValue,
                                            center = endDraw,
                                            style = Stroke(width = 1.8.dp.toPx())
                                        )

                                        // Glow gradient envelope
                                        drawCircle(
                                            color = cursorColor.copy(alpha = 0.25f),
                                            radius = 7.dp.toPx() * pulseValue,
                                            center = endDraw
                                        )

                                        // Core cursor head
                                        drawCircle(
                                            color = cursorColor,
                                            radius = 5.dp.toPx(),
                                            center = endDraw
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = 2.dp.toPx(),
                                            center = endDraw
                                        )
                                    }
                                }
                            }

                            // HUD Overlay Status box with rich charcoal/black alpha backdrop
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = Color.Black.copy(alpha = 0.65f),
                                border = BorderStroke(0.5.dp, Color(0xFF49454F))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        "STATUS HUD",
                                        color = Color(0xFFD0BCFF),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val curSec = viewModel.parsedSegments.getOrNull(
                                        (viewModel.maxSegmentsToDraw - 1).coerceAtLeast(0)
                                    )
                                    Text(
                                        "X: ${String.format("%.1f", curSec?.end?.x ?: 0f)} mm",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "Y: ${String.format("%.1f", curSec?.end?.y ?: 0f)} mm",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "S-Power: ${curSec?.sPower ?: 0}",
                                        color = when (viewModel.toolType) {
                                            "LASER" -> Color(0xFFD0BCFF)
                                            "SPINDLE" -> SpindleGold
                                            else -> RouterGreen
                                        },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "Lines: ${viewModel.parsedSegments.size}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Interactive Zoom/Reset overlay button
                            IconButton(
                                onClick = { viewModel.maxSegmentsToDraw = viewModel.parsedSegments.size; viewModel.isSimulating = false },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(SlateDark, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Simulation", tint = PrimaryCyan)
                            }
                        }
                    }

                    // --- PLAYBACK CONTROLS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.isSimulating = !viewModel.isSimulating },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isSimulating) LaserCrimson else PrimaryCyan,
                                contentColor = SlateDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (viewModel.isSimulating) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (viewModel.isSimulating) (if (lang == "id") "JEDA SIM" else "PAUSE SIM") else (if (lang == "id") "PUTAR SIMULASI" else "PLAY SIMULATE"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Sim manual progress slider bar
                    Column {
                        Text(
                            if (lang == "id") "Garis Toolpath: ${viewModel.maxSegmentsToDraw} / ${viewModel.parsedSegments.size}" else "G-Code Toolpaths Lines: ${viewModel.maxSegmentsToDraw} / ${viewModel.parsedSegments.size}",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = viewModel.maxSegmentsToDraw.toFloat(),
                            onValueChange = {
                                viewModel.isSimulating = false
                                viewModel.maxSegmentsToDraw = it.toInt()
                            },
                            valueRange = 0f..(viewModel.parsedSegments.size.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                        )
                    }
                }
            }

            FlexItem(expandedWeight = 1.0f) {
                // --- TERMINAL RAW G-CODE DISPLAY CARD ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tEditorHeadline,
                            color = PrimaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        // GCode Output Text Field (editable terminal stream modeled as a dark professional console)
                        OutlinedTextField(
                            value = viewModel.currentGCode,
                            onValueChange = {
                                viewModel.currentGCode = it
                                // Recalculate outline paths instantly
                                viewModel.parsedSegments = GCodeParser.parseGCode(it)
                                viewModel.maxSegmentsToDraw = viewModel.parsedSegments.size
                            },
                            visualTransformation = gcodeVisualTransformation,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF211F26),
                                unfocusedContainerColor = Color(0xFF211F26),
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = Color(0xFF49454F),
                                cursorColor = PrimaryCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .testTag("gcode_terminal_editor"),
                            maxLines = 100
                        )

                        // Row 1: Copy, Export file (.nc) and Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Copy to clipboard
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("CNC GCODE", viewModel.currentGCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, if (lang == "id") "G-code disalin ke papan klip!" else "G-code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = TextLight),
                                border = BorderStroke(0.5.dp, PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(tCopy, fontSize = 10.sp, maxLines = 1)
                            }

                            // Native Download / Export file (.nc)
                            Button(
                                onClick = {
                                    try {
                                        saveGCodeLauncher.launch("CNC_G_Code_File.nc")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error launching saver: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = RouterGreen),
                                border = BorderStroke(0.5.dp, RouterGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.2f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (lang == "id") "Unduh (.nc)" else "Export (.nc)", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                            }

                            // Share raw text
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "CNC G-Code File")
                                        putExtra(Intent.EXTRA_TEXT, viewModel.currentGCode)
                                    }
                                    context.startActivity(Intent.createChooser(intent, tShare))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = TextLight),
                                border = BorderStroke(0.5.dp, PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(tShare, fontSize = 10.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Row 2: Database Save
                        Button(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tSaveProj, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to enter project title
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(tSaveProj, color = PrimaryCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(tEnterName, color = TextLight, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectNameInput,
                        onValueChange = { projectNameInput = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            focusedBorderColor = PrimaryCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectNameInput.isNotEmpty()) {
                            viewModel.saveProject(projectNameInput)
                            showSaveDialog = false
                            Toast.makeText(context, if (lang == "id") "Projek berhasil disimpan!" else "Project saved successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("OK", color = SlateDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(if (lang == "id") "Batal" else "Cancel", color = LaserCrimson)
                }
            },
            containerColor = CardDark
        )
    }
}

// ==========================================
// PANE 5: PROJECTS (FILE PERSISTENCE LISTS)
// ==========================================
@Composable
fun ProjectsPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()

    val tTitle = if (lang == "id") "Manajemen File Projek G-code" else "G-Code File & Database Manager"
    val tDesc = if (lang == "id") "Muat kembali file desain G-code yang tersimpan dari riwayat lokal:" else "Reload/Export raw CNC toolpaths saved locally on your device:"
    val tEmpty = if (lang == "id") "Tidak ada projek tersimpan. Buat desain baru pada tab Teks, Sketsa, atau Preset!" else "No saved projects found. Go design one in Text, Sketch, or Preset tabs!"
    val tDeleteAll = if (lang == "id") "Hapus Semua Projek" else "Flush Database Entirely"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    tTitle,
                    color = PrimaryCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(tDesc, color = UnselectedGrey, fontSize = 11.sp)
            }

            if (projects.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearAllProjects() }) {
                    Icon(Icons.Default.Delete, contentDescription = tDeleteAll, tint = LaserCrimson)
                }
            }
        }

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CardDark, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tEmpty,
                    color = UnselectedGrey,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(projects) { project ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, Color(0x1B00E5FF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loadProject(project)
                                Toast
                                    .makeText(
                                        context,
                                        if (lang == "id") "Projek '${project.name}' dimuat!" else "Project '${project.name}' reloaded!",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    project.name,
                                    color = TextLight,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = if (project.toolType == "LASER") LaserCrimson.copy(alpha = 0.2f) else SpindleGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            project.toolType,
                                            color = if (project.toolType == "LASER") LaserCrimson else SpindleGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        "${project.width.toInt()}x${project.height.toInt()}mm",
                                        color = UnselectedGrey,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // G-code share icon
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "CNC GCODE ${project.name}")
                                            putExtra(Intent.EXTRA_TEXT, project.gcode)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share G-Code"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = PrimaryCyan)
                                }

                                // Delete icon
                                IconButton(onClick = { viewModel.deleteProject(project) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LaserCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PANE 6: SETTINGS (SOPHISTICATED CONFIGS)
// ==========================================
@Composable
fun SettingsPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = androidx.compose.ui.platform.LocalContext.current
    val tTitle = if (lang == "id") "Pengaturan CNC & Mesin (FluidNC)" else "Machine & FluidNC Config"
    val tSub = if (lang == "id") "Sesuaikan parameter perangkat keras, feedrate, kecepatan spindle, dan inisialisasi G-code:" else "Configure raw feedrates, safety coordinates, and customized tool codes:"

    var showAddBitDialog by remember { mutableStateOf(false) }
    var showEditBitDialog by remember { mutableStateOf<com.example.data.RouterBit?>(null) }

    var bitNameInput by remember { mutableStateOf("") }
    var bitTypeInput by remember { mutableStateOf("Straight Flute") }
    var bitDiameterInput by remember { mutableStateOf("3.175") }
    var bitFlutesInput by remember { mutableStateOf(2) }
    
    val tLangTitle = if (lang == "id") "Bahasa Aplikasi (Language)" else "App Language (Bahasa)"
    val tWorkspace = if (lang == "id") "Batas Dimensi Benda Kerja (mm)" else "Workpiece Dimensions (mm)"
    val tSpeedHeader = if (lang == "id") "Faktor Kecepatan & Feedrate (F-Word)" else "Feedrates Defaults (F-Word)"
    val tTravelF = if (lang == "id") "Feedrate Jelajah rapid (mm/min)" else "Rapid Travel Feedrate (G0 F)"
    val tCutF = if (lang == "id") "Feedrate Pemotongan cut (mm/min)" else "Normal Cut Feedrate (G1 F)"
    val tLaserPower = if (lang == "id") "Laser Power Maksimum (S-Word)" else "Max Tool Power S-Scale"

    val tModeHeader = if (lang == "id") "Mode Kerja Spindle & Z-Axis" else "Z-Axis Spindle Passes Configuration"
    val tSafeZ = if (lang == "id") "Ketinggian Aman Z (Safe Height)" else "Safe Z Clearance Level"
    val tCutZ = if (lang == "id") "Kedalaman Potong Total (Cut Z)" else "Total Cut Depth (Absolute Z)"
    val tZStep = if (lang == "id") "Kedalaman per Pass (Multi-pass)" else "Multipass Step Increment"

    var settingsCategory by remember { mutableStateOf("GENERAL") }
    val categoriesList = listOf(
        Pair("GENERAL", if (lang == "id") "⚙️ Umum" else "⚙️ General"),
        Pair("WORKSPACE", if (lang == "id") "📏 Area Kerja" else "📏 Workspace"),
        Pair("TOOLHEAD", if (lang == "id") "🔌 Perkakas" else "🔌 Toolhead"),
        Pair("DATABASES", if (lang == "id") "📂 Resep & Presisi" else "📂 Specs & Database")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            tTitle,
            color = PrimaryCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(tSub, color = TextLight, fontSize = 11.sp)

        // Category navigation row (styled pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categoriesList.forEach { (catID, catLabel) ->
                val isSelected = settingsCategory == catID
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PrimaryCyan.copy(alpha = 0.15f) else CardDark
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) PrimaryCyan else BorderCyan.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { settingsCategory = catID }
                ) {
                    Text(
                        text = catLabel,
                        color = if (isSelected) PrimaryCyan else TextLight,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (settingsCategory == "GENERAL") {
        // --- APP THEME & VISUAL CUSTOMIZATION CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "id") "🎨 PERSONALISASI TEMA VISUAL" else "🎨 APP VISUAL THEMES",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                // Dark/Light Mode instant toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "id") "Mode Gelap (Dark Mode)" else "Dark Mode Theme",
                            color = TextLight,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (lang == "id") "Ganti tingkat kecerahan layar" else "Switch default lighting modes dynamically",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = viewModel.isDarkTheme,
                        onCheckedChange = { viewModel.isDarkTheme = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                    )
                }
                
                // Solid border divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderCyan.copy(alpha = 0.5f))
                )
                
                Text(
                    text = if (lang == "id") "Pilih Profil Skema Warna:" else "Select Screen Color Profile:",
                    color = TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                
                val themeList = listOf(
                    Triple("SLATE", if (lang == "id") "Slate Classic" else "Slate Classic", Color(0xFF00E5FF)),
                    Triple("CYBERPUNK", if (lang == "id") "Cyberpunk Neon" else "Cyberpunk Neon", Color(0xFFFF007F)),
                    Triple("EMERALD", if (lang == "id") "Emerald Sage" else "Emerald Forest", Color(0xFF00E676)),
                    Triple("AMBER", if (lang == "id") "Amber Sunset" else "Amber Sunset", Color(0xFFFF9100)),
                    Triple("AMETHYST", if (lang == "id") "Amethyst Cosmic" else "Cosmic Amethyst", Color(0xFFD0BCFF))
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeList.forEach { (themeKey, name, colorIndicator) ->
                        val isSel = viewModel.selectedTheme == themeKey
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) PrimaryCyan.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSel) PrimaryCyan else BorderCyan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable { viewModel.selectedTheme = themeKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(colorIndicator)
                                )
                                Text(
                                    text = name,
                                    color = if (isSel) PrimaryCyan else TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
        } // End GENERAL Theme Card

        if (settingsCategory == "DATABASES") {
        // Toolpath Optimization Control Card
        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.optimizeToolpathOn = !viewModel.optimizeToolpathOn }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.optimizeToolpathOn,
                    onCheckedChange = { viewModel.optimizeToolpathOn = it },
                    colors = CheckboxDefaults.colors(checkedColor = PrimaryCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (lang == "id") "Optimasi Jalur Alat (Nearest Neighbor TSP)" else "Optimize CNC Toolpath (Nearest TSP)",
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        if (lang == "id") "Urutkan jalur otomatis untuk meminimalkan gerakan kosong udara bebas" else "Sequences cutting vectors automatically to minimize free air-travel distance",
                        color = TextLight,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Material Database & Recommend Engine
        val context = LocalContext.current
        var materialFilter by remember { mutableStateOf("ALL") } // "ALL", "Wood", "Acrylic", "Aluminum"
        var expandedMaterialIndex by remember { mutableStateOf(-1) }

        var presetNameInput by remember { mutableStateOf("") }
        var showSavePresetDialog by remember { mutableStateOf(false) }

        if (showSavePresetDialog) {
            AlertDialog(
                onDismissRequest = { showSavePresetDialog = false },
                title = { Text(if (lang == "id") "Simpan Konfigurasi" else "Save Custom Preset", color = PrimaryCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (lang == "id") "Beri nama material unik untuk menyimpan parameter aktif:" else "Choose a name for your custom laser or spindle config:", color = TextLight, fontSize = 12.sp)
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (presetNameInput.trim().isNotEmpty()) {
                                viewModel.saveNewUserPreset(presetNameInput.trim())
                                presetNameInput = ""
                                showSavePresetDialog = false
                                Toast.makeText(context, if (lang == "id") "Preset berhasil disimpan!" else "Preset saved successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("Save", color = SlateDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSavePresetDialog = false }) {
                        Text("Cancel", color = TextLight)
                    }
                },
                containerColor = CardDark
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (lang == "id") "Database Rekomendasi Material" else "CNC Material Knowledge Database",
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    if (lang == "id") "Gunakan pengaturan teruji pabrikan untuk Wood, Acrylic, dan Aluminum berikut ini:" 
                    else "Access verified speed dials, depths, and bit sizes for Wood, Acrylic, and Aluminum cuts:",
                    color = TextLight,
                    fontSize = 10.sp
                )

                // Category Tabs for Database
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALL", "Wood", "Acrylic", "Aluminum").forEach { cat ->
                        val isSelected = materialFilter == cat
                        val tabBtnColor = if (isSelected) PrimaryCyan else SlateDark
                        val tabTextColor = if (isSelected) SlateDark else TextLight
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(tabBtnColor)
                                .clickable { materialFilter = cat; expandedMaterialIndex = -1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (cat == "ALL") (if (lang == "id") "Semua" else "All") else cat,
                                color = tabTextColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Material recommendations list
                val filteredList = viewModel.materialDatabase.filter { 
                    materialFilter == "ALL" || it.category == materialFilter 
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredList.forEachIndexed { index, sample ->
                        val isExpanded = expandedMaterialIndex == index
                        val borderCol = if (isExpanded) PrimaryCyan.copy(alpha = 0.5f) else BorderCyan.copy(alpha = 0.3f)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateDark),
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedMaterialIndex = if (isExpanded) -1 else index }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            sample.subCategory,
                                            color = TextLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            sample.category,
                                            color = PrimaryCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand details",
                                        tint = TextLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (lang == "id") sample.descIdIndo else sample.descId,
                                        color = TextLight.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Display parameters table grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Laser Settings Card
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = CardDark),
                                            border = BorderStroke(0.5.dp, BorderCyan)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("LASER ENGRAVE / CUT", color = LaserCrimson, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Power: S${sample.laserPower}", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("Feed: ${sample.laserFeedrate.toInt()} mm/m", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { 
                                                        viewModel.applyMaterialSample(sample, "LASER")
                                                        Toast.makeText(context, if (lang == "id") "Konfigurasi Laser dimuat!" else "Laser parameters loaded!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.fillMaxWidth().height(26.dp)
                                                ) {
                                                    Text(if (lang == "id") "Pakai Laser" else "Load Laser", color = if (viewModel.isDarkTheme) Color.Black else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        // Router / Spindle settings Card
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = CardDark),
                                            border = BorderStroke(0.5.dp, BorderCyan)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("ROUTER / SPINDLE", color = SpindleGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Speed: ${sample.spindleRPM} RPM", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("Feed: ${sample.spindleFeedrate.toInt()} mm/m", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("Depth: ${sample.cutDepth} mm", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("StepZ: ${sample.stepDown} mm/p", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                if (sample.bitType.isNotEmpty()) {
                                                    Text("Bit: ${sample.bitType.substringBefore(" (")}", color = RouterGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(
                                                    onClick = { 
                                                        viewModel.applyMaterialSample(sample, "ROUTER")
                                                        Toast.makeText(context, if (lang == "id") "Konfigurasi Router dimuat!" else "Router parameters loaded!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = RouterGreen),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.fillMaxWidth().height(26.dp)
                                                ) {
                                                    Text(if (lang == "id") "Pakai Router" else "Load Router", color = if (viewModel.isDarkTheme) Color.Black else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = BorderCyan.copy(alpha = 0.4f))

                // Custom Presets Carousel List and Actions
                Text(
                    if (lang == "id") "Preset Tersimpan Kustom" else "Custom Stored Presets",
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    items(viewModel.activePresetsList) { preset ->
                        val isSelected = viewModel.selectedPresetName == preset.name
                        val borderCol = if (isSelected) {
                            when (preset.toolType) {
                                "LASER" -> LaserCrimson
                                "SPINDLE" -> SpindleGold
                                else -> RouterGreen
                            }
                        } else {
                            Color(0xFF49454F)
                        }

                        Card(
                            modifier = Modifier
                                .width(155.dp)
                                .clickable { viewModel.applyPreset(preset) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SlateDark else CardDark
                            ),
                            border = BorderStroke(1.5.dp, borderCol),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        preset.toolType,
                                        color = when (preset.toolType) {
                                            "LASER" -> LaserCrimson
                                            "SPINDLE" -> SpindleGold
                                            else -> RouterGreen
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (preset.isUserPreset) {
                                        IconButton(
                                            onClick = { viewModel.deleteUserPreset(preset) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Hapus Preset",
                                                tint = LaserCrimson,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    preset.name,
                                    color = TextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("Feed: ${preset.feedrateCut.toInt()} mm/m", color = TextLight, fontSize = 9.sp)
                                if (preset.toolType == "LASER") {
                                    Text("Power: S${preset.laserPowerS}", color = TextLight, fontSize = 9.sp)
                                } else {
                                    Text("Cut Z: ${preset.targetCutZ} mm", color = TextLight, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showSavePresetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add preset", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (lang == "id") "Simpan Parameter Aktif kustom" else "Save Active Settings as Custom Recipe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        } // End DATABASES Materials Card

        if (settingsCategory == "GENERAL") {
        // 1. Language selector
        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(tLangTitle, color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.language = "id" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.language == "id") PrimaryCyan else SlateDark,
                            contentColor = if (viewModel.language == "id") SlateDark else TextLight
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bahasa Indonesia", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.language = "en" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.language == "en") PrimaryCyan else SlateDark,
                            contentColor = if (viewModel.language == "en") SlateDark else TextLight
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("English", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        } // End GENERAL Language Selector

        if (settingsCategory == "WORKSPACE") {
        // 2. Machine Workspace limits
        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tWorkspace, color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("X-Max (mm)", color = TextLight, fontSize = 11.sp)
                        OutlinedTextField(
                            value = viewModel.workspaceWidth.toInt().toString(),
                            onValueChange = {
                                viewModel.workspaceWidth = it.toFloatOrNull() ?: viewModel.workspaceWidth
                                viewModel.compileCurrentPaths()
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Y-Max (mm)", color = TextLight, fontSize = 11.sp)
                        OutlinedTextField(
                            value = viewModel.workspaceHeight.toInt().toString(),
                            onValueChange = {
                                viewModel.workspaceHeight = it.toFloatOrNull() ?: viewModel.workspaceHeight
                                viewModel.compileCurrentPaths()
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (lang == "id") "Ketebalan Bahan (Thickness)" else "Material Thickness",
                        color = TextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${String.format("%.1f", viewModel.materialThickness)} mm",
                        color = PrimaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = viewModel.materialThickness,
                    onValueChange = { viewModel.materialThickness = it },
                    valueRange = 0.5f..50.0f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                )
            }
        }

        // 2b. Work Coordinate System Origin / Starting Point Offset Config
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (lang == "id") "Titik Awal Mulai Router/Laser (WCS Offset)" else "Work Origin / Starting Point (WCS Offset)",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    if (lang == "id") "Geser letak koordinat mulai benda kerja (X/Y Offset) relatif terhadap titik acuan nol." 
                    else "Offsets the workpiece coordinates relative to the machine origin to determine starting point.",
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (lang == "id") "Offset Mulai X (mm)" else "Start Offset X (mm)", color = TextLight, fontSize = 11.sp)
                        OutlinedTextField(
                            value = if (viewModel.startOffsetX == 0f) "" else viewModel.startOffsetX.toString(),
                            placeholder = { Text("0.0", color = TextLight.copy(alpha = 0.4f), fontSize = 11.sp) },
                            onValueChange = {
                                val v = it.toFloatOrNull()
                                if (it.isEmpty()) {
                                    viewModel.startOffsetX = 0f
                                    viewModel.compileCurrentPaths()
                                } else if (v != null) {
                                    viewModel.startOffsetX = v
                                    viewModel.compileCurrentPaths()
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextLight),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                                focusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (lang == "id") "Offset Mulai Y (mm)" else "Start Offset Y (mm)", color = TextLight, fontSize = 11.sp)
                        OutlinedTextField(
                            value = if (viewModel.startOffsetY == 0f) "" else viewModel.startOffsetY.toString(),
                            placeholder = { Text("0.0", color = TextLight.copy(alpha = 0.4f), fontSize = 11.sp) },
                            onValueChange = {
                                val v = it.toFloatOrNull()
                                if (it.isEmpty()) {
                                    viewModel.startOffsetY = 0f
                                    viewModel.compileCurrentPaths()
                                } else if (v != null) {
                                    viewModel.startOffsetY = v
                                    viewModel.compileCurrentPaths()
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextLight),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                                focusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }

                // Preset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.startOffsetX = 0f
                            viewModel.startOffsetY = 0f
                            viewModel.compileCurrentPaths()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (lang == "id") "Reset Nol" else "Reset Zero (0,0)", fontSize = 10.sp, color = TextLight)
                    }

                    Button(
                        onClick = {
                            viewModel.startOffsetX = viewModel.workspaceWidth / 4f
                            viewModel.startOffsetY = viewModel.workspaceHeight / 4f
                            viewModel.compileCurrentPaths()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (lang == "id") "Tengah" else "Center WCS", fontSize = 10.sp, color = TextLight)
                    }
                }
            }
        }
        } // End WORKSPACE

        if (settingsCategory == "TOOLHEAD") {
        // 3. Tool Select Mode: Laser vs Spindle vs Router
        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (lang == "id") "Peralatan Aktif (Tool Selector)" else "CNC Toolhead Mode",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.toolType = "LASER"; viewModel.compileCurrentPaths() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.toolType == "LASER") LaserCrimson else SlateDark,
                            contentColor = if (viewModel.toolType == "LASER") Color.White else TextLight
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("LASER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.toolType = "SPINDLE"; viewModel.compileCurrentPaths() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.toolType == "SPINDLE") SpindleGold else SlateDark,
                            contentColor = if (viewModel.toolType == "SPINDLE") Color.White else TextLight
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("SPINDLE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.toolType = "ROUTER"; viewModel.compileCurrentPaths() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.toolType == "ROUTER") RouterGreen else SlateDark,
                            contentColor = if (viewModel.toolType == "ROUTER") Color.White else TextLight
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("ROUTER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (viewModel.toolType == "ROUTER") {
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (lang == "id") "Pilih Jenis Mata Pisau:" else "Select Router Bit:",
                            color = RouterGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    bitNameInput = ""
                                    bitTypeInput = "Straight Flute"
                                    bitDiameterInput = "3.175"
                                    bitFlutesInput = 2
                                    showAddBitDialog = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Bit",
                                tint = RouterGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                if (lang == "id") "Tambah" else "Add",
                                color = RouterGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    val routerBitsFromDb by viewModel.allRouterBits.collectAsStateWithLifecycle()
                    val routerBitsList = routerBitsFromDb.ifEmpty {
                        listOf(
                            com.example.data.RouterBit(id = 1, name = "Straight Flute (3.175mm)", type = "Straight Flute", diameter = 3.175f, flutes = 2, isCustom = false),
                            com.example.data.RouterBit(id = 2, name = "Upcut Spiral (2.0mm)", type = "Upcut Spiral", diameter = 2.0f, flutes = 1, isCustom = false),
                            com.example.data.RouterBit(id = 3, name = "Downcut Spiral (3.175mm)", type = "Downcut Spiral", diameter = 3.175f, flutes = 2, isCustom = false),
                            com.example.data.RouterBit(id = 4, name = "V-Bit (60-Degree Engraving)", type = "V-Bit", diameter = 3.175f, flutes = 1, isCustom = false),
                            com.example.data.RouterBit(id = 5, name = "Ball Nose Mill (1.5mm)", type = "Ball Nose", diameter = 1.5f, flutes = 2, isCustom = false)
                        )
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        routerBitsList.forEach { bit ->
                            val isSelected = viewModel.routerBitType == bit.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.routerBitType = bit.name }
                                    .background(if (isSelected) RouterGreen.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) RouterGreen.copy(alpha = 0.3f) else Color.Transparent
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.routerBitType = bit.name },
                                    colors = RadioButtonDefaults.colors(selectedColor = RouterGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            bit.name,
                                            color = if (isSelected) RouterGreen else TextLight,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (bit.isCustom) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(RouterGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "KUSTOM",
                                                    color = RouterGreen,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "Tipe: ${bit.type} • Dia: ${bit.diameter}mm • Flute: ${bit.flutes}",
                                        color = TextLight.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                if (bit.isCustom) {
                                    IconButton(
                                        onClick = {
                                            bitNameInput = bit.name
                                            bitTypeInput = bit.type
                                            bitDiameterInput = bit.diameter.toString()
                                            bitFlutesInput = bit.flutes
                                            showEditBitDialog = bit
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Bit",
                                            tint = RouterGreen.copy(alpha = 0.8f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteRouterBit(bit)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Bit",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showAddBitDialog) {
                        AlertDialog(
                            onDismissRequest = { showAddBitDialog = false },
                            title = {
                                Text(
                                    if (lang == "id") "Tambah Mata Pisau Custom" else "Add Custom Router Bit",
                                    color = RouterGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (lang == "id") "Nama Mata Pisau:" else "Bit Name:", color = TextLight, fontSize = 11.sp)
                                    OutlinedTextField(
                                        value = bitNameInput,
                                        onValueChange = { bitNameInput = it },
                                        placeholder = { Text("Mata Pisau 6mm", fontSize = 11.sp, color = TextLight.copy(alpha = 0.5f)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RouterGreen,
                                            focusedTextColor = TextLight,
                                            unfocusedTextColor = TextLight
                                        )
                                    )

                                    Text(if (lang == "id") "Jenis Tipe:" else "Bit Type:", color = TextLight, fontSize = 11.sp)
                                    val types = listOf("Straight Flute", "Upcut Spiral", "Downcut Spiral", "V-Bit", "Ball Nose", "Compression", "T-Slot", "Core Box", "Custom")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        types.forEach { type ->
                                            val isSelected = bitTypeInput == type
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) RouterGreen.copy(alpha = 0.15f) else CardDark,
                                                border = BorderStroke(1.dp, if (isSelected) RouterGreen else TextLight.copy(alpha = 0.2f)),
                                                modifier = Modifier.clickable { bitTypeInput = type }
                                            ) {
                                                Text(
                                                    text = type,
                                                    color = if (isSelected) RouterGreen else TextLight,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (lang == "id") "Diameter (mm):" else "Diameter (mm):", color = TextLight, fontSize = 11.sp)
                                            OutlinedTextField(
                                                value = bitDiameterInput,
                                                onValueChange = { bitDiameterInput = it },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = RouterGreen,
                                                    focusedTextColor = TextLight,
                                                    unfocusedTextColor = TextLight
                                                )
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (lang == "id") "Jumlah Flute:" else "Flutes Count:", color = TextLight, fontSize = 11.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                IconButton(
                                                    onClick = { if (bitFlutesInput > 1) bitFlutesInput-- },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("-", color = RouterGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Text("$bitFlutesInput", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { bitFlutesInput++ },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("+", color = RouterGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val diam = bitDiameterInput.toFloatOrNull() ?: 3.175f
                                        val nameStr = if (bitNameInput.trim().isEmpty()) "$bitTypeInput (${diam}mm)" else bitNameInput.trim()
                                        viewModel.insertRouterBit(
                                            com.example.data.RouterBit(
                                                name = nameStr,
                                                type = bitTypeInput,
                                                diameter = diam,
                                                flutes = bitFlutesInput,
                                                isCustom = true
                                            )
                                        )
                                        showAddBitDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RouterGreen, contentColor = SlateDark)
                                ) {
                                    Text(if (lang == "id") "Tambah" else "Add", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddBitDialog = false }) {
                                    Text(if (lang == "id") "Batal" else "Cancel", color = TextLight.copy(alpha = 0.6f))
                                }
                            }
                        )
                    }

                    showEditBitDialog?.let { bitToEdit ->
                        AlertDialog(
                            onDismissRequest = { showEditBitDialog = null },
                            title = {
                                Text(
                                    if (lang == "id") "Ubah Mata Pisau" else "Edit Router Bit",
                                    color = RouterGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (lang == "id") "Nama Mata Pisau:" else "Bit Name:", color = TextLight, fontSize = 11.sp)
                                    OutlinedTextField(
                                        value = bitNameInput,
                                        onValueChange = { bitNameInput = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RouterGreen,
                                            focusedTextColor = TextLight,
                                            unfocusedTextColor = TextLight
                                        )
                                    )

                                    Text(if (lang == "id") "Jenis Tipe:" else "Bit Type:", color = TextLight, fontSize = 11.sp)
                                    val types = listOf("Straight Flute", "Upcut Spiral", "Downcut Spiral", "V-Bit", "Ball Nose", "Compression", "T-Slot", "Core Box", "Custom")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        types.forEach { type ->
                                            val isSelected = bitTypeInput == type
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) RouterGreen.copy(alpha = 0.15f) else CardDark,
                                                border = BorderStroke(1.dp, if (isSelected) RouterGreen else TextLight.copy(alpha = 0.2f)),
                                                modifier = Modifier.clickable { bitTypeInput = type }
                                            ) {
                                                Text(
                                                    text = type,
                                                    color = if (isSelected) RouterGreen else TextLight,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (lang == "id") "Diameter (mm):" else "Diameter (mm):", color = TextLight, fontSize = 11.sp)
                                            OutlinedTextField(
                                                value = bitDiameterInput,
                                                onValueChange = { bitDiameterInput = it },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = RouterGreen,
                                                    focusedTextColor = TextLight,
                                                    unfocusedTextColor = TextLight
                                                )
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (lang == "id") "Jumlah Flute:" else "Flutes Count:", color = TextLight, fontSize = 11.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                IconButton(
                                                    onClick = { if (bitFlutesInput > 1) bitFlutesInput-- },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("-", color = RouterGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Text("$bitFlutesInput", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { bitFlutesInput++ },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("+", color = RouterGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val diam = bitDiameterInput.toFloatOrNull() ?: 3.175f
                                        val nameStr = if (bitNameInput.trim().isEmpty()) "$bitTypeInput (${diam}mm)" else bitNameInput.trim()
                                        viewModel.updateRouterBit(
                                            bitToEdit.copy(
                                                name = nameStr,
                                                type = bitTypeInput,
                                                diameter = diam,
                                                flutes = bitFlutesInput
                                            )
                                        )
                                        showEditBitDialog = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RouterGreen, contentColor = SlateDark)
                                ) {
                                    Text(if (lang == "id") "Simpan" else "Save", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditBitDialog = null }) {
                                    Text(if (lang == "id") "Batal" else "Cancel", color = TextLight.copy(alpha = 0.6f))
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Multi-feedrate speeds controls
        Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tSpeedHeader, color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                // Cut speed
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tCutF, color = TextLight, fontSize = 11.sp)
                        Text("${viewModel.feedrateCut.toInt()} mm/min", color = PrimaryCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = viewModel.feedrateCut,
                        onValueChange = { viewModel.feedrateCut = it; viewModel.compileCurrentPaths() },
                        valueRange = 100f..4000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                    )
                }

                // Travel speed
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tTravelF, color = TextLight, fontSize = 11.sp)
                        Text("${viewModel.feedrateTravel.toInt()} mm/min", color = PrimaryCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = viewModel.feedrateTravel,
                        onValueChange = { viewModel.feedrateTravel = it; viewModel.compileCurrentPaths() },
                        valueRange = 500f..8000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                    )
                }

                // S scale power max S
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (viewModel.toolType == "LASER") tLaserPower else (if (lang == "id") "Putaran Spindle / Router (S RPM)" else "Spindle / Router RPM (S)"),
                            color = TextLight,
                            fontSize = 11.sp
                        )
                        Text(
                            if (viewModel.toolType == "LASER") "S${viewModel.targetPowerS}" else "${viewModel.targetPowerS * 15} RPM",
                            color = PrimaryCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = viewModel.targetPowerS.toFloat(),
                        onValueChange = { viewModel.targetPowerS = it.toInt(); viewModel.compileCurrentPaths() },
                        valueRange = 100f..1000f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan)
                    )
                }
            }
        }

        // 4. Spindle / Router Z depths configs (if active or for convenience)
        if (viewModel.toolType == "SPINDLE" || viewModel.toolType == "ROUTER") {
            val activeDepthColor = if (viewModel.toolType == "ROUTER") RouterGreen else SpindleGold
            Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (lang == "id") "Pengaturan Kedalaman & Ketinggian Potong Z" else tModeHeader,
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Safe Z
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tSafeZ, color = TextLight, fontSize = 11.sp)
                            Text("${String.format("%.1f", viewModel.safeZ)} mm", color = activeDepthColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = viewModel.safeZ,
                            onValueChange = { viewModel.safeZ = it; viewModel.compileCurrentPaths() },
                            valueRange = 1f..15f,
                            colors = SliderDefaults.colors(thumbColor = activeDepthColor)
                        )
                    }

                    // Cut Z
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tCutZ, color = TextLight, fontSize = 11.sp)
                            Text("${String.format("%.1f", viewModel.targetCutZ)} mm", color = activeDepthColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = viewModel.targetCutZ,
                            onValueChange = { viewModel.targetCutZ = it; viewModel.compileCurrentPaths() },
                            valueRange = -25f..-0.1f,
                            colors = SliderDefaults.colors(thumbColor = activeDepthColor)
                        )
                    }

                    // step pass
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tZStep, color = TextLight, fontSize = 11.sp)
                            Text("${String.format("%.1f", viewModel.zStepPerPass)} mm/pass", color = activeDepthColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = viewModel.zStepPerPass,
                            onValueChange = { viewModel.zStepPerPass = it; viewModel.compileCurrentPaths() },
                            valueRange = 0.1f..10f,
                            colors = SliderDefaults.colors(thumbColor = activeDepthColor)
                        )
                    }

                    // Precise Numeric Inputs for Z values
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (lang == "id") "Presisi Input Angka (Numeric Mode)" else "Precise Numeric Input (Keypad)",
                        color = TextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (lang == "id") "Safe Z (mm)" else "Safe Z (mm)", color = TextLight, fontSize = 9.sp)
                            OutlinedTextField(
                                value = viewModel.safeZ.toString(),
                                onValueChange = {
                                    val v = it.toFloatOrNull()
                                    if (it.isEmpty()) {
                                        viewModel.safeZ = 5.0f
                                        viewModel.compileCurrentPaths()
                                    } else if (v != null) {
                                        viewModel.safeZ = v
                                        viewModel.compileCurrentPaths()
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextLight),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeDepthColor,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                                    focusedTextColor = TextLight,
                                    unfocusedTextColor = TextLight,
                                    cursorColor = activeDepthColor
                                ),
                                modifier = Modifier.height(48.dp).fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(if (lang == "id") "Kedalaman Potong (mm)" else "Cut Depth Z (mm)", color = TextLight, fontSize = 9.sp)
                            OutlinedTextField(
                                value = viewModel.targetCutZ.toString(),
                                onValueChange = {
                                    val v = it.toFloatOrNull()
                                    if (it.isEmpty()) {
                                        viewModel.targetCutZ = -1.5f
                                        viewModel.compileCurrentPaths()
                                    } else if (v != null) {
                                        viewModel.targetCutZ = v
                                        viewModel.compileCurrentPaths()
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextLight),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeDepthColor,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                                    focusedTextColor = TextLight,
                                    unfocusedTextColor = TextLight,
                                    cursorColor = activeDepthColor
                                ),
                                modifier = Modifier.height(48.dp).fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (lang == "id") "Z-Step per Pass (mm)" else "Step Z (mm)", color = TextLight, fontSize = 9.sp)
                            OutlinedTextField(
                                value = viewModel.zStepPerPass.toString(),
                                onValueChange = {
                                    val v = it.toFloatOrNull()
                                    if (it.isEmpty()) {
                                        viewModel.zStepPerPass = 0.5f
                                        viewModel.compileCurrentPaths()
                                    } else if (v != null) {
                                        viewModel.zStepPerPass = v
                                        viewModel.compileCurrentPaths()
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextLight),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeDepthColor,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                                    focusedTextColor = TextLight,
                                    unfocusedTextColor = TextLight,
                                    cursorColor = activeDepthColor
                                ),
                                modifier = Modifier.height(48.dp).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        } // End TOOLHEAD

        if (settingsCategory == "DATABASES") {
        // 5. Precision & Multi-Pass Carving Strategy Card (Anti-drift, High Precision)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Precision",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (lang == "id") "ULTRA PRESISI & STRATEGI ANTI-MELENCENG" else "ULTRA PRECISION & ANTI-DRIFT STRATEGY",
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    if (lang == "id") "Strategi pemotongan multi-pass dirancang untuk mencegah pergeseran letak jalur potong akibat getaran mekanis dinamis."
                    else "Cutting strategies configured to prevent axis slippage and coordinate misalignment on DIY and professional routers.",
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )

                Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                // 2a. Carving Multi-Pass Strategy Toggle
                Text(
                    if (lang == "id") "Pilih Strategi Potong Berulang (Multi-Pass)" else "Carving Depth Strategy (Multi-Pass)",
                    color = TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.updateMultiPassStrategy("DEPTH_FIRST") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.multiPassStrategy == "DEPTH_FIRST") PrimaryCyan else SlateDark,
                            contentColor = if (viewModel.multiPassStrategy == "DEPTH_FIRST") Color.Black else TextLight
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (lang == "id") "Kedalaman-Dulu (Rekomendasi!)" else "Depth-First (Best-Fit)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.updateMultiPassStrategy("LAYER_FIRST") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.multiPassStrategy == "LAYER_FIRST") PrimaryCyan else SlateDark,
                            contentColor = if (viewModel.multiPassStrategy == "LAYER_FIRST") Color.Black else TextLight
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (lang == "id") "Lapisan-Gantian" else "Layer-By-Layer",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Help/Info text explaining the active strategy
                Text(
                    text = if (viewModel.multiPassStrategy == "DEPTH_FIRST") {
                        if (lang == "id") "★ REKOMENDASI ULTRA PRESISI: Selesaikan satu bentuk hingga kedalaman penuh baru pindah ke bentuk lain. Mengurangi lonjakan perjalanan udara kosong (travel rapid G0) sebesar 90%, mengeliminasi motor stepper slip/melenceng secara total."
                        else "★ HIGH PRECISION RECOMMENDATION: Completes cutting one shape to full depth before moving to the next. Reduces generic travel (G0) move overhead by 90% to avoid stepper slippage."
                    } else {
                        if (lang == "id") "★ MODEL LAPISAN: Memotong semua bentuk secara merata per kedalaman lapisan. Sedikit lambat dan berisiko melenceng jika sasis motor longgar karena banyak travel bolak-balik."
                        else "★ LAYER BY LAYER: Carves all shapes layer-by-layer progressively. High travel overhead might accumulate small axis offsets."
                    },
                    color = if (viewModel.multiPassStrategy == "DEPTH_FIRST") PrimaryCyan else TextLight.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )

                Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                // 2b. Coordinate decimal precision
                Text(
                    if (lang == "id") "Presisi Angka Desimal Koordinat" else "Coordinate Decimal Resolution (G-Code)",
                    color = TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 3, 4).forEach { decimals ->
                        val isSel = viewModel.gcodeDecimalPrecision == decimals
                        val lbl = when (decimals) {
                            2 -> if (lang == "id") "2 Desimal (0.01mm)" else "2 Decimals (0.01mm)"
                            3 -> if (lang == "id") "3 Desimal (0.001mm)" else "3 Decimals (0.001mm)"
                            else -> if (lang == "id") "4 Desimal (0.0001mm)" else "4 Decimals (0.0001mm)"
                        }
                        Button(
                            onClick = { viewModel.updateGCodeDecimalPrecision(decimals) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) PrimaryCyan else SlateDark,
                                contentColor = if (isSel) Color.Black else TextLight
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(lbl, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Text(
                    if (lang == "id") "G-Code beresolusi 3 desimal (0.001mm/1 mikron) atau 4 desimal menghapus pembulatan titik sambungan antar putaran lapisan potong."
                    else "Higher decimals avoid progressive rounding misalignment across continuous passes.",
                    color = TextLight.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }

        // Global G-Code Generation Defaults Card
        val defaultContext = LocalContext.current
        var editCutF by remember(viewModel.defaultFeedrateCut) { mutableStateOf(viewModel.defaultFeedrateCut.toInt().toString()) }
        var editTravelF by remember(viewModel.defaultFeedrateTravel) { mutableStateOf(viewModel.defaultFeedrateTravel.toInt().toString()) }
        var editPlungeF by remember(viewModel.defaultFeedratePlunge) { mutableStateOf(viewModel.defaultFeedratePlunge.toInt().toString()) }
        var editSafeZ by remember(viewModel.defaultSafeZ) { mutableStateOf(viewModel.defaultSafeZ.toString()) }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (lang == "id") "Default G-Code Global" else "Global G-Code Defaults",
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    if (lang == "id") "Tentukan feedrate default, plong, dan tinggi aman Z yang diterapkan ke semua G-code baru secara permanen:"
                    else "Define default feedrates, plunge rates, and safe Z-heights to apply to all newly generated G-code permanently:",
                    color = TextLight,
                    fontSize = 10.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Pemotongan (F Cut)" else "Cut Feed (F Cut)",
                            color = TextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editCutF,
                            onValueChange = { editCutF = it },
                            placeholder = { Text("1200", color = UnselectedGrey, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().testTag("default_cut_feed_input")
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Jelajah (F Travel)" else "Travel Feed (F)",
                            color = TextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editTravelF,
                            onValueChange = { editTravelF = it },
                            placeholder = { Text("3000", color = UnselectedGrey, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().testTag("default_travel_feed_input")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Plunge (F Plunge)" else "Plunge Feed (F)",
                            color = TextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editPlungeF,
                            onValueChange = { editPlungeF = it },
                            placeholder = { Text("200", color = UnselectedGrey, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().testTag("default_plunge_feed_input")
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (lang == "id") "Z Aman (Safe Z)" else "Safe Z Height (mm)",
                            color = TextLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editSafeZ,
                            onValueChange = { editSafeZ = it },
                            placeholder = { Text("5.0", color = UnselectedGrey, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                cursorColor = PrimaryCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().testTag("default_safe_z_input")
                        )
                    }
                }

                Button(
                    onClick = {
                        val cutVal = editCutF.toFloatOrNull() ?: viewModel.defaultFeedrateCut
                        val travelVal = editTravelF.toFloatOrNull() ?: viewModel.defaultFeedrateTravel
                        val plungeVal = editPlungeF.toFloatOrNull() ?: viewModel.defaultFeedratePlunge
                        val safeZVal = editSafeZ.toFloatOrNull() ?: viewModel.defaultSafeZ

                        viewModel.updateDefaultSettings(cutVal, travelVal, plungeVal, safeZVal)
                        
                        editCutF = cutVal.toInt().toString()
                        editTravelF = travelVal.toInt().toString()
                        editPlungeF = plungeVal.toInt().toString()
                        editSafeZ = safeZVal.toString()

                        Toast.makeText(
                            defaultContext,
                            if (lang == "id") "Default global berhasil disimpan & diterapkan!" else "Global defaults successfully saved & applied!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_defaults_button")
                ) {
                    Text(
                        if (lang == "id") "Simpan & Terapkan Default Global" else "Save & Apply Global Defaults",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        } // End DATABASES
    }
}

// ==========================================
// PANE 8: FLUIDNC OFFLINE CONTROL CENTER & WEB UI
// ==========================================
@Composable
fun FluidNCControlPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = android.view.View(LocalContext.current).context
    var subTab by remember { mutableStateOf("DASHBOARD") } // "DASHBOARD" or "WEB_UI"
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var consoleCommandInput by remember { mutableStateOf("") }
    val gcodeVisualTransformation = remember { GCodeVisualTransformation() }

    val tTitle = if (lang == "id") "FluidNC Wi-Fi Offline Control" else "FluidNC Wi-Fi Offline Center"
    val tSubtitle = if (lang == "id") 
        "Kendalian penuh mesin CNC FluidNC Anda secara offline via Wi-Fi lokal dengan lancar." 
        else "Take full wireless control of your FluidNC laser/router offline over local network."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tab Header & Switch Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tTitle,
                    color = PrimaryCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    tSubtitle,
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }

            // Connection indicator LED
            val ledColor = when (viewModel.fluidNCConnectionStatus) {
                "CONNECTED" -> RouterGreen
                "FAILED" -> Color.Red
                "TESTING" -> Color(0xFFFF9800) // Orange
                else -> UnselectedGrey
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(SlateDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ledColor, androidx.compose.foundation.shape.CircleShape)
                        .border(BorderStroke(1.5.dp, TextLight.copy(alpha = 0.2f)), androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = when (viewModel.fluidNCConnectionStatus) {
                        "CONNECTED" -> if (lang == "id") "TERHUBUNG" else "ONLINE"
                        "FAILED" -> if (lang == "id") "TERPUTUS" else "OFFLINE"
                        "TESTING" -> if (lang == "id") "PINGING..." else "TESTING..."
                        else -> "SIAP"
                    },
                    color = ledColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Sub-Navigation: Dashboard Control vs Native Web UI Swappers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { subTab = "DASHBOARD" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "DASHBOARD") PrimaryCyan else Color.Transparent,
                    contentColor = if (subTab == "DASHBOARD") SlateDark else TextLight
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "id") "DASHBOARD KONTROL" else "OFFLINE JOGGER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { subTab = "WEB_UI" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "WEB_UI") PrimaryCyan else Color.Transparent,
                    contentColor = if (subTab == "WEB_UI") SlateDark else TextLight
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "id") "NATIVE WEB UI (192.168.0.1)" else "FLUIDNC WEB PAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Main content render based on active sub tab
        if (subTab == "DASHBOARD") {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Connection Configuration Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (lang == "id") "ALAMAT KONEKSI AP FLUIDNC" else "FLUIDNC WIFI AP CONNECTION",
                            color = PrimaryCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = viewModel.fluidNCIpAddress,
                                onValueChange = { viewModel.fluidNCIpAddress = it },
                                label = { Text("IP Address / Host", fontSize = 10.sp) },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(52.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextLight),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryCyan,
                                    unfocusedBorderColor = BorderCyan,
                                    focusedLabelColor = PrimaryCyan,
                                    unfocusedLabelColor = UnselectedGrey
                                ),
                                singleLine = true
                            )

                            Button(
                                onClick = { 
                                    viewModel.testFluidConnection() 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(if (lang == "id") "SAMBUNG" else "CONNECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Jogging D-Pad Joystick and Steps Size Column
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (lang == "id") "KONTROL NAVIGASI MANUAL (JOGGING)" else "MANUAL JOGGING CONTROLLER (XY / Z)",
                            color = PrimaryCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Joystick grid + Z column
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // XY D-Pad Controller (Cross layout)
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .background(SlateDark, androidx.compose.foundation.shape.CircleShape)
                                    .border(BorderStroke(1.5.dp, BorderCyan.copy(alpha = 0.4f)), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // Y+ Button (Top)
                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 Y${viewModel.fluidNCJogDistance} F${viewModel.fluidNCFeedrate.toInt()}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryCyan),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .size(44.dp)
                                ) {
                                    Text("▲ Y+", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // Y- Button (Bottom)
                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 Y-${viewModel.fluidNCJogDistance} F${viewModel.fluidNCFeedrate.toInt()}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryCyan),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .size(44.dp)
                                ) {
                                    Text("▼ Y-", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // X- Button (Left)
                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 X-${viewModel.fluidNCJogDistance} F${viewModel.fluidNCFeedrate.toInt()}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryCyan),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .size(44.dp)
                                ) {
                                    Text("◀ X-", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // X+ Button (Right)
                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 X${viewModel.fluidNCJogDistance} F${viewModel.fluidNCFeedrate.toInt()}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryCyan),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(44.dp)
                                ) {
                                    Text("▶ X+", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                // Center: Zero Command
                                Button(
                                    onClick = { viewModel.sendFluidCommand("G92 X0 Y0") },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan.copy(alpha = 0.2f), contentColor = PrimaryCyan),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("ZERO\nXY", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Z axis speed controls (Up/Down)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("AXIS Z", color = PrimaryCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                
                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 Z${viewModel.fluidNCJogDistance} F1000") },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f), contentColor = TextLight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(42.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▲ Z+", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                Button(
                                    onClick = { viewModel.sendFluidCommand("G92 Z0") },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateDark, contentColor = PrimaryCyan),
                                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp).width(50.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("ZERO Z", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                Button(
                                    onClick = { viewModel.sendFluidCommand("\$J=G91 Z-${viewModel.fluidNCJogDistance} F1000") },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f), contentColor = TextLight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(42.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("▼ Z-", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // Horizontal segmented-style row for Jog steps size
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (lang == "id") "Jarak Langkah Jogging (Step Size):" else "Step Jog Distance:",
                                color = UnselectedGrey,
                                fontSize = 9.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val steps = listOf(0.1f, 1f, 10f, 50f, 100f)
                                steps.forEach { valStep ->
                                    val isSelected = viewModel.fluidNCJogDistance == valStep
                                    Button(
                                        onClick = { viewModel.fluidNCJogDistance = valStep },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) PrimaryCyan else CardDark,
                                            contentColor = if (isSelected) SlateDark else TextLight
                                        ),
                                        border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("${valStep}mm", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        // Feedrate controller slider
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (lang == "id") "Kecepatan Motor (Feedrate):" else "Jog Feedrate:",
                                    color = UnselectedGrey,
                                    fontSize = 9.sp
                                )
                                Text(
                                    "${viewModel.fluidNCFeedrate.toInt()} mm/min",
                                    color = PrimaryCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Slider(
                                value = viewModel.fluidNCFeedrate,
                                onValueChange = { viewModel.fluidNCFeedrate = it },
                                valueRange = 100f..5000f,
                                colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                            )
                        }
                    }
                }

                // Workpiece Corner Origin G92 Set Tool (Atur Posisi Awal / Pojokan Bahan)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Origin", tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                            Text(
                                if (lang == "id") "TETAPKAN TITIK NOL POJOKAN BAHAN" else "WORKPIECE CORNER ZERO ALIGNER",
                                color = PrimaryCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            if (lang == "id") "Sejajarkan ujung router ke salah satu pojokan bahan secara manual, lalu ketuk sudut pada papan di bawah untuk menyamakan sistem koordinat kerja (G92)."
                            else "Jog the router bit manually to any workpiece corner, then tap the corresponding board corner below to apply coordinate alignment (G92).",
                            color = UnselectedGrey,
                            fontSize = 9.sp
                        )

                        Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)

                        // Material Size Inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = viewModel.workspaceWidth.toInt().toString(),
                                onValueChange = {
                                    val newVal = it.toFloatOrNull() ?: viewModel.workspaceWidth
                                    viewModel.workspaceWidth = newVal.coerceIn(10f, 1000f)
                                },
                                label = { Text(if (lang == "id") "Lebar Bahan (W - mm)" else "Width (W - mm)", fontSize = 9.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLight),
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryCyan,
                                    unfocusedBorderColor = BorderCyan.copy(alpha = 0.4f),
                                    focusedLabelColor = PrimaryCyan,
                                    unfocusedLabelColor = UnselectedGrey
                                ),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = viewModel.workspaceHeight.toInt().toString(),
                                onValueChange = {
                                    val newVal = it.toFloatOrNull() ?: viewModel.workspaceHeight
                                    viewModel.workspaceHeight = newVal.coerceIn(10f, 1000f)
                                },
                                label = { Text(if (lang == "id") "Panjang Bahan (H - mm)" else "Length (H - mm)", fontSize = 9.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLight),
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryCyan,
                                    unfocusedBorderColor = BorderCyan.copy(alpha = 0.4f),
                                    focusedLabelColor = PrimaryCyan,
                                    unfocusedLabelColor = UnselectedGrey
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Interactive Workpiece Board Representation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visual Board (a dark plate with 5 target nodes)
                            Box(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 110.dp)
                                    .background(Color(0xFF0F0E13), RoundedCornerShape(10.dp))
                                    .border(BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        color = BorderCyan.copy(alpha = 0.05f),
                                        size = size
                                    )
                                    // Sub lines representing alignment guide
                                    drawLine(
                                        color = BorderCyan.copy(alpha = 0.15f),
                                        start = Offset(0f, size.height / 2f),
                                        end = Offset(size.width, size.height / 2f),
                                        strokeWidth = 1f
                                    )
                                    drawLine(
                                        color = BorderCyan.copy(alpha = 0.15f),
                                        start = Offset(size.width / 2f, 0f),
                                        end = Offset(size.width / 2f, size.height),
                                        strokeWidth = 1f
                                    )
                                }

                                val w = viewModel.workspaceWidth.toInt()
                                val h = viewModel.workspaceHeight.toInt()

                                // 1. Top Left Node (Atas-Kiri)
                                CornerOriginNode(
                                    align = Alignment.TopStart,
                                    label = "TL",
                                    onClick = {
                                        viewModel.sendFluidCommand("G92 X0 Y$h")
                                        Toast.makeText(context, if (lang == "id") "Nol Kerja disetel ke Kiri-Atas (X0 Y$h)!" else "Work Coordinate Origin set to Top-Left!", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 2. Top Right Node (Atas-Kanan)
                                CornerOriginNode(
                                    align = Alignment.TopEnd,
                                    label = "TR",
                                    onClick = {
                                        viewModel.sendFluidCommand("G92 X$w Y$h")
                                        Toast.makeText(context, if (lang == "id") "Nol Kerja disetel ke Kanan-Atas (X$w Y$h)!" else "Work Coordinate Origin set to Top-Right!", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 3. Center Node (Tengah)
                                CornerOriginNode(
                                    align = Alignment.Center,
                                    label = "MID",
                                    onClick = {
                                        viewModel.sendFluidCommand("G92 X${w/2} Y${h/2}")
                                        Toast.makeText(context, if (lang == "id") "Nol Kerja disetel ke Pusat (X${w/2} Y${h/2})!" else "Work Coordinate Origin set to Center!", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 4. Bottom Left Node (Bawah-Kiri)
                                CornerOriginNode(
                                    align = Alignment.BottomStart,
                                    label = "BL",
                                    onClick = {
                                        viewModel.sendFluidCommand("G92 X0 Y0")
                                        Toast.makeText(context, if (lang == "id") "Nol Kerja disetel ke Kiri-Bawah (X0 Y0)!" else "Work Coordinate Origin set to Bottom-Left!", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 5. Bottom Right Node (Bawah-Kanan)
                                CornerOriginNode(
                                    align = Alignment.BottomEnd,
                                    label = "BR",
                                    onClick = {
                                        viewModel.sendFluidCommand("G92 X$w Y0")
                                        Toast.makeText(context, if (lang == "id") "Nol Kerja disetel ke Kanan-Bawah (X$w Y0)!" else "Work Coordinate Origin set to Bottom-Right!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            // Quick Presets Descriptions/Control List
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    if (lang == "id") "Sudut Acuan Pahat:" else "Cutter Corner Reference:",
                                    color = UnselectedGrey,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (lang == "id") "Arah gerak & alur laju router dikalkulasi dari titik G92 offset terpilih."
                                    else "Cutter flow & feed directions are configured relative to targeted G92 coordinate shift.",
                                    color = TextLight.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = {
                                        viewModel.sendFluidCommand("G92.1")
                                        Toast.makeText(context, if (lang == "id") "Setelan G92 offset di-reset!" else "All coordinate offsets reset!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateDark, contentColor = PrimaryCyan),
                                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (lang == "id") "RESET ABSOLUT (G92.1)" else "CLEAR OFFSETS (G92.1)", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                // Hot Macros and Emergency Stop Row
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (lang == "id") "MAKRO & KONTROL DARURAT CNC" else "MACROS & EMERGENCY SYSTEM HALT",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Unlock system
                            Button(
                                onClick = { viewModel.sendFluidCommand("\$X") },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.15f), contentColor = PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("\$X UNLOCK", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                            // Homing system
                            Button(
                                onClick = { viewModel.sendFluidCommand("\$H") },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.15f), contentColor = PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("\$H HOME ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                                                     Button(
                                onClick = { 
                                    viewModel.sendFluidCommand("M112 ; !!! EMERGENCY HALT !!!") 
                                    Toast.makeText(context, "EMERGENCY PRESSED! CNC HALTED!", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.2f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EM-STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // CNC Z-Probe Controller Panel (Ultra high-precision CNC tool setting)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Probe", tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                            Text(
                                if (lang == "id") "KONTROL PROBE Z-AXIS" else "Z-AXIS PROBE MODULE",
                                color = PrimaryCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            if (lang == "id") "Sentuh permukaan bawah pahat ke touchplate logam secara otomatis untuk mengatur titik reference Z=0 absolut."
                            else "Auto search workpiece zero using a metal touch plate sensor over G38.2 probing limits.",
                            color = UnselectedGrey,
                            fontSize = 9.sp
                        )

                        Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)

                        // Settings Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Thickness Settings
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (lang == "id") "Tebal Plat (mm)" else "Plate Thick. (mm)",
                                    color = UnselectedGrey,
                                    fontSize = 8.sp
                                )
                                var textVal by remember(viewModel.probeThickness) { mutableStateOf(viewModel.probeThickness.toString()) }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = textVal,
                                    onValueChange = {
                                        textVal = it
                                        it.toFloatOrNull()?.let { v -> viewModel.probeThickness = v }
                                    },
                                    textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }

                            // Max Search Limit Distance
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (lang == "id") "Batas Cari Z (mm)" else "Search Limit Z",
                                    color = UnselectedGrey,
                                    fontSize = 8.sp
                                )
                                var textVal by remember(viewModel.probeMaxDistance) { mutableStateOf(viewModel.probeMaxDistance.toString()) }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = textVal,
                                    onValueChange = {
                                        textVal = it
                                        it.toFloatOrNull()?.let { v -> viewModel.probeMaxDistance = v }
                                    },
                                    textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }

                            // Feedrate Cut speed
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (lang == "id") "Speed (mm/m)" else "Probe F (mm/m)",
                                    color = UnselectedGrey,
                                    fontSize = 8.sp
                                )
                                var textVal by remember(viewModel.probeFeedrate) { mutableStateOf(viewModel.probeFeedrate.toInt().toString()) }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = textVal,
                                    onValueChange = {
                                        textVal = it
                                        it.toFloatOrNull()?.let { v -> viewModel.probeFeedrate = v }
                                    },
                                    textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Trigger Action Button
                        Button(
                            onClick = { viewModel.startZAxisProbe() },
                            enabled = !viewModel.isProbing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryCyan,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                                disabledContentColor = TextLight.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (viewModel.isProbing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TextLight, strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (lang == "id") "MENCARI TOUCHPLATE..." else "PROBING IN PROGRESS...", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (lang == "id") "MULAI PROBING SEKARANG" else "START AUTO Z PROBE RUN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Streaming Program Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (lang == "id") "PENGIRIM PROGRAM G-CODE SEKARANG" else "CURRENT G-CODE DIRECT STREAMER",
                            color = RouterGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            if (lang == "id") 
                                "Kirim file desain G-Code saat ini baris-demi-baris secara wireless langsung ke FluidNC."
                                else "Stream the currently compiled laser/spindle G-code straight to FluidNC parser over IP network.",
                            color = UnselectedGrey,
                            fontSize = 10.sp
                        )

                        if (viewModel.isStreamingToFluidNC) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                    Text(
                                        if (lang == "id") "Mengirim: ${viewModel.fluidNCStreamCurrentIndex} / ${viewModel.fluidNCStreamTotal}" 
                                        else "Streaming: ${viewModel.fluidNCStreamCurrentIndex} / ${viewModel.fluidNCStreamTotal} lines",
                                        color = RouterGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "${(viewModel.fluidNCStreamProgress * 100).toInt()}%",
                                        color = RouterGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { viewModel.fluidNCStreamProgress },
                                    color = RouterGreen,
                                    trackColor = SlateDark,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.stopStreamingGCode() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text(if (lang == "id") "HENTIKAN KIRIM" else "CANCEL STREAM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startStreamingGCode() },
                                colors = ButtonDefaults.buttonColors(containerColor = RouterGreen, contentColor = SlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (lang == "id") "MULAI KIRIM KODE UTAMA" else "START DIRECT TRANSMISSION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Retro Terminal/Console Log Console Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (lang == "id") "RETRO SERIAL LOG FLUIDNC" else "REALTIME TRANSCIEVER LOG",
                                color = PrimaryCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(
                                onClick = { viewModel.fluidNCConsoleResponse = listOf("Log cleared.") },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Text("CLEAR", color = PrimaryCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(SlateDark, RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            viewModel.fluidNCConsoleResponse.forEach { response ->
                                Text(
                                    text = response,
                                    color = RouterGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
                                )
                            }
                        }

                        // Custom Command Box (Kotak Perintah G-Code)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = consoleCommandInput,
                                onValueChange = { consoleCommandInput = it },
                                visualTransformation = gcodeVisualTransformation,
                                label = { Text(if (lang == "id") "Ketik Perintah / G-Code" else "Type Command / G-Code", fontSize = 10.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLight),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryCyan,
                                    unfocusedBorderColor = BorderCyan.copy(alpha = 0.5f),
                                    focusedLabelColor = PrimaryCyan,
                                    unfocusedLabelColor = UnselectedGrey
                                ),
                                placeholder = {
                                    Text(
                                        text = "Contoh: G0 X10 Y10",
                                        color = UnselectedGrey.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (consoleCommandInput.trim().isNotEmpty()) {
                                        viewModel.sendFluidCommand(consoleCommandInput.trim())
                                        consoleCommandInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Text(if (lang == "id") "KIRIM" else "SEND", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // WEB_UI NATIVE EMBEDDED VIEW WITH CHROMIUM CONTROLS
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Address toolbar controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardDark, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "URL:",
                        color = PrimaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    Text(
                        "http://${viewModel.cleanIP}/",
                        color = TextLight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    // Refresh Button
                    Button(
                        onClick = { 
                            webViewInstance?.reload() 
                            Toast.makeText(context, if (lang == "id") "Memuat ulang Web UI..." else "Reloading Web UI...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan.copy(alpha = 0.15f), contentColor = PrimaryCyan),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (lang == "id") "MUAT ULANG" else "REFRESH", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Open External Browser Link
                    Button(
                        onClick = { 
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://${viewModel.cleanIP}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f), contentColor = TextLight),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (lang == "id") "BROWSER LUAR" else "OUTSIDE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Full featured Offline Edge WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(CardDark)
                        .border(BorderStroke(1.5.dp, BorderCyan.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewClient = android.webkit.WebViewClient()
                                webChromeClient = android.webkit.WebChromeClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    allowFileAccess = true
                                }
                                loadUrl("http://${viewModel.cleanIP}")
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ==========================================
// PANE 9: OFF-LINE DXF TO G-CODE PLOTTER
// ==========================================
@Composable
fun DXFConverterPane(viewModel: GCodeViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current
    
    // File open picker contract
    val dxfPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val name = getFileNameFromUri(context, uri)
                    viewModel.importDxfFromStream(inputStream, name)
                } else {
                    Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val tTitle = if (lang == "id") "Konverter Vektor DXF CAD" else "DXF CAD Vector Plotter"
    val tDesc = if (lang == "id") 
        "Ubah file desain draft CAD standard (.dxf) langsung ke G-code berkualitas tinggi secara offline tanpa internet." 
        else "Process standard AutoCAD/Inkscape DXF draft drawings directly into G-code entirely offline."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tTitle,
                color = PrimaryCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                tDesc,
                color = TextLight.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }

        // Main action / File status
        if (viewModel.dxfFileName == null) {
            // Upload card mimicking a plotter machine loading slot
            Card(
                onClick = { dxfPickerLauncher.launch("*/*") },
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.5.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Share, 
                        contentDescription = "Upload DXF", 
                        tint = PrimaryCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (lang == "id") "Buka / Impor File DXF CAD" else "Open DXF CAD Drawing",
                        color = TextLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (lang == "id") "Klik untuk memilih file .dxf lokal" else "Click to select a local .dxf draw file",
                        color = UnselectedGrey,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            // Status Card for parsed elements
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RouterGreen, modifier = Modifier.size(24.dp))
                            Column {
                                Text(
                                    viewModel.dxfFileName ?: "CAD Drawing",
                                    color = TextLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    if (lang == "id") "Elemen Terproses: ${viewModel.dxfRawPaths.size} rute pen-down" 
                                    else "Parsed Entities: ${viewModel.dxfRawPaths.size} pen-down paths",
                                    color = UnselectedGrey,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Close/Clear vector
                        IconButton(onClick = {
                            viewModel.dxfFileName = null
                            viewModel.dxfRawPaths = emptyList()
                            viewModel.activePaths = emptyList()
                            viewModel.currentGCode = ""
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { dxfPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark, contentColor = PrimaryCyan),
                            border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (lang == "id") "GANTI FILE" else "CHANGE FILE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.applyDxfPathsAndCompile()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan.copy(alpha = 0.15f), contentColor = PrimaryCyan),
                            border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (lang == "id") "REFRESH TRACE" else "RE-PLOT TRACE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Processing Loading Indicator
        if (viewModel.isProcessingDxf) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = PrimaryCyan, modifier = Modifier.size(24.dp))
                    Text(
                        if (lang == "id") "Mengkalkulasi rute gerak desain..." else "Compiling vector G-code elements...",
                        color = TextLight,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Parsing error feedback
        viewModel.dxfProcessError?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    err,
                    color = Color.Red,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }        // Scaling & Presets settings responsive blocks
        if (viewModel.dxfRawPaths.isNotEmpty()) {
            ResponsiveFlexLayout(spacing = 16.dp) {
                FlexItem(expandedWeight = 1f) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                if (lang == "id") "DONGKRAK SKALA DAN SISTEM UNIT" else "SCALE CALIBRATION & MEASURE",
                                color = PrimaryCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Unit systems Segment
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (lang == "id") "Sistem Unit File DXF Asal:" else "Source file unit system:",
                                    color = UnselectedGrey,
                                    fontSize = 10.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { 
                                            viewModel.dxfUnitIsInch = false
                                            viewModel.applyDxfPathsAndCompile()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!viewModel.dxfUnitIsInch) PrimaryCyan else SlateDark,
                                            contentColor = if (!viewModel.dxfUnitIsInch) SlateDark else TextLight
                                        ),
                                        border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Millimeters (mm)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { 
                                            viewModel.dxfUnitIsInch = true
                                            viewModel.applyDxfPathsAndCompile()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (viewModel.dxfUnitIsInch) PrimaryCyan else SlateDark,
                                            contentColor = if (viewModel.dxfUnitIsInch) SlateDark else TextLight
                                        ),
                                        border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Inches (in)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Scaler Slider
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (lang == "id") "Rasio Skala Desain:" else "Design scale ratio:",
                                        color = UnselectedGrey,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        String.format("%.2f x", viewModel.dxfScaleFactor),
                                        color = PrimaryCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Slider(
                                    value = viewModel.dxfScaleFactor,
                                    onValueChange = { 
                                        viewModel.dxfScaleFactor = it 
                                    },
                                    onValueChangeFinished = {
                                        viewModel.applyDxfPathsAndCompile()
                                    },
                                    valueRange = 0.1f..10.0f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                                )
                            }
                        }
                    }
                }

                FlexItem(expandedWeight = 1.2f) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                if (lang == "id") "PREFERENSI MESIN CNC PILIHAN" else "CNC COMPILATION PRESETS",
                                color = PrimaryCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Tool Choice Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val toolLaser = viewModel.toolType == "LASER"
                                Button(
                                    onClick = { 
                                        viewModel.toolType = "LASER"
                                        viewModel.applyDxfPathsAndCompile()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (toolLaser) PrimaryCyan else SlateDark,
                                        contentColor = if (toolLaser) SlateDark else TextLight
                                    ),
                                    border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (lang == "id") "LASER POTONG" else "LASER CUTTER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { 
                                        viewModel.toolType = "ROUTER"
                                        viewModel.applyDxfPathsAndCompile()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!toolLaser) PrimaryCyan else SlateDark,
                                        contentColor = if (!toolLaser) SlateDark else TextLight
                                    ),
                                    border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (lang == "id") "SPINDLE CNC" else "SPINDLE ROUTER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Status details Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(if (lang == "id") "Kecepatan Potong:" else "Feedrate:", color = UnselectedGrey, fontSize = 9.sp)
                                    Text("${viewModel.feedrateCut.toInt()} mm/min", color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text(if (lang == "id") "Kedalaman Potong Z:" else "Target Depth Z:", color = UnselectedGrey, fontSize = 9.sp)
                                    Text("${viewModel.targetCutZ} mm", color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text(if (lang == "id") "Step Per Pass:" else "Depth Step:", color = UnselectedGrey, fontSize = 9.sp)
                                    Text("${viewModel.zStepPerPass} mm", color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text(if (lang == "id") "Ketinggian Aman:" else "Safe Height Z:", color = UnselectedGrey, fontSize = 9.sp)
                                    Text("${viewModel.safeZ} mm", color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Text(
                                if (lang == "id") 
                                    "*Edit preferensi dan kecepatan ini sewaktu-waktu secara mendalam melalui panel SETTINGS."
                                    else "Tune feedrates, laser commands, and cutter shapes over on the SETTINGS tab.",
                                color = UnselectedGrey,
                                fontSize = 9.sp,
                                style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
                        }
                    }
                }
            }
        }

            // Successfully compilation output feedback
            if (viewModel.currentGCode.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, RouterGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(RouterGreen, androidx.compose.foundation.shape.CircleShape))
                                Text(
                                    if (lang == "id") "G-CODE TERKOMPILASI" else "SUCCESSFULLY COMPILED G-CODE",
                                    color = RouterGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Text(
                                if (lang == "id") "${viewModel.parsedSegments.size} segment rute" else "${viewModel.parsedSegments.size} vector paths",
                                color = UnselectedGrey,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            if (lang == "id") 
                                "G-code berhasil diekstrak dan didesinfeksi dari vector DXF Anda. Sekarang Anda dapat:"
                                else "CNC instructions generated perfectly from CAD elements. You can now choose what to do:",
                            color = TextLight.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Link connection trigger
                            Button(
                                onClick = { 
                                    viewModel.sendFluidCommand("\$X") 
                                    Toast.makeText(context, if (lang == "id") "Membuka Panel Streamer FluidNC..." else "Opening FluidNC Wi-Fi controls...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = SlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.2f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (lang == "id") "KIRIM WI-FI ⚡" else "SEND TO MACHINE ⚡", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }

                            // Download / Save local project representation
                            Button(
                                onClick = { 
                                    try {
                                        val saveName = viewModel.dxfFileName?.replace(".dxf", ".nc") ?: "output.nc"
                                        viewModel.saveProject(saveName)
                                        Toast.makeText(context, if (lang == "id") "Tersimpan ke FILES!" else "Saved to local FILES list!", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateDark, contentColor = RouterGreen),
                                border = BorderStroke(1.dp, RouterGreen.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (lang == "id") "SIMPAN FILE" else "SAVE WORKSPACE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

private fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "drawing.dxf"
}

@Composable
fun BoxScope.CornerOriginNode(
    align: Alignment,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF231E2B),
                contentColor = PrimaryCyan
            ),
            shape = androidx.compose.foundation.shape.CircleShape,
            border = BorderStroke(1.2.dp, PrimaryCyan.copy(alpha = 0.8f)),
            modifier = Modifier
                .align(align)
                .size(32.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = label,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = PrimaryCyan
                )
            }
        }
    }
}

class GCodeVisualTransformation : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val plainText = text.text
        if (plainText == lastText && lastResult != null) {
            return lastResult!!
        }
        val annotated = highlightGCode(plainText)
        val result = TransformedText(annotated, OffsetMapping.Identity)
        lastText = plainText
        lastResult = result
        return result
    }
}

fun highlightGCode(text: String): AnnotatedString {
    if (text.length > 8000) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text)
        val textLength = text.length
        if (textLength == 0) return@buildAnnotatedString
        
        // Split lines while keeping track of actual index positions
        val lines = text.split('\n')
        var currentOffset = 0
        
        for (line in lines) {
            val lineLen = line.length
            
            // Check for comments (starts with semicolon or parentheses)
            var commentIndex = line.indexOf(';')
            if (commentIndex == -1) {
                commentIndex = line.indexOf('(')
            }
            
            val codePart = if (commentIndex != -1) line.substring(0, commentIndex) else line
            
            // Highlight the code part
            var i = 0
            while (i < codePart.length) {
                val c = codePart[i]
                
                // Highlight system parameters or commands which start with '$'
                if (c == '$') {
                    val s = i
                    i++
                    while (i < codePart.length && (codePart[i].isLetterOrDigit() || codePart[i] == '=' || codePart[i] == '.' || codePart[i] == '$')) {
                        i++
                    }
                    val start = (currentOffset + s).coerceIn(0, textLength)
                    val end = (currentOffset + i).coerceIn(0, textLength)
                    if (start < end) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFFEA80FC), fontWeight = FontWeight.Bold), // Vibrant Purple
                            start = start,
                            end = end
                        )
                    }
                    continue
                }
                
                // G / M Commands (G0, G1, M3, M5, etc.)
                if (c.equals('G', ignoreCase = true) || c.equals('M', ignoreCase = true)) {
                    val s = i
                    i++
                    while (i < codePart.length && (codePart[i].isDigit() || codePart[i] == '.')) {
                        i++
                    }
                    val start = (currentOffset + s).coerceIn(0, textLength)
                    val end = (currentOffset + i).coerceIn(0, textLength)
                    if (start < end) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFF00E676), fontWeight = FontWeight.Bold), // Vibrant Lime Green
                            start = start,
                            end = end
                        )
                    }
                    continue
                }
                
                // Sumbu / Axes (X, Y, Z, I, J, K)
                if (c.equals('X', ignoreCase = true) || c.equals('Y', ignoreCase = true) || c.equals('Z', ignoreCase = true) ||
                    c.equals('I', ignoreCase = true) || c.equals('J', ignoreCase = true) || c.equals('K', ignoreCase = true)) {
                    val startLetter = (currentOffset + i).coerceIn(0, textLength)
                    val endLetter = (currentOffset + i + 1).coerceIn(0, textLength)
                    if (startLetter < endLetter) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold), // Sky Blue
                            start = startLetter,
                            end = endLetter
                        )
                    }
                    val startNum = i + 1
                    i++
                    while (i < codePart.length && (codePart[i].isDigit() || codePart[i] == '-' || codePart[i] == '+' || codePart[i] == '.')) {
                        i++
                    }
                    val startN = (currentOffset + startNum).coerceIn(0, textLength)
                    val endN = (currentOffset + i).coerceIn(0, textLength)
                    if (startN < endN) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFFECEFF1)), // Ice White
                            start = startN,
                            end = endN
                        )
                    }
                    continue
                }
                
                // Feedrate (F), Speed (S), Parameters (P, H, D)
                if (c.equals('F', ignoreCase = true) || c.equals('S', ignoreCase = true) || 
                    c.equals('P', ignoreCase = true) || c.equals('H', ignoreCase = true) || c.equals('D', ignoreCase = true)) {
                    val startLetter = (currentOffset + i).coerceIn(0, textLength)
                    val endLetter = (currentOffset + i + 1).coerceIn(0, textLength)
                    if (startLetter < endLetter) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold), // Amber Orange
                            start = startLetter,
                            end = endLetter
                        )
                    }
                    val startNum = i + 1
                    i++
                    while (i < codePart.length && (codePart[i].isDigit() || codePart[i] == '-' || codePart[i] == '+' || codePart[i] == '.')) {
                        i++
                    }
                    val startN = (currentOffset + startNum).coerceIn(0, textLength)
                    val endN = (currentOffset + i).coerceIn(0, textLength)
                    if (startN < endN) {
                        addStyle(
                            style = SpanStyle(color = Color(0xFFECEFF1)), // Ice White
                            start = startN,
                            end = endN
                        )
                    }
                    continue
                }
                
                // Default character
                i++
            }
            
            // Highlight the comment part (Muted Slate Blue-Gray)
            if (commentIndex != -1) {
                val s = (currentOffset + commentIndex).coerceIn(0, textLength)
                val e = (currentOffset + lineLen).coerceIn(0, textLength)
                if (s < e) {
                    addStyle(
                        style = SpanStyle(color = Color(0xFF90A4AE), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), // Slate Muted Gray
                        start = s,
                        end = e
                    )
                }
            }
            
            currentOffset += lineLen + 1 // +1 for the newline character
        }
    }
}

// =========================================================================
// VECTRIC ASPIRE MOBILE CAD/CAM STUDIO
// =========================================================================

data class AspireVector(
    val id: String,
    val type: String, // "Circle", "Rectangle", "Polygon", "Star", "Polyline", "Text"
    val name: String,
    val cx: Float = 0f,
    val cy: Float = 0f,
    val radius: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val cornerRadius: Float = 0f,
    val sides: Int = 6,
    val points: Int = 5,
    val innerRatio: Float = 0.5f,
    val textStr: String = "",
    val textHeight: Float = 12f,
    val rawPoints: List<Offset> = emptyList(),
    val isSelected: Boolean = false
)

data class CAMOperation(
    val id: String,
    val name: String,
    val type: String, // PROFILE, POCKET, DRILL, 3D_ROUGH, 3D_FINISH
    val toolName: String,
    val cutDepth: Float,
    val stepDown: Float,
    val feedRate: Float,
    val plungeRate: Float,
    val rawPaths: List<List<Offset>> = emptyList(),
    val gcode: String = "",
    val isEnabled: Boolean = true
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AspireCADCAMPane(viewModel: GCodeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = viewModel.language

    // --- TRANSLATION DICTIONARY ---
    val tTitle = if (lang == "id") "Vectric ASPIRE CAD/CAM Studio" else "Vectric ASPIRE CAD/CAM Studio"
    val tDesc = if (lang == "id") "Studio CAD/CAM profesional terintegrasi untuk mendesain gambar vektor dan mengonversi menjadi multi-pass G-code mirip software PC Vectric Aspire." else "Professional integrated CAD/CAM workspace for drafting vector shapes and compiling optimized multi-pass CNC G-code."
    
    // UI state
    var selectedTabPreview by remember { mutableStateOf("2D_CAD") } // "2D_CAD" or "3D_SIMULATION"
    var selectedCADTool by remember { mutableStateOf("NONE") } // NONE, CIRCLE, RECTANGLE, POLYGON, STAR, OVAL, ARC, POLYLINE, TEXT
    var activeWorkspaceTab by remember { mutableStateOf("CAD") } // "CAD" Drawing vs "CAM" Toolpaths vs "CONTROL" Machine
    var unitSystem by remember { mutableStateOf("MM") } // "MM" vs "INCH"
    var zZeroLocation by remember { mutableStateOf("TOP") } // "TOP" Surface vs "BED" Table
    var datumOriginIndex by remember { mutableStateOf(2) } // 0: BL, 1: TL, 2: Center, 3: BR, 4: TR
    var cornerTreatmentType by remember { mutableStateOf("SQUARE") } // SQUARE, ROUNDED, DOGBONE, TBONE
    var vectorOffsetDistance by remember { mutableStateOf("3.0") }
    var pocketRasterRotation by remember { mutableStateOf("0.0") }
    var pigmentInfillColor by remember { mutableStateOf("NATURAL") } // NATURAL, BLACK, GOLD, BLUE, RED
    var spindleAudioVolume by remember { mutableStateOf(0.5f) }
    
    // CAM Multi-Operations list
    val camOperationsList = remember { mutableStateListOf<CAMOperation>() }
    
    // CNC Offline Controller & Jogger States
    var machineConnectedState by remember { mutableStateOf("DISCONNECTED") } // DISCONNECTED, CONNECTING, CONNECTED
    var jogStepSize by remember { mutableStateOf(10.0f) } // 0.1, 1.0, 10.0, 50.0
    var currentXCoord by remember { mutableStateOf(0.00f) }
    var currentYCoord by remember { mutableStateOf(0.00f) }
    var currentZCoord by remember { mutableStateOf(5.00f) }
    var maxFeedrateOverride by remember { mutableStateOf(100) }
    var spindleRpmOverride by remember { mutableStateOf(100) }
    val consoleTerminalOutput = remember { mutableStateListOf<String>(
        "Mobile Serial Link Console active.",
        "[System] CNC GRBL/FluidNC protocol active.",
        "[Status] Click 'CONNECT' to link electronic controller."
    ) }
    var consoleCommandInput by remember { mutableStateOf("") }
    
    // Dynamic vector entities list
    val vectorsList = remember { mutableStateListOf<AspireVector>() }
    var selectedVectorId by remember { mutableStateOf<String?>(null) }

    // --- 3D MODELLING RELIEF & TRACING STATES (VECTRIC ASPIRE STYLE) ---
    var selected3DModelPreset by remember { mutableStateOf("NONE") } // NONE, ROSETTE, EAGLE, ROSE, CUSTOM_STL
    var stlFileName by remember { mutableStateOf<String?>(null) }
    var reliefDepthGrid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var reliefZMax by remember { mutableStateOf("6.0") }
    var reliefScale by remember { mutableStateOf("1.0") }
    var reliefCombineMode by remember { mutableStateOf("ADD") } // ADD, SUBTRACT
    var reliefCx by remember { mutableStateOf("0.0") }
    var reliefCy by remember { mutableStateOf("0.0") }
    
    // Advanced 3D features
    var reliefSmoothingPasses by remember { mutableStateOf(0) } // 0: None, 1: Low, 2: Med, 3: High
    var reliefNoiseRoughness by remember { mutableStateOf(0.0f) } // 0.0f to 0.15f
    var reliefContrast by remember { mutableStateOf(1.0f) } // 0.5f to 2.0f
    var reliefRasterAngle by remember { mutableStateOf("0.0") } // "0.0", "45.0", "90.0"
    
    var traceThreshold by remember { mutableStateOf(0.45f) }
    var importedTraceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Dynamic 3D relief grid calculation effect
    LaunchedEffect(
        selected3DModelPreset, reliefScale, reliefCx, reliefCy, reliefCombineMode,
        reliefSmoothingPasses, reliefNoiseRoughness, reliefContrast
    ) {
        val sc = reliefScale.toFloatOrNull() ?: 1.0f
        val cxOff = reliefCx.toFloatOrNull() ?: 0f
        val cyOff = reliefCy.toFloatOrNull() ?: 0f
        val sub = reliefCombineMode == "SUBTRACT"
        
        val baseGrid = when (selected3DModelPreset) {
            "ROSETTE" -> generateRosetteGrid()
            "EAGLE" -> generateEagleGrid()
            "ROSE" -> generateRoseGrid()
            else -> reliefDepthGrid // retains custom STL data
        }
        
        if (baseGrid != null) {
            var transformed = Array(64) { FloatArray(64) }
            for (x in 0 until 64) {
                for (y in 0 until 64) {
                    val sourceX = ((x - 32) / sc + 32 - cxOff * (64f / 120f)).toInt()
                    val sourceY = ((y - 32) / sc + 32 + cyOff * (64f / 120f)).toInt()
                    
                    var valZ = 0f
                    if (sourceX in 0 until 64 && sourceY in 0 until 64) {
                        valZ = baseGrid[sourceX][sourceY]
                    }
                    if (sub) {
                        valZ = if (valZ > 0.015f) (1.0f - valZ) else 0f
                    }
                    transformed[x][y] = valZ
                }
            }

            // ADVANCED 3D FILTER 1: Noise Roughness (Wood/Stone Textured Relief Carving)
            if (reliefNoiseRoughness > 0.001f) {
                for (x in 0 until 64) {
                    for (y in 0 until 64) {
                        if (transformed[x][y] > 0.012f) {
                            val pseudoNoise = (Math.sin(x * 2.5 + y * 1.8) * Math.cos(x * 1.1 - y * 1.4)).toFloat() * reliefNoiseRoughness
                            transformed[x][y] = (transformed[x][y] + pseudoNoise).coerceIn(0.01f, 1.0f)
                        }
                    }
                }
            }

            // ADVANCED 3D FILTER 2: Material Smoothing Filter (Box blurring filter mimicking physical tool sand finishing)
            if (reliefSmoothingPasses > 0) {
                val passes = reliefSmoothingPasses.coerceIn(1, 4)
                for (pass in 0 until passes) {
                    val smoothed = Array(64) { FloatArray(64) }
                    for (x in 0 until 64) {
                        for (y in 0 until 64) {
                            var sum = 0f
                            var count = 0
                            for (dx in -1..1) {
                                for (dy in -1..1) {
                                    val nx = x + dx
                                    val ny = y + dy
                                    if (nx in 0 until 64 && ny in 0 until 64) {
                                        sum += transformed[nx][ny]
                                        count++
                                    }
                                }
                            }
                            smoothed[x][y] = sum / count
                        }
                    }
                    transformed = smoothed
                }
            }

            // ADVANCED 3D FILTER 3: Height Contrast Adjuster
            if (Math.abs(reliefContrast - 1.0f) > 0.02f) {
                for (x in 0 until 64) {
                    for (y in 0 until 64) {
                        if (transformed[x][y] > 0.012f) {
                            val centered = transformed[x][y] - 0.4f
                            transformed[x][y] = (0.4f + centered * reliefContrast).coerceIn(0f, 1.0f)
                        }
                    }
                }
            }

            reliefDepthGrid = transformed
        } else if (selected3DModelPreset == "NONE") {
            reliefDepthGrid = null
        }
    }

    // Launchers for choosing files
    val dxfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    val parsed = com.example.utils.DXFParser.parse(inputStream)
                    if (parsed.isNotEmpty()) {
                        parsed.forEachIndexed { sIdx, path ->
                            vectorsList.add(
                                AspireVector(
                                    id = "dxf_import_${System.currentTimeMillis()}_$sIdx",
                                    type = "Polyline",
                                    name = "DXF Path ${sIdx + 1}",
                                    rawPoints = path,
                                    cx = 0f,
                                    cy = 0f
                                )
                            )
                        }
                        android.widget.Toast.makeText(context, "DXF CAD imported: ${parsed.size} paths added", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "No vector paths found in DXF file!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "DXF Import Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val traceImagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    val decoded = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (decoded != null) {
                        importedTraceBitmap = decoded
                        android.widget.Toast.makeText(context, "Tracing image loaded! Adjust slider and click 'Apply Trace'", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to decode image pixels!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Image Load Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val stlPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    val grid = parseStlToDepthMap(inputStream, 64, 64)
                    reliefDepthGrid = grid
                    selected3DModelPreset = "CUSTOM_STL"
                    stlFileName = "Custom Loaded STL Mesh"
                    android.widget.Toast.makeText(context, "3D STL Mesh sliced & projected into relief!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "STL Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Shape Draft Fields
    var cxInput by remember { mutableStateOf("0") }
    var cyInput by remember { mutableStateOf("0") }
    var rInput by remember { mutableStateOf("25") }
    var wInput by remember { mutableStateOf("60") }
    var hInput by remember { mutableStateOf("40") }
    var cornerRadiusInput by remember { mutableStateOf("5") }
    var polySidesInput by remember { mutableStateOf("6") }
    var starPointsInput by remember { mutableStateOf("5") }
    var starRatioInput by remember { mutableStateOf("50") } // 50%
    var textStrInput by remember { mutableStateOf("ASPIRE CNC") }
    var textHeightInput by remember { mutableStateOf("15") }

    // Arc input fields
    var arcStartXInput by remember { mutableStateOf("-30") }
    var arcStartYInput by remember { mutableStateOf("0") }
    var arcEndXInput by remember { mutableStateOf("30") }
    var arcEndYInput by remember { mutableStateOf("0") }
    var arcBulgeInput by remember { mutableStateOf("12") }

    // Polyline local coordinate draft points list
    val draftedPolylineNodes = remember { mutableStateListOf<Offset>() }

    // CAM parameters
    var toolpathType by remember { mutableStateOf("PROFILE") } // PROFILE, POCKET, DRILL
    var cutDepth by remember { mutableStateOf("-3.0") } // Z depth
    var stepDown by remember { mutableStateOf("1.0") } // Z pass depth
    var spindleRPM by remember { mutableStateOf("16000") } // RPM
    var cuttingSide by remember { mutableStateOf("OUTSIDE") } // OUTSIDE, INSIDE, ON
    var selectedBitName by remember { mutableStateOf("End Mill 1/8\" (3.175mm)") }
    var cuttingDirection by remember { mutableStateOf("CLIMB") } // CLIMB vs CONVENTIONAL
    var enableHoldingTabs by remember { mutableStateOf(false) }
    var holdingTabsCount by remember { mutableStateOf(4) }
    var materialType by remember { mutableStateOf("WALNUT") } // OAK, WALNUT, CHERRY, PINE, BRASS, SLATE
    var activePostProcessor by remember { mutableStateOf("Vectric WinCNC (*.tap)") }
    
    // Calculation outputs
    var isCalculated by remember { mutableStateOf(false) }
    var estimatedTimeStr by remember { mutableStateOf("00:00") }
    var totalGCodeLines by remember { mutableStateOf(0) }
    var activeGCodePreview by remember { mutableStateOf("") }
    
    // Simulation triggers
    var isSimPlaying by remember { mutableStateOf(false) }
    var simTimeProg by remember { mutableStateOf(0f) } // 0f to 1f

    // Standard pre-populating with a gorgeous "Vectric Commemorative Shield" template
    LaunchedEffect(Unit) {
        if (vectorsList.isEmpty()) {
            // Preset 1: Outer Rectangle Board Border
            vectorsList.add(
                AspireVector(
                    id = "v_border",
                    type = "Rectangle",
                    name = "Outer Border",
                    cx = 0f,
                    cy = 0f,
                    width = 120f,
                    height = 120f,
                    cornerRadius = 8f,
                    rawPoints = createRectanglePoints(0f, 0f, 120f, 120f, 8f)
                )
            )
            // Preset 2: Engraved badge outer star
            vectorsList.add(
                AspireVector(
                    id = "v_shield",
                    type = "Star",
                    name = "Medal Star",
                    cx = 0f,
                    cy = 0f,
                    points = 8,
                    radius = 35f,
                    innerRatio = 0.6f,
                    rawPoints = createStarPoints(0f, 0f, 8, 35f, 35f * 0.6f)
                )
            )
            // Preset 3: Center Core Carving
            vectorsList.add(
                AspireVector(
                    id = "v_core",
                    type = "Circle",
                    name = "Inner Circle",
                    cx = 0f,
                    cy = 0f,
                    radius = 16f,
                    rawPoints = createCirclePoints(0f, 0f, 16f)
                )
            )
            // Preset 4: Text engraving
            vectorsList.add(
                AspireVector(
                    id = "v_text",
                    type = "Text",
                    name = "Center Text",
                    cx = 0f,
                    cy = -4f,
                    textStr = "CNC",
                    textHeight = 10f,
                    rawPoints = createTextVectorPoints("CNC", 0f, -4f, 10f)
                )
            )
        }
    }

    // Interactive simulation thread
    LaunchedEffect(isSimPlaying) {
        if (isSimPlaying) {
            simTimeProg = 0f
            while (simTimeProg < 1.0f && isSimPlaying) {
                delay(30)
                simTimeProg += 0.008f
            }
            isSimPlaying = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- PAGE HEADER BLOCK ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Vectric",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tDesc,
                        color = Color(0xFFB0BEC5),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // --- DUAL COLUMN / FLEX CAD-CAM SYSTEM ---
        ResponsiveFlexLayout(spacing = 16.dp) {
            // ================== SIDEBAR TOOLBOX (LEFT) ==================
            FlexItem(expandedWeight = 0.45f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // --- VECTRIC WORKSPACE SEPARATOR TABS ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C101B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { activeWorkspaceTab = "CAD" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeWorkspaceTab == "CAD") PrimaryCyan else Color.Transparent,
                                contentColor = if (activeWorkspaceTab == "CAD") Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(34.dp).testTag("tab_drawing_cad"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Create, null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "id") "CAD" else "CAD Drafting", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { activeWorkspaceTab = "CAM" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeWorkspaceTab == "CAM") SpindleGold else Color.Transparent,
                                contentColor = if (activeWorkspaceTab == "CAM") Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1.5f).height(34.dp).testTag("tab_toolpath_cam"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Build, null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "id") "CAM" else "CAM Toolpaths", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { activeWorkspaceTab = "CONTROL" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeWorkspaceTab == "CONTROL") Color(0xFF4CAF50) else Color.Transparent,
                                contentColor = if (activeWorkspaceTab == "CONTROL") Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1.5f).height(34.dp).testTag("tab_machine_control"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Home, null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "id") "Kontrol" else "CNC Jogger", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (activeWorkspaceTab == "CAD") {
                        // ================== DRAWING PANEL (CAD) ==================
                        // 1. Job Setup Material Sizing
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, null, tint = SpindleGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "id") "1. DIMENSI PAPAN & UNIT" else "1. MATERIAL SIZE & UNITS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Width X (${unitSystem.lowercase()})", fontSize = 8.sp, color = UnselectedGrey)
                                        OutlinedTextField(
                                            value = viewModel.workspaceWidth.toString(),
                                            onValueChange = { newVal -> newVal.toFloatOrNull()?.let { viewModel.workspaceWidth = it } },
                                            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.height(44.dp).testTag("input_job_width")
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Height Y (${unitSystem.lowercase()})", fontSize = 8.sp, color = UnselectedGrey)
                                        OutlinedTextField(
                                            value = viewModel.workspaceHeight.toString(),
                                            onValueChange = { newVal -> newVal.toFloatOrNull()?.let { viewModel.workspaceHeight = it } },
                                            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.height(44.dp).testTag("input_job_height")
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Thick Z (${unitSystem.lowercase()})", fontSize = 8.sp, color = UnselectedGrey)
                                        OutlinedTextField(
                                            value = viewModel.materialThickness.toString(),
                                            onValueChange = { newVal -> newVal.toFloatOrNull()?.let { viewModel.materialThickness = it } },
                                            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.height(44.dp).testTag("input_job_thickness")
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Unit System:", fontSize = 9.sp, color = TextLight)
                                    Row {
                                        listOf("MM", "INCH").forEach { u ->
                                            val active = unitSystem == u
                                            Button(
                                                onClick = { unitSystem = u },
                                                colors = ButtonDefaults.buttonColors(containerColor = if (active) PrimaryCyan else SlateDark, contentColor = if (active) Color.Black else Color.White),
                                                shape = RoundedCornerShape(4.dp), modifier = Modifier.height(24.dp).padding(horizontal = 2.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Text(u, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Interactive Datum Origin choosing (BL, TL, Center, etc.)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (lang == "id") "Titik Datum:" else "Datum Origin:", fontSize = 9.sp, color = TextLight)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("B-Left", "Center", "T-Left").forEachIndexed { index, label ->
                                            val act = datumOriginIndex == index
                                            Box(
                                                modifier = Modifier
                                                    .background(if (act) SpindleGold else SlateDark, RoundedCornerShape(4.dp))
                                                    .clickable { datumOriginIndex = index }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(label, fontSize = 8.sp, color = if (act) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Vector shape drawer panel
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "id") "2. BUAT VEKTOR BARU" else "2. CREATE GEOMETRIC VECTORS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val shapes = listOf(
                                        "CIRCLE" to "Circle", "RECTANGLE" to "Rect", "STAR" to "Star",
                                        "POLYGON" to "Poly", "OVAL" to "Oval", "ARC" to "Arc", "TEXT" to "Text", "POLYLINE" to "Polyline"
                                    )
                                    shapes.forEach { (code, label) ->
                                        val active = selectedCADTool == code
                                        Button(
                                            onClick = { selectedCADTool = if (active) "NONE" else code; draftedPolylineNodes.clear() },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (active) PrimaryCyan else SlateDark, contentColor = if (active) Color.Black else Color.White),
                                            shape = RoundedCornerShape(4.dp), modifier = Modifier.height(26.dp).testTag("btn_draw_$code"),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (selectedCADTool != "NONE") {
                                    Column(
                                        modifier = Modifier.background(Color(0xFF0F1424), RoundedCornerShape(6.dp)).padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Setup Tool: $selectedCADTool", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                        
                                        when (selectedCADTool) {
                                            "CIRCLE" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = cxInput, onValueChange = { cxInput = it }, label = { Text("CX", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = cyInput, onValueChange = { cyInput = it }, label = { Text("CY", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = rInput, onValueChange = { rInput = it }, label = { Text("Rad R", fontSize = 8.sp) }, modifier = Modifier.weight(1.2f).height(44.dp))
                                                }
                                            }
                                            "OVAL" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = cxInput, onValueChange = { cxInput = it }, label = { Text("CX", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = cyInput, onValueChange = { cyInput = it }, label = { Text("CY", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = wInput, onValueChange = { wInput = it }, label = { Text("Rad X", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = hInput, onValueChange = { hInput = it }, label = { Text("Rad Y", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                            }
                                            "ARC" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = arcStartXInput, onValueChange = { arcStartXInput = it }, label = { Text("X-Start", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = arcStartYInput, onValueChange = { arcStartYInput = it }, label = { Text("Y-Start", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = arcEndXInput, onValueChange = { arcEndXInput = it }, label = { Text("X-End", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = arcEndYInput, onValueChange = { arcEndYInput = it }, label = { Text("Y-End", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = arcBulgeInput, onValueChange = { arcBulgeInput = it }, label = { Text("Bulge", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                            }
                                            "RECTANGLE" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = cxInput, onValueChange = { cxInput = it }, label = { Text("CX", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = cyInput, onValueChange = { cyInput = it }, label = { Text("CY", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = wInput, onValueChange = { wInput = it }, label = { Text("W-Width", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = hInput, onValueChange = { hInput = it }, label = { Text("H-Height", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = cornerRadiusInput, onValueChange = { cornerRadiusInput = it }, label = { Text("Radius", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Corner Style:", fontSize = 8.sp, color = TextLight)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        listOf("SQUARE", "ROUNDED", "DOGBONE", "TBONE").forEach { style ->
                                                            val sSel = cornerTreatmentType == style
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(if (sSel) SpindleGold else SlateDark, RoundedCornerShape(3.dp))
                                                                    .clickable { cornerTreatmentType = style }
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(style, fontSize = 7.sp, color = if (sSel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            "STAR" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = starPointsInput, onValueChange = { starPointsInput = it }, label = { Text("Points", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = rInput, onValueChange = { rInput = it }, label = { Text("Outer R", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = starRatioInput, onValueChange = { starRatioInput = it }, label = { Text("Ratio %", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                            }
                                            "POLYGON" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = polySidesInput, onValueChange = { polySidesInput = it }, label = { Text("Sides", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = rInput, onValueChange = { rInput = it }, label = { Text("Radius", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                }
                                            }
                                            "TEXT" -> {
                                                OutlinedTextField(value = textStrInput, onValueChange = { textStrInput = it }, label = { Text("Text content", fontSize = 8.sp) }, modifier = Modifier.fillMaxWidth().height(44.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedTextField(value = cxInput, onValueChange = { cxInput = it }, label = { Text("X", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = cyInput, onValueChange = { cyInput = it }, label = { Text("Y", fontSize = 8.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                                    OutlinedTextField(value = textHeightInput, onValueChange = { textHeightInput = it }, label = { Text("Height (mm)", fontSize = 8.sp) }, modifier = Modifier.weight(1.5f).height(44.dp))
                                                }
                                            }
                                            "POLYLINE" -> {
                                                Text("Tap canvas coordinate nodes to sketch profile segments directly.", fontSize = 8.sp, color = PrimaryCyan)
                                                if (draftedPolylineNodes.isNotEmpty()) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Nodes: ${draftedPolylineNodes.size}", fontSize = 9.sp, color = Color.White)
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Button(onClick = { draftedPolylineNodes.clear() }, colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson), modifier = Modifier.height(24.dp)) {
                                                                Text("Clear", fontSize = 8.sp)
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    if (draftedPolylineNodes.size >= 2) {
                                                                        vectorsList.add(AspireVector(id = "vec_poly_${System.currentTimeMillis()}", type = "Polyline", name = "Polyline ${vectorsList.size}", rawPoints = draftedPolylineNodes.toList()))
                                                                        draftedPolylineNodes.clear()
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.height(24.dp)
                                                            ) {
                                                                Text("Commit", fontSize = 8.sp, color = Color.Black)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (selectedCADTool != "POLYLINE") {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val cx = cxInput.toFloatOrNull() ?: 0f
                                                        val cy = cyInput.toFloatOrNull() ?: 0f
                                                        val r = rInput.toFloatOrNull() ?: 20f
                                                        val w = wInput.toFloatOrNull() ?: 50f
                                                        val h = hInput.toFloatOrNull() ?: 30f
                                                        val cr = cornerRadiusInput.toFloatOrNull() ?: 0f
                                                        val sides = polySidesInput.toIntOrNull() ?: 6
                                                        val starP = starPointsInput.toIntOrNull() ?: 5
                                                        val starRatio = (starRatioInput.toFloatOrNull() ?: 50f) / 100f

                                                        val pts = when (selectedCADTool) {
                                                            "CIRCLE" -> createCirclePoints(cx, cy, r)
                                                            "RECTANGLE" -> createRectanglePointsAdvanced(cx, cy, w, h, cornerTreatmentType, cr, 1.587f)
                                                            "STAR" -> createStarPoints(cx, cy, starP, r, r * starRatio)
                                                            "POLYGON" -> createPolygonPoints(cx, cy, r, sides)
                                                            "OVAL" -> {
                                                                val ptList = mutableListOf<Offset>()
                                                                for (i in 0..40) {
                                                                    val a = (i * 2 * Math.PI / 40.0).toFloat()
                                                                    ptList.add(Offset(cx + w * Math.cos(a.toDouble()).toFloat(), cy + h * Math.sin(a.toDouble()).toFloat()))
                                                                }
                                                                ptList
                                                            }
                                                            "ARC" -> {
                                                                val ax = arcStartXInput.toFloatOrNull() ?: -20f
                                                                val ay = arcStartYInput.toFloatOrNull() ?: 0f
                                                                val bx = arcEndXInput.toFloatOrNull() ?: 20f
                                                                val by = arcEndYInput.toFloatOrNull() ?: 0f
                                                                val bulge = arcBulgeInput.toFloatOrNull() ?: 10f
                                                                val mx = (ax + bx) / 2f
                                                                val my = (ay + by) / 2f
                                                                val dx = bx - ax
                                                                val dy = by - ay
                                                                val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                                                val px = -dy / d * bulge
                                                                val py = dx / d * bulge
                                                                val ptList = mutableListOf<Offset>()
                                                                for (i in 0..20) {
                                                                    val t = i / 20f
                                                                    val t1 = 1f - t
                                                                    ptList.add(Offset(t1*t1*ax + 2*t1*t*(mx+px) + t*t*bx, t1*t1*ay + 2*t1*t*(my+py) + t*t*by))
                                                                }
                                                                ptList
                                                            }
                                                            "TEXT" -> createTextVectorPoints(textStrInput, cx, cy, textHeightInput.toFloatOrNull() ?: 12f)
                                                            else -> emptyList()
                                                        }

                                                        if (pts.isNotEmpty()) {
                                                            vectorsList.add(
                                                                AspireVector(
                                                                    id = "v_${System.currentTimeMillis()}", type = selectedCADTool, name = "$selectedCADTool ${vectorsList.size}",
                                                                    cx = cx, cy = cy, radius = r, width = w, height = h, cornerRadius = cr, sides = sides, points = starP, innerRatio = starRatio,
                                                                    textStr = textStrInput, textHeight = textHeightInput.toFloatOrNull() ?: 12f, rawPoints = pts
                                                                )
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "CAD Setup Fail", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                                shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().height(32.dp).testTag("btn_apply_cad_vector")
                                            ) {
                                                Text(if (lang == "id") "APPLY / DRAW VECTOR" else "APPLY / DRAW VECTOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateDark)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Selected Vector Operator Matrix
                        val activeV = vectorsList.find { it.id == selectedVectorId }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, if (activeV != null) SpindleGold.copy(alpha = 0.5f) else BorderCyan.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, null, tint = SpindleGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "id") "2B. OPERATOR VEKTOR CAD" else "2B. VECTOR OPERATORS (TRANSFORM)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                }

                                if (activeV == null) {
                                    Text("Click any 2D vector on the blueprint to unlock PC-grade structural operations (Contour Offsets, Transforms, Mirroring, Align).", fontSize = 8.sp, color = UnselectedGrey, lineHeight = 11.sp)
                                } else {
                                    Text("Selected: [${activeV.type.uppercase()}] ${activeV.name}", fontSize = 9.sp, color = PrimaryCyan, fontWeight = FontWeight.Bold)
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Transforms
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Scale Sizes:", fontSize = 8.sp, color = UnselectedGrey)
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                listOf("0.9x", "1.1x").forEach { factor ->
                                                    Box(modifier = Modifier.background(SlateDark, RoundedCornerShape(3.dp)).clickable {
                                                        val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                        if (idx != -1) vectorsList[idx] = scaleVector(vectorsList[idx], if (factor =="0.9x") 0.9f else 1.1f)
                                                    }.padding(horizontal = 6.dp, vertical = 3.dp)) { Text(factor, fontSize = 8.sp, color = Color.White) }
                                                }
                                            }
                                            Text("Mirror Tools:", fontSize = 8.sp, color = UnselectedGrey)
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Button(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = mirrorVector(vectorsList[idx], true)
                                                }, colors = ButtonDefaults.buttonColors(containerColor = SlateDark), shape = RoundedCornerShape(3.dp), modifier = Modifier.height(20.dp).weight(1f), contentPadding = PaddingValues(0.dp)) {
                                                    Text("Flip-H", fontSize = 7.sp)
                                                }
                                                Button(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = mirrorVector(vectorsList[idx], false)
                                                }, colors = ButtonDefaults.buttonColors(containerColor = SlateDark), shape = RoundedCornerShape(3.dp), modifier = Modifier.height(20.dp).weight(1f), contentPadding = PaddingValues(0.dp)) {
                                                    Text("Flip-V", fontSize = 7.sp)
                                                }
                                            }
                                        }

                                        // Translation Move
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Move & Align Shift:", fontSize = 8.sp, color = UnselectedGrey, modifier = Modifier.align(Alignment.Start))
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = moveVector(vectorsList[idx], -5f, 0f)
                                                }, modifier = Modifier.size(22.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("◀", fontSize = 8.sp, color = SpindleGold) }
                                                IconButton(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = centerVector(vectorsList[idx])
                                                }, modifier = Modifier.size(22.dp).background(PrimaryCyan, RoundedCornerShape(3.dp))) { Text("☉", fontSize = 8.sp, color = Color.Black) }
                                                IconButton(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = moveVector(vectorsList[idx], 5f, 0f)
                                                }, modifier = Modifier.size(22.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("▶", fontSize = 8.sp, color = SpindleGold) }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = moveVector(vectorsList[idx], 0f, 5f)
                                                }, modifier = Modifier.size(22.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("▲", fontSize = 8.sp, color = SpindleGold) }
                                                IconButton(onClick = {
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) vectorsList[idx] = moveVector(vectorsList[idx], 0f, -5f)
                                                }, modifier = Modifier.size(22.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("▼", fontSize = 8.sp, color = SpindleGold) }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Divider(color = Color(0xFF1E293B))

                                    // Contour Offsetting
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Professional Geometric Offset Contour:", fontSize = 8.sp, color = SpindleGold, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedTextField(
                                                value = vectorOffsetDistance,
                                                onValueChange = { vectorOffsetDistance = it },
                                                label = { Text("Offset (mm)", fontSize = 7.sp) },
                                                modifier = Modifier.weight(1f).height(42.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    val d = vectorOffsetDistance.toFloatOrNull() ?: 3f
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) {
                                                        val baseVec = vectorsList[idx]
                                                        val insetPts = offsetClosedPoints(baseVec.rawPoints, -d)
                                                        if (insetPts.isNotEmpty()) {
                                                            vectorsList.add(baseVec.copy(id = "v_offset_${System.currentTimeMillis()}", name = "${baseVec.name}_offset_in", rawPoints = insetPts))
                                                            Toast.makeText(context, "Inward Offset Applied", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SlateDark), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(28.dp).weight(1f), contentPadding = PaddingValues(0.dp)
                                            ) { Text("Offset IN", fontSize = 8.sp, color = PrimaryCyan) }
                                            Button(
                                                onClick = {
                                                    val d = vectorOffsetDistance.toFloatOrNull() ?: 3f
                                                    val idx = vectorsList.indexOfFirst { it.id == activeV.id }
                                                    if (idx != -1) {
                                                        val baseVec = vectorsList[idx]
                                                        val outsetPts = offsetClosedPoints(baseVec.rawPoints, d)
                                                        if (outsetPts.isNotEmpty()) {
                                                            vectorsList.add(baseVec.copy(id = "v_offset_${System.currentTimeMillis()}", name = "${baseVec.name}_offset_out", rawPoints = outsetPts))
                                                            Toast.makeText(context, "Outward Offset Applied", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SlateDark), shape = RoundedCornerShape(4.dp), modifier = Modifier.height(28.dp).weight(1f), contentPadding = PaddingValues(0.dp)
                                            ) { Text("Offset OUT", fontSize = 8.sp, color = PrimaryCyan) }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                val duplicated = activeV.copy(id = "v_copy_${System.currentTimeMillis()}", name = "${activeV.name}_copy", cx = activeV.cx + 5f, cy = activeV.cy - 5f, rawPoints = activeV.rawPoints.map { Offset(it.x + 5f, it.y - 5f) })
                                                vectorsList.add(duplicated)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SlateDark), modifier = Modifier.height(26.dp).weight(1f)
                                        ) { Text("Duplicate", fontSize = 8.sp) }
                                        Button(
                                            onClick = {
                                                vectorsList.remove(activeV)
                                                selectedVectorId = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson), modifier = Modifier.height(26.dp).weight(1f)
                                        ) { Text("Delete Entity", fontSize = 8.sp, color = Color.White) }
                                    }
                                }
                            }
                        }

                        // ================== CARD 2C. CAD FILE IMPORT & AUTO-TRACE ==================
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, null, tint = SpindleGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "id") "2C. IMPOR FILE & TRASING FOTO" else "2C. FILE IMPORT & IMAGE TRACING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = { dxfPickerLauncher.launch("application/octet-stream") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(30.dp).weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(if (lang == "id") "Impor DXF" else "Import DXF", fontSize = 8.sp, color = PrimaryCyan, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { traceImagePickerLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(30.dp).weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(if (lang == "id") "Pilih Trase PNG" else "Load Trace PNG", fontSize = 8.sp, color = PrimaryCyan, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (importedTraceBitmap != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF090D1A)),
                                        border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(if (lang == "id") "PENGATURAN OUTLINE TRASING:" else "PHOTO TRACING CONTROLS:", fontSize = 8.sp, color = SpindleGold, fontWeight = FontWeight.Bold)
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Batas: ${String.format("%.2f", traceThreshold)}", fontSize = 8.sp, color = Color.White)
                                                Slider(
                                                    value = traceThreshold,
                                                    onValueChange = { traceThreshold = it },
                                                    valueRange = 0.1f..0.9f,
                                                    modifier = Modifier.weight(1f).height(20.dp)
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        importedTraceBitmap?.let { bmp ->
                                                            val traced = traceBitmapContours(bmp, traceThreshold)
                                                            if (traced.isNotEmpty()) {
                                                                traced.forEachIndexed { idx, p ->
                                                                    vectorsList.add(
                                                                        AspireVector(
                                                                            id = "trace_${System.currentTimeMillis()}_$idx",
                                                                            type = "Polyline",
                                                                            name = "Traced Contours $idx",
                                                                            rawPoints = p
                                                                        )
                                                                    )
                                                                }
                                                                android.widget.Toast.makeText(context, "${traced.size} outline paths generated!", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                android.widget.Toast.makeText(context, "No solid regions found, adjust barrier threshold!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                                    modifier = Modifier.height(26.dp).weight(1f),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Trase Outline ke CAD", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                                }

                                                Button(
                                                    onClick = { importedTraceBitmap = null },
                                                    colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson),
                                                    modifier = Modifier.height(26.dp).weight(0.4f),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Batal", fontSize = 8.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                             }
                         }

                        // ================== CARD 3. MODELING 3D RELIEF ==================
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, null, tint = SpindleGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "id") "3. MODELING 3D RELIEF ORNAMEN" else "3. 3D RELIEF ORNAMENT MODELING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                }

                                Text(if (lang == "id") "Pilih desain ornamen 3D untuk diukir di papan:" else "Select 3D relief model to carve on surface:", fontSize = 8.sp, color = UnselectedGrey)
                                
                                // Selection layout
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("NONE", "ROSETTE", "EAGLE", "ROSE").forEach { preset ->
                                        val active = selected3DModelPreset == preset
                                        Box(
                                            modifier = Modifier
                                                .background(if (active) SpindleGold else SlateDark, RoundedCornerShape(3.dp))
                                                .clickable { selected3DModelPreset = preset }
                                                .padding(horizontal = 4.dp, vertical = 3.dp).weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when(preset) {
                                                    "NONE" -> "Flat"
                                                    "ROSETTE" -> "Rosette"
                                                    "EAGLE" -> "Eagle"
                                                    else -> "Rose"
                                                },
                                                fontSize = 8.sp,
                                                color = if (active) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { stlPickerLauncher.launch("application/octet-stream") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(26.dp).weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(if (selected3DModelPreset == "CUSTOM_STL") "STL: loaded" else "Import 3D STL file", fontSize = 8.sp, color = PrimaryCyan)
                                    }
                                    
                                    if (selected3DModelPreset == "CUSTOM_STL") {
                                        IconButton(onClick = { selected3DModelPreset = "NONE" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, null, tint = LaserCrimson)
                                        }
                                    }
                                }

                                if (selected3DModelPreset != "NONE") {
                                    Divider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 2.dp))
                                    
                                    Text("Combining Style & Depth:", fontSize = 8.sp, color = UnselectedGrey)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("ADD", "SUBTRACT").forEach { mode ->
                                            val active = reliefCombineMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .background(if (active) PrimaryCyan else SlateDark, RoundedCornerShape(3.dp))
                                                    .clickable { reliefCombineMode = mode }
                                                    .padding(horizontal = 4.dp, vertical = 3.dp).weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (mode == "ADD") "Timbul (ADD)" else "Ukiran (SUB)", fontSize = 8.sp, color = if (active) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = reliefZMax,
                                            onValueChange = { reliefZMax = it },
                                            label = { Text("Z depth (mm)", fontSize = 7.sp) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        )
                                        OutlinedTextField(
                                            value = reliefScale,
                                            onValueChange = { reliefScale = it },
                                            label = { Text("Dimens. Scale", fontSize = 7.sp) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = reliefCx,
                                            onValueChange = { reliefCx = it },
                                            label = { Text("Shift X Offset", fontSize = 7.sp) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        )
                                        OutlinedTextField(
                                            value = reliefCy,
                                            onValueChange = { reliefCy = it },
                                            label = { Text("Shift Y Offset", fontSize = 7.sp) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        )
                                    }

                                    // --- NEW ADVANCED 3D EDITING PARAMETERS ---
                                    Divider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("ADVANCED 3D EDITING FILTERS:", fontSize = 9.sp, color = SpindleGold, fontWeight = FontWeight.Bold)
                                    
                                    // 1. Smoothing filter chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Smooth Filter:", fontSize = 8.sp, color = Color.White)
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            listOf(0 to "Raw", 1 to "Low", 2 to "Med", 3 to "High").forEach { (idx, label) ->
                                                val active = reliefSmoothingPasses == idx
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) PrimaryCyan else SlateDark, RoundedCornerShape(2.dp))
                                                        .clickable { reliefSmoothingPasses = idx }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 7.sp, color = if (active) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 2. Texture noise level chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Carving Texture:", fontSize = 8.sp, color = Color.White)
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            listOf(0.0f to "Flat", 0.03f to "Sand", 0.06f to "Wood", 0.11f to "Stone").forEach { (valNoi, label) ->
                                                val active = Math.abs(reliefNoiseRoughness - valNoi) < 0.015f
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) SpindleGold else SlateDark, RoundedCornerShape(2.dp))
                                                        .clickable { reliefNoiseRoughness = valNoi }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 7.sp, color = if (active) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 3. Height Gain Contrast slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Contrast (Gain): ${String.format("%.1f", reliefContrast)}x", fontSize = 8.sp, color = Color.White)
                                        Slider(
                                            value = reliefContrast,
                                            onValueChange = { reliefContrast = it },
                                            valueRange = 0.5f..2.0f,
                                            modifier = Modifier.width(100.dp).height(12.dp)
                                        )
                                    }

                                    // 4. Scanning Raster Angle selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("3D G-code Angle:", fontSize = 8.sp, color = Color.White)
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            listOf("0.0" to "0° Horiz", "45.0" to "45° Diag", "90.0" to "90° Vert").forEach { (angStr, label) ->
                                                val active = reliefRasterAngle == angStr
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) PrimaryCyan else SlateDark, RoundedCornerShape(2.dp))
                                                        .clickable { reliefRasterAngle = angStr }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 7.sp, color = if (active) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Passive Vector inventory scroll-card list
                        if (vectorsList.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF070B16)),
                                border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("DESIGN VECTOR INVENTORY:", fontSize = 8.sp, color = UnselectedGrey, fontWeight = FontWeight.Bold)
                                    LazyColumn {
                                        items(vectorsList.size) { i ->
                                            val v = vectorsList[i]
                                            val sel = selectedVectorId == v.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 1.dp)
                                                    .background(if (sel) PrimaryCyan.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable { selectedVectorId = if (sel) null else v.id }
                                                    .padding(4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("[${v.type}] ${v.name}", fontSize = 9.sp, color = if (sel) PrimaryCyan else Color.White)
                                                IconButton(onClick = { vectorsList.removeAt(i); if (sel) selectedVectorId = null }, modifier = Modifier.size(16.dp)) {
                                                    Icon(Icons.Default.Delete, null, tint = LaserCrimson, modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeWorkspaceTab == "CAM") {
                        // ================== TOOLPATHS PANEL (CAM) ==================
                        // 1. CNC Cutter Tool tooling selection
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, null, tint = SpindleGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("1. ENDMILL CUTTER TOOL SELECTOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateDark, RoundedCornerShape(4.dp))
                                        .clickable {
                                            selectedBitName = when (selectedBitName) {
                                                "End Mill 1/8\" (3.175mm)" -> "End Mill 1/4\" (6.35mm)"
                                                "End Mill 1/4\" (6.35mm)" -> "V-Bit 60° (12.7mm)"
                                                "V-Bit 60° (12.7mm)" -> "Ball Nose 2mm"
                                                else -> "End Mill 1/8\" (3.175mm)"
                                            }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedBitName, fontSize = 10.sp, color = SpindleGold, fontWeight = FontWeight.Bold)
                                    Text("(TOGGLE BIT)", fontSize = 8.sp, color = PrimaryCyan)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    listOf("PROFILE", "POCKET", "DRILL", "3D_ROUGH", "3D_FINISH").forEach { strategy ->
                                        val active = toolpathType == strategy
                                        Button(
                                            onClick = { toolpathType = strategy },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (active) SpindleGold else SlateDark, contentColor = if (active) Color.Black else Color.White),
                                            shape = RoundedCornerShape(4.dp), modifier = Modifier.height(26.dp).weight(1f),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = when (strategy) {
                                                    "3D_ROUGH" -> "3D Rgh"
                                                    "3D_FINISH" -> "3D Fin"
                                                    else -> strategy
                                                },
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(value = cutDepth, onValueChange = { cutDepth = it }, label = { Text("Cut Depth", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                    OutlinedTextField(value = stepDown, onValueChange = { stepDown = it }, label = { Text("Step Down", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(value = spindleRPM, onValueChange = { spindleRPM = it }, label = { Text("Spindle RPM", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp))
                                    OutlinedTextField(
                                        value = viewModel.feedrateCut.toString(),
                                        onValueChange = { it.toFloatOrNull()?.let { f -> viewModel.feedrateCut = f } },
                                        label = { Text("Feed mm/min", fontSize = 7.sp) }, modifier = Modifier.weight(1f).height(44.dp)
                                    )
                                }
                            }
                        }

                        // 2. Milling Strategies
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("2. MILLING PARAMETERS SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                
                                if (toolpathType == "PROFILE") {
                                    Text("Cutting Side placement:", fontSize = 8.sp, color = UnselectedGrey)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("OUTSIDE", "INSIDE", "ON").forEach { side ->
                                            val act = cuttingSide == side
                                            Box(
                                                modifier = Modifier
                                                    .background(if (act) PrimaryCyan else SlateDark, RoundedCornerShape(3.dp))
                                                    .clickable { cuttingSide = side }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp).weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(side, fontSize = 8.sp, color = if (act) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Text("Milling Feed Routing Mode:", fontSize = 8.sp, color = UnselectedGrey)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("CLIMB", "CONVENTIONAL").forEach { dir ->
                                        val act = cuttingDirection == dir
                                        Box(
                                            modifier = Modifier
                                                .background(if (act) SpindleGold else SlateDark, RoundedCornerShape(3.dp))
                                                .clickable { cuttingDirection = dir }
                                                .padding(horizontal = 8.dp, vertical = 4.dp).weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(dir, fontSize = 8.sp, color = if (act) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Interactive Bridges (Tabs)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = enableHoldingTabs, onCheckedChange = { enableHoldingTabs = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryCyan))
                                        Text("Holding Tabs (Bridges)", fontSize = 9.sp, color = Color.White)
                                    }
                                    if (enableHoldingTabs) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { holdingTabsCount = (holdingTabsCount - 1).coerceAtLeast(2) }, modifier = Modifier.size(18.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("-", fontSize = 8.sp, color = Color.White) }
                                            Text(holdingTabsCount.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                                            IconButton(onClick = { holdingTabsCount = (holdingTabsCount + 1).coerceAtMost(8) }, modifier = Modifier.size(18.dp).background(SlateDark, RoundedCornerShape(3.dp))) { Text("+", fontSize = 8.sp, color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Post Processor Exporter Setup
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("3. EXPORT POST PROCESSOR SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SpindleGold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateDark, RoundedCornerShape(4.dp))
                                        .clickable {
                                            activePostProcessor = when (activePostProcessor) {
                                                "Vectric WinCNC (*.tap)" -> "GRBL ISO CNC (*.gcode)"
                                                "GRBL ISO CNC (*.gcode)" -> "LinuxCNC EMC2 (*.ngc)"
                                                "LinuxCNC EMC2 (*.ngc)" -> "Mach3 Mill G-code (*.txt)"
                                                else -> "Vectric WinCNC (*.tap)"
                                            }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activePostProcessor, fontSize = 9.sp, color = PrimaryCyan, fontWeight = FontWeight.Bold)
                                    Text("(CHANGE)", fontSize = 7.sp, color = UnselectedGrey)
                                }

                                Text("Timber Stock Simulation Selection:", fontSize = 8.sp, color = UnselectedGrey)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf("OAK", "WALNUT", "CHERRY", "PINE", "BRASS", "SLATE").forEach { mat ->
                                        val mSelect = materialType == mat
                                        Box(
                                            modifier = Modifier
                                                .background(if (mSelect) SpindleGold else SlateDark, RoundedCornerShape(3.dp))
                                                .clickable { materialType = mat }
                                                .padding(horizontal = 4.dp, vertical = 3.dp).weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(mat, fontSize = 8.sp, color = if (mSelect) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Compile trigger
                                Button(
                                    onClick = {
                                        if (vectorsList.isEmpty() && !(toolpathType == "3D_ROUGH" || toolpathType == "3D_FINISH")) {
                                            Toast.makeText(context, "No vector drawings or 3D relief ornaments found!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val depthVal = cutDepth.toFloatOrNull() ?: -3f
                                        val passVal = stepDown.toFloatOrNull() ?: 1f
                                        val toolDiameter = when {
                                            selectedBitName.contains("1/4") -> 6.35f
                                            selectedBitName.contains("1/8") -> 3.175f
                                            selectedBitName.contains("60°") -> 3.175f
                                            selectedBitName.contains("2mm") -> 2f
                                            else -> 3.175f
                                        }

                                        val rawCompiledPaths = mutableListOf<List<Offset>>()
                                        if (toolpathType == "3D_ROUGH" || toolpathType == "3D_FINISH") {
                                            val grid = reliefDepthGrid
                                            if (grid != null) {
                                                val w = grid.size
                                                val h = grid[0].size
                                                val step = if (toolpathType == "3D_ROUGH") 4 else 2
                                                val mmRange = 120f
                                                for (yIdx in 0 until h step step) {
                                                    val rowPoints = mutableListOf<Offset>()
                                                    val range = if (yIdx % 2 == 0) (0 until w step step) else ((w - 1) downTo 0 step step)
                                                    val mmY = -(yIdx - h/2f) * (mmRange / h)
                                                    range.forEach { xIdx ->
                                                        val mmX = (xIdx - w/2f) * (mmRange / w)
                                                        rowPoints.add(Offset(mmX, mmY))
                                                    }
                                                    rawCompiledPaths.add(rowPoints)
                                                }
                                            }
                                        } else {
                                            vectorsList.forEach { v ->
                                                if (toolpathType == "POCKET") {
                                                    rawCompiledPaths.addAll(generatePocketPaths(v, toolDiameter))
                                                } else {
                                                    rawCompiledPaths.add(v.rawPoints)
                                                }
                                            }
                                        }
                                        viewModel.activePaths = rawCompiledPaths

                                        val gCodeResult = compileToAspireGCode(
                                            vectors = vectorsList.toList(),
                                            depthZ = depthVal,
                                            passZ = passVal,
                                            spindleRpm = spindleRPM.toIntOrNull() ?: 16000,
                                            feedRate = viewModel.feedrateCut,
                                            plungeRate = viewModel.feedratePlunge,
                                            safeHeight = viewModel.safeZ,
                                            toolName = selectedBitName,
                                            sideMode = cuttingSide,
                                            toolpathType = toolpathType,
                                            addHoldingTabs = enableHoldingTabs,
                                            holdingTabsCount = holdingTabsCount,
                                            cuttingDirection = cuttingDirection,
                                            reliefDepthGrid = reliefDepthGrid,
                                            reliefZMax = reliefZMax.toFloatOrNull() ?: 6.0f
                                        )

                                        viewModel.currentGCode = gCodeResult
                                        activeGCodePreview = gCodeResult
                                        totalGCodeLines = gCodeResult.lines().size
                                        viewModel.parsedSegments = GCodeParser.parseGCode(gCodeResult)
                                        viewModel.maxSegmentsToDraw = viewModel.parsedSegments.size

                                        var totalLength = 0f
                                        rawCompiledPaths.forEach { pts ->
                                            for (ptIdx in 0 until pts.size - 1) {
                                                totalLength += Math.hypot((pts[ptIdx+1].x - pts[ptIdx].x).toDouble(), (pts[ptIdx+1].y - pts[ptIdx].y).toDouble()).toFloat()
                                            }
                                        }
                                        val passes = Math.ceil((Math.abs(depthVal)/Math.abs(passVal)).toDouble()).toInt()
                                        val totalDist = totalLength * passes
                                        val seconds = ((totalDist / viewModel.feedrateCut) * 60 + passes * 4).toInt()
                                        estimatedTimeStr = String.format("%02d:%02d", seconds / 60, seconds % 60)
                                        isCalculated = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpindleGold),
                                    shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().height(38.dp).testTag("btn_calculate_toolpath")
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CALCULATE CNC TOOLPATHS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }

            // ================== PREVIEW CANVAS SPACE (RIGHT) ==================
            FlexItem(expandedWeight = 0.55f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Preview Mode Header Tab toggle
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { selectedTabPreview = "2D_CAD" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTabPreview == "2D_CAD") PrimaryCyan else Color.Transparent,
                                        contentColor = if (selectedTabPreview == "2D_CAD") SlateDark else TextLight
                                    ),
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(if (lang == "id") "2D CAD Tata Letak" else "2D Design Blueprint", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { selectedTabPreview = "3D_SIMULATION" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTabPreview == "3D_SIMULATION") PrimaryCyan else Color.Transparent,
                                        contentColor = if (selectedTabPreview == "3D_SIMULATION") SlateDark else TextLight
                                    ),
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(if (lang == "id") "3D Simulasi Material" else "3D Metal/Wood Carver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // MAIN VIEWER PORT AREA
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (selectedTabPreview == "2D_CAD") {
                                // 2D LAYOUT DESIGN BOARD Blueprint MM Grid
                                val activePointsList = vectorsList.flatMap { it.rawPoints }

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0C0F17))
                                        .pointerInput(selectedCADTool) {
                                            detectTapGestures { offset ->
                                                if (selectedCADTool == "POLYLINE") {
                                                    // Map screen pixels to local relative mm coord space
                                                    val localX = (offset.x - size.width/2f) / (size.width/180f)
                                                    val localY = -(offset.y - size.height/2f) / (size.height/180f)
                                                    draftedPolylineNodes.add(Offset(localX, localY))
                                                }
                                            }
                                        }
                                ) {
                                    val localW = size.width
                                    val localH = size.height
                                    val scaleFactor = localW / 180f // 180mm screen grid density representation

                                    // Render grid lines
                                    val segments = 18
                                    val stepSize = localW / segments
                                    for (i in 0..segments) {
                                        val lineVal = i * stepSize
                                        // X-grid
                                        drawLine(
                                            color = Color(0xFF1E283A),
                                            start = Offset(lineVal, 0f),
                                            end = Offset(lineVal, localH),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        // Y-grid
                                        drawLine(
                                            color = Color(0xFF1E283A),
                                            start = Offset(0f, lineVal),
                                            end = Offset(localW, lineVal),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    // Major Coordinate Axis Centred
                                    drawLine(
                                        color = Color(0xFF37474F),
                                        start = Offset(localW/2f, 0f),
                                        end = Offset(localW/2f, localH),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color(0xFF37474F),
                                        start = Offset(0f, localH/2f),
                                        end = Offset(localW, localH/2f),
                                        strokeWidth = 2.dp.toPx()
                                    )

                                    // Draw actual vector boundaries
                                    vectorsList.forEach { v ->
                                        val pts = v.rawPoints
                                        if (pts.isNotEmpty()) {
                                            val strokeColor = if (selectedVectorId == v.id) SpindleGold else Color(0xFF00E5FF)
                                            val strokeWVal = if (selectedVectorId == v.id) 3.dp.toPx() else 1.5.dp.toPx()
                                            
                                            // Render loop lines
                                            for (idx in 0 until pts.size - 1) {
                                                val startS = Offset(localW/2f + pts[idx].x * scaleFactor, localH/2f - pts[idx].y * scaleFactor)
                                                val endS = Offset(localW/2f + pts[idx+1].x * scaleFactor, localH/2f - pts[idx+1].y * scaleFactor)
                                                drawLine(color = strokeColor, start = startS, end = endS, strokeWidth = strokeWVal)
                                            }

                                            // Draw individual coordinate endpoints / nodes
                                            pts.forEach { pt ->
                                                drawCircle(
                                                    color = Color.White,
                                                    radius = 2.5.dp.toPx(),
                                                    center = Offset(localW/2f + pt.x * scaleFactor, localH/2f - pt.y * scaleFactor)
                                                )
                                            }
                                        }
                                    }

                                    // Draw temporary/active polyline nodes under construction
                                    if (selectedCADTool == "POLYLINE" && draftedPolylineNodes.isNotEmpty()) {
                                        for (idx in 0 until draftedPolylineNodes.size - 1) {
                                            val startS = Offset(localW/2f + draftedPolylineNodes[idx].x * scaleFactor, localH/2f - draftedPolylineNodes[idx].y * scaleFactor)
                                            val endS = Offset(localW/2f + draftedPolylineNodes[idx+1].x * scaleFactor, localH/2f - draftedPolylineNodes[idx+1].y * scaleFactor)
                                            drawLine(
                                                color = Color(0xFF00E676),
                                                start = startS,
                                                end = endS,
                                                strokeWidth = 2.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                                            )
                                        }
                                        draftedPolylineNodes.forEach { pt ->
                                            drawCircle(
                                                color = Color(0xFF00E676),
                                                radius = 3.5.dp.toPx(),
                                                center = Offset(localW/2f + pt.x * scaleFactor, localH/2f - pt.y * scaleFactor)
                                            )
                                        }
                                    }
                                }

                                // Interactive HUD Badge
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(10.dp)
                                        .background(Color(0xFF0F111A).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                                        .border(0.5.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedCADTool == "POLYLINE") {
                                            if (lang == "id") "KLIK KANVAS UNTUK BUAT SEGMEN" else "CLICK Blueprint BOARD TO ENCODE NODES"
                                        } else {
                                            if (lang == "id") "Blueprint CAD: ${vectorsList.size} Objek Aktif" else "CAD Blueprint: ${vectorsList.size} Active Vectors"
                                        },
                                        color = PrimaryCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                // 3D PHOTOREALISTIC MATERIAL WOOD CARVER PREVIEW
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val shadedBitmapState = remember(selected3DModelPreset, materialType, reliefZMax, reliefScale, reliefCombineMode, reliefCx, reliefCy, reliefDepthGrid) {
                                        val grid = reliefDepthGrid
                                        if (grid != null) {
                                            generateReliefShadedBitmap(grid, materialType).asImageBitmap()
                                        } else {
                                            null
                                        }
                                    }
                                    // Custom drawBehind wood grain gradient canvas
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val woodWidth = size.width
                                        val woodHeight = size.height
                                        val scaleFactor = woodWidth / 180f

                                        // 1. Draw rich background gradient based on selected material type
                                        val materialGradient = when (materialType) {
                                            "OAK" -> Brush.verticalGradient(
                                                colors = listOf(Color(0xFFD7A15C), Color(0xFFBC8F5F), Color(0xFFD7A15C))
                                            )
                                            "CHERRY" -> Brush.verticalGradient(
                                                colors = listOf(Color(0xFF8B2C19), Color(0xFF5B1006), Color(0xFF8B2C19))
                                            )
                                            "PINE" -> Brush.verticalGradient(
                                                colors = listOf(Color(0xFFEEDD92), Color(0xFFD7C172), Color(0xFFEEDD92))
                                            )
                                            "BRASS" -> Brush.verticalGradient(
                                                colors = listOf(Color(0xFFC5A03A), Color(0xFFDEC36A), Color(0xFF8B6C12))
                                            )
                                            "SLATE" -> Brush.verticalGradient(
                                                colors = listOf(Color(0xFF353C42), Color(0xFF1E2226), Color(0xFF353C42))
                                            )
                                            else -> Brush.verticalGradient( // "WALNUT" default
                                                colors = listOf(Color(0xFF5A3125), Color(0xFF3B1E16), Color(0xFF5A3125))
                                            )
                                        }
                                        drawRect(brush = materialGradient)

                                        // 1B. Custom photorealistic knot overlays for Knotty Pine material
                                        if (materialType == "PINE") {
                                            drawCircle(
                                                color = Color(0xFF5A3125).copy(alpha = 0.25f),
                                                radius = 16.dp.toPx(),
                                                center = Offset(woodWidth * 0.25f, woodHeight * 0.35f)
                                            )
                                            drawCircle(
                                                color = Color(0xFF5A3125).copy(alpha = 0.15f),
                                                radius = 32.dp.toPx(),
                                                center = Offset(woodWidth * 0.25f, woodHeight * 0.35f),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                            drawCircle(
                                                color = Color(0xFF5A3125).copy(alpha = 0.25f),
                                                radius = 12.dp.toPx(),
                                                center = Offset(woodWidth * 0.75f, woodHeight * 0.7f)
                                            )
                                            drawCircle(
                                                color = Color(0xFF5A3125).copy(alpha = 0.15f),
                                                radius = 24.dp.toPx(),
                                                center = Offset(woodWidth * 0.75f, woodHeight * 0.7f),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }

                                        // 1C. Linear brush highlights for reflective Brass Metal
                                        if (materialType == "BRASS") {
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.2f),
                                                start = Offset(0f, 0f),
                                                end = Offset(woodWidth, woodHeight),
                                                strokeWidth = 32.dp.toPx()
                                            )
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.12f),
                                                start = Offset(woodWidth * 0.2f, 0f),
                                                end = Offset(woodWidth, woodHeight * 0.8f),
                                                strokeWidth = 14.dp.toPx()
                                            )
                                        }

                                        // 2. Simulated radial grain fibers (Draw growth rings for organic wood material)
                                        if (materialType != "BRASS" && materialType != "SLATE") {
                                            val radialFiberColor = when (materialType) {
                                                "OAK" -> Color(0xFF8B5A2B).copy(alpha = 0.08f)
                                                "CHERRY" -> Color(0xFF4A1006).copy(alpha = 0.08f)
                                                "PINE" -> Color(0xFF9E8E56).copy(alpha = 0.1f)
                                                else -> Color(0xFFC48A73).copy(alpha = 0.05f) // walnut defaults
                                            }
                                            for (i in 0..16) {
                                                val factor = i * (woodHeight / 16f)
                                                val graphPath = Path().apply {
                                                    moveTo(0f, factor)
                                                    for (stepX in 0..10) {
                                                        val xVal = stepX * (woodWidth / 10f)
                                                        val yVal = factor + Math.sin(((stepX * 0.5f) + i).toDouble()).toFloat() * 12f
                                                        lineTo(xVal, yVal)
                                                    }
                                                }
                                                drawPath(
                                                    path = graphPath,
                                                    color = radialFiberColor,
                                                    style = Stroke(width = 3.dp.toPx())
                                                )
                                            }
                                        }

                                        // Draw material borders to create 3D frame depth
                                        drawRect(
                                            color = Color.Black.copy(alpha = 0.35f),
                                            style = Stroke(width = 4.dp.toPx())
                                        )

                                        // 2B. Draw 3D Shaded Relief Heightmap Overlay
                                        shadedBitmapState?.let { imgBmp ->
                                             drawImage(
                                                 image = imgBmp,
                                                 dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                                                 alpha = 0.95f
                                             )
                                        }

                                        // 3. Render carved trenches
                                        val trenchColor = when (materialType) {
                                            "OAK" -> Color(0xFF5A3125)
                                            "CHERRY" -> Color(0xFF3A0B02)
                                            "PINE" -> Color(0xFF6B4522)
                                            "BRASS" -> Color(0xFF3B2F04)
                                            "SLATE" -> Color(0xFF0F1115)
                                            else -> Color(0xFF1E0E0A)
                                        }
                                        val highlightColor = when (materialType) {
                                            "BRASS" -> Color(0xFFFDEB9E).copy(alpha = 0.7f)
                                            "SLATE" -> Color(0xFF5F6E79).copy(alpha = 0.5f)
                                            else -> Color(0xFFFFECC4).copy(alpha = 0.6f)
                                        }

                                        val totalPointsToCarve = vectorsList.flatMap { v ->
                                            if (toolpathType == "POCKET") {
                                                val toolDiameter = when {
                                                    selectedBitName.contains("1/4") -> 6.35f
                                                    selectedBitName.contains("1/8") -> 3.175f
                                                    selectedBitName.contains("60°") -> 3.175f
                                                    selectedBitName.contains("2mm") -> 2.0f
                                                    else -> 3.175f
                                                }
                                                generatePocketPaths(v, toolDiameter).flatMap { it }
                                            } else {
                                                v.rawPoints
                                            }
                                        }

                                        if (totalPointsToCarve.isNotEmpty()) {
                                            val pointsToDrawCount = if (isSimPlaying) {
                                                (totalPointsToCarve.size * simTimeProg).toInt().coerceIn(0, totalPointsToCarve.size)
                                            } else {
                                                totalPointsToCarve.size
                                            }

                                            // Compile simulated blocks of lines
                                            vectorsList.forEach { v ->
                                                val pathsToRender = if (toolpathType == "POCKET") {
                                                    val toolDiameter = when {
                                                        selectedBitName.contains("1/4") -> 6.35f
                                                        selectedBitName.contains("1/8") -> 3.175f
                                                        selectedBitName.contains("60°") -> 3.175f
                                                        selectedBitName.contains("2mm") -> 2.0f
                                                        else -> 3.175f
                                                    }
                                                    generatePocketPaths(v, toolDiameter)
                                                } else {
                                                    listOf(v.rawPoints)
                                                }

                                                var renderedCount = 0
                                                pathsToRender.forEach { pts ->
                                                    if (pts.isNotEmpty()) {
                                                        for (idx in 0 until pts.size - 1) {
                                                            if (renderedCount <= pointsToDrawCount) {
                                                                val startS = Offset(woodWidth/2f + pts[idx].x * scaleFactor, woodHeight/2f - pts[idx].y * scaleFactor)
                                                                val endS = Offset(woodWidth/2f + pts[idx+1].x * scaleFactor, woodHeight/2f - pts[idx+1].y * scaleFactor)
                                                                
                                                                // Underlay depth offset carve shadows
                                                                drawLine(
                                                                    color = trenchColor.copy(alpha = 0.85f),
                                                                    start = Offset(startS.x + 1f, startS.y + 1f),
                                                                    end = Offset(endS.x + 1f, endS.y + 1f),
                                                                    strokeWidth = 5.dp.toPx()
                                                                )

                                                                // Main engraving trench
                                                                drawLine(
                                                                    color = when (materialType) {
                                                                        "OAK" -> Color(0xFF9E7036)
                                                                        "CHERRY" -> Color(0xFFC04C33)
                                                                        "PINE" -> Color(0xFFC2AD71)
                                                                        "BRASS" -> Color(0xFFD3B250)
                                                                        "SLATE" -> Color(0xFF24292C)
                                                                        else -> Color(0xFFCCA276) // walnut inner wood defaults
                                                                    },
                                                                    start = startS,
                                                                    end = endS,
                                                                    strokeWidth = if (toolpathType == "PROFILE") 3.dp.toPx() else 2.5.dp.toPx()
                                                                )

                                                                // Upper glowing tool highlight relief rim
                                                                drawLine(
                                                                    color = highlightColor,
                                                                    start = Offset(startS.x - 0.5f, startS.y - 0.5f),
                                                                    end = Offset(endS.x - 0.5f, endS.y - 0.5f),
                                                                    strokeWidth = 1.dp.toPx()
                                                                )
                                                            }
                                                            renderedCount++
                                                        }
                                                    }
                                                }
                                            }

                                            // 4. Moving Spindle Tool Overlay Animation (Spinning yellow coordinate tip!)
                                            if (isSimPlaying && pointsToDrawCount > 0 && pointsToDrawCount < totalPointsToCarve.size) {
                                                val activeCenterPt = totalPointsToCarve[pointsToDrawCount]
                                                val activeScreenPos = Offset(woodWidth/2f + activeCenterPt.x * scaleFactor, woodHeight/2f - activeCenterPt.y * scaleFactor)

                                                // Moving router tip core
                                                drawCircle(
                                                    color = SpindleGold,
                                                    radius = 8.dp.toPx(),
                                                    center = activeScreenPos
                                                )
                                                drawCircle(
                                                    color = Color.White,
                                                    radius = 4.dp.toPx(),
                                                    center = activeScreenPos
                                                )
                                                // Outer cutting spindle visual circle aura
                                                drawCircle(
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    radius = 16.dp.toPx(),
                                                    center = activeScreenPos,
                                                    style = Stroke(width = 1.5.dp.toPx())
                                                )
                                            }
                                        }
                                    }

                                    // Material Simulation controller bar overlay
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(14.dp)
                                            .background(Color(0xFF0F111A).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { isSimPlaying = !isSimPlaying },
                                            colors = ButtonDefaults.buttonColors(containerColor = SpindleGold),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSimPlaying) Icons.Filled.Warning else Icons.Filled.PlayArrow,
                                                contentDescription = "Simulate",
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isSimPlaying) "Pause" else "PLAY SIM", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }

                                        LinearProgressIndicator(
                                            progress = { simTimeProg },
                                            modifier = Modifier
                                                .width(100.dp)
                                                .height(5.dp),
                                            color = SpindleGold,
                                            trackColor = Color(0xFF1E283A)
                                        )

                                        IconButton(
                                            onClick = {
                                                isSimPlaying = false
                                                simTimeProg = 0f
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, "Reset", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Window 4: Live Results and G-Code Compilation Terminal panel
                    if (isCalculated) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, BorderCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, "Solved", tint = Color(0xFF00E676), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (lang == "id") "HASIL JALUR VEKTOR JALUR ALAT" else "ASPIRE COMPLIANT SOLVER OUTPUT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676)
                                        )
                                    }
                                    Text(
                                        text = "EST: $estimatedTimeStr",
                                        color = SpindleGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateDark, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Post: GRBL/FluidNC-Compliant\nLine Count: $totalGCodeLines G-lines\nBit Diameter: $selectedBitName",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextLight,
                                        lineHeight = 12.sp
                                    )
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("CNC G-Code", activeGCodePreview)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, if (lang == "id") "G-code tersalin!" else "G-Code Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text("COPY G-CODE", fontSize = 9.sp, color = SlateDark, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Interactive Gcode editor preview
                                OutlinedTextField(
                                    value = activeGCodePreview,
                                    onValueChange = {
                                        activeGCodePreview = it
                                        viewModel.currentGCode = it
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF81C784)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// REAL VECTRIC ASPIRE CORE MATHEMATICAL & CAM ENGINE HELPERS
// =========================================================================

object CNCHummer {
    private var audioTrack: android.media.AudioTrack? = null
    private var isPlaying = false

    fun startHum(frequency: Float = 180f) {
        if (isPlaying) return
        isPlaying = true
        Thread {
            try {
                val sampleRate = 8000
                val numSamples = 1200
                val bufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                val track = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize.coerceAtLeast(numSamples * 2),
                    android.media.AudioTrack.MODE_STREAM
                )
                audioTrack = track
                track.play()

                val samples = ShortArray(numSamples)
                var angle = 0.0
                while (isPlaying) {
                    for (i in 0 until numSamples) {
                        // Aggressive mechanical router cutting wave (combining fundamental + tooth click high frequency)
                        val waveValue = 0.4 * Math.sin(angle) + 0.15 * Math.sin(angle * 2.1) + 0.1 * Math.sin(angle * 5.0)
                        samples[i] = (waveValue * Short.MAX_VALUE).toInt().toShort()
                        angle += 2 * Math.PI * frequency / sampleRate
                    }
                    track.write(samples, 0, numSamples)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stopHum() {
        isPlaying = false
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // silent catch
        }
        audioTrack = null
    }
}

fun getCentroid(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset(0f, 0f)
    var sumX = 0f
    var sumY = 0f
    points.forEach {
        sumX += it.x
        sumY += it.y
    }
    return Offset(sumX / points.size, sumY / points.size)
}

fun getBounds(points: List<Offset>): Bounds {
    if (points.isEmpty()) return Bounds(0f, 0f, 0f, 0f)
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    points.forEach {
        if (it.x < minX) minX = it.x
        if (it.x > maxX) maxX = it.x
        if (it.y < minY) minY = it.y
        if (it.y > maxY) maxY = it.y
    }
    return Bounds(minX, maxX, minY, maxY)
}

data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

fun offsetClosedPoints(points: List<Offset>, offsetVal: Float): List<Offset> {
    if (points.size < 3) return points
    val result = mutableListOf<Offset>()
    val n = points.size
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val curr = points[i]
        val next = points[(i + 1) % n]

        val dx1 = curr.x - prev.x
        val dy1 = curr.y - prev.y
        val len1 = Math.hypot(dx1.toDouble(), dy1.toDouble()).toFloat()

        val dx2 = next.x - curr.x
        val dy2 = next.y - curr.y
        val len2 = Math.hypot(dx2.toDouble(), dy2.toDouble()).toFloat()

        if (len1 < 0.01f || len2 < 0.01f) {
            result.add(curr)
            continue
        }

        // Inside/Outside bi-sector math normals
        val nx1 = -dy1 / len1
        val ny1 = dx1 / len1
        val nx2 = -dy2 / len2
        val ny2 = dx2 / len2

        val bisX = (nx1 + nx2) / 2f
        val bisY = (ny1 + ny2) / 2f
        val bisLen = Math.hypot(bisX.toDouble(), bisY.toDouble()).toFloat()

        if (bisLen > 0.01f) {
            val factor = offsetVal / bisLen
            result.add(Offset(curr.x + bisX * factor, curr.y + bisY * factor))
        } else {
            result.add(Offset(curr.x + nx1 * offsetVal, curr.y + ny1 * offsetVal))
        }
    }
    if (result.isNotEmpty()) result.add(result.first()) // close loop
    return result
}

fun generatePocketClearPoints(v: AspireVector, stepover: Float): List<List<Offset>> {
    val bounds = getBounds(v.rawPoints)
    val h = bounds.maxY - bounds.minY
    val w = bounds.maxX - bounds.minX
    if (h < 1f || w < 1f || stepover <= 0.1f) return listOf(v.rawPoints)

    val paths = mutableListOf<List<Offset>>()
    var y = bounds.minY + stepover * 0.5f
    var leftToRight = true
    while (y < bounds.maxY) {
        // High fidelity raster clipping limits based on standard geometries
        var minXAtY = bounds.minX + w * 0.1f
        var maxXAtY = bounds.maxX - w * 0.1f

        if (v.type == "Circle" || v.type == "Inner Circle") {
            val r = v.radius
            val dy = Math.abs(y - v.cy)
            if (dy < r) {
                val dx = Math.sqrt((r * r - dy * dy).toDouble()).toFloat()
                minXAtY = v.cx - dx
                maxXAtY = v.cx + dx
            }
        } else if (v.type == "Rectangle" || v.type == "Outer Border") {
            minXAtY = v.cx - v.width / 2f + v.cornerRadius * 0.2f
            maxXAtY = v.cx + v.width / 2f - v.cornerRadius * 0.2f
        } else if (v.type == "Oval" || v.type == "Ellipse") {
            val rx = v.width / 2f
            val ry = v.height / 2f
            val dy = Math.abs(y - v.cy)
            if (dy < ry) {
                val factor = Math.sqrt((1f - (dy * dy) / (ry * ry)).toDouble()).toFloat()
                val dx = rx * factor
                minXAtY = v.cx - dx
                maxXAtY = v.cx + dx
            }
        } else {
            // Linear ratio taper interpolation for Polygons / Stars layout
            val cy = (bounds.minY + bounds.maxY) / 2f
            val ratio = (1f - (Math.abs(y - cy) / (h / 2f)) * 0.6f).coerceIn(0.1f, 1f)
            minXAtY = v.cx - (w / 2f) * ratio
            maxXAtY = v.cx + (w / 2f) * ratio
        }

        if (minXAtY + 0.1f < maxXAtY) {
            val line = if (leftToRight) {
                listOf(Offset(minXAtY, y), Offset(maxXAtY, y))
            } else {
                listOf(Offset(maxXAtY, y), Offset(minXAtY, y))
            }
            paths.add(line)
        }
        leftToRight = !leftToRight
        y += stepover
    }

    if (paths.isEmpty()) return listOf(v.rawPoints)
    return paths
}

private fun createCirclePoints(cx: Float, cy: Float, radius: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    val segments = 48
    for (i in 0..segments) {
        val angle = (2.0 * Math.PI * i) / segments
        val x = cx + radius * Math.cos(angle).toFloat()
        val y = cy + radius * Math.sin(angle).toFloat()
        points.add(Offset(x, y))
    }
    return points
}

private fun createOvalPoints(cx: Float, cy: Float, width: Float, height: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    val segments = 40
    val rx = width / 2f
    val ry = height / 2f
    for (i in 0..segments) {
        val angle = (2.0 * Math.PI * i) / segments
        val x = cx + rx * Math.cos(angle).toFloat()
        val y = cy + ry * Math.sin(angle).toFloat()
        points.add(Offset(x, y))
    }
    return points
}

private fun createArcPoints(x1: Float, y1: Float, x2: Float, y2: Float, elevation: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    val segments = 20
    val midX = (x1 + x2) / 2f
    val midY = (y1 + y2) / 2f
    val dx = x2 - x1
    val dy = y2 - y1
    val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len < 0.1f) {
        return listOf(Offset(x1, y1), Offset(x2, y2))
    }
    // Perpendicular norm
    val px = -dy / len
    val py = dx / len
    val apexX = midX + px * elevation
    val apexY = midY + py * elevation

    // Quadratic interpolation bezier
    for (i in 0..segments) {
        val t = i.toFloat() / segments
        val mt = 1f - t
        val x = mt * mt * x1 + 2 * mt * t * apexX + t * t * x2
        val y = mt * mt * y1 + 2 * mt * t * apexY + t * t * y2
        points.add(Offset(x, y))
    }
    return points
}

private fun createRectanglePoints(cx: Float, cy: Float, w: Float, h: Float, cr: Float): List<Offset> {
    return createRectanglePointsAdvanced(cx, cy, w, h, "ROUNDED", cr, 1.5f)
}

private fun createRectanglePointsAdvanced(
    cx: Float, cy: Float, w: Float, h: Float,
    cornerType: String, cornerRadius: Float, bitRadius: Float
): List<Offset> {
    val points = mutableListOf<Offset>()
    val halfW = w / 2f
    val halfH = h / 2f

    if (cornerType == "SQUARE" || (cornerType == "ROUNDED" && cornerRadius <= 0.1f)) {
        points.add(Offset(cx - halfW, cy - halfH))
        points.add(Offset(cx + halfW, cy - halfH))
        points.add(Offset(cx + halfW, cy + halfH))
        points.add(Offset(cx - halfW, cy + halfH))
        points.add(Offset(cx - halfW, cy - halfH))
    } else if (cornerType == "ROUNDED") {
        val rVal = cornerRadius.coerceAtMost(halfW).coerceAtMost(halfH)
        val arcSegs = 10
        // Rounded corners
        for (i in 0..arcSegs) {
            val a = Math.PI + (i * Math.PI / 2.0 / arcSegs)
            points.add(Offset((cx - halfW + rVal) + rVal * Math.cos(a).toFloat(), (cy - halfH + rVal) + rVal * Math.sin(a).toFloat()))
        }
        for (i in 0..arcSegs) {
            val a = 1.5 * Math.PI + (i * Math.PI / 2.0 / arcSegs)
            points.add(Offset((cx + halfW - rVal) + rVal * Math.cos(a).toFloat(), (cy - halfH + rVal) + rVal * Math.sin(a).toFloat()))
        }
        for (i in 0..arcSegs) {
            val a = 0.0 + (i * Math.PI / 2.0 / arcSegs)
            points.add(Offset((cx + halfW - rVal) + rVal * Math.cos(a).toFloat(), (cy + halfH - rVal) + rVal * Math.sin(a).toFloat()))
        }
        for (i in 0..arcSegs) {
            val a = 0.5 * Math.PI + (i * Math.PI / 2.0 / arcSegs)
            points.add(Offset((cx - halfW + rVal) + rVal * Math.cos(a).toFloat(), (cy + halfH - rVal) + rVal * Math.sin(a).toFloat()))
        }
        points.add(points.first())
    } else if (cornerType == "DOGBONE") {
        val r = bitRadius.coerceAtMost(halfW * 0.4f).coerceAtMost(halfH * 0.4f)
        val d = r * 0.707f // diagonal projection

        // Bottom Left corner notch
        points.add(Offset(cx - halfW, cy - halfH + r * 1.5f))
        points.add(Offset(cx - halfW - d, cy - halfH - d))
        points.add(Offset(cx - halfW + r * 1.5f, cy - halfH))

        // Bottom Right corner notch
        points.add(Offset(cx + halfW - r * 1.5f, cy - halfH))
        points.add(Offset(cx + halfW + d, cy - halfH - d))
        points.add(Offset(cx + halfW, cy - halfH + r * 1.5f))

        // Top Right corner notch
        points.add(Offset(cx + halfW, cy + halfH - r * 1.5f))
        points.add(Offset(cx + halfW + d, cy + halfH + d))
        points.add(Offset(cx + halfW - r * 1.5f, cy + halfH))

        // Top Left corner notch
        points.add(Offset(cx - halfW + r * 1.5f, cy + halfH))
        points.add(Offset(cx - halfW - d, cy + halfH + d))
        points.add(Offset(cx - halfW, cy + halfH - r * 1.5f))

        points.add(points.first())
    } else { // "TBONE"
        val r = bitRadius.coerceAtMost(halfW * 0.4f).coerceAtMost(halfH * 0.4f)

        // Bottom Left T-bone relief cut
        points.add(Offset(cx - halfW, cy - halfH + r * 1.5f))
        points.add(Offset(cx - halfW - r, cy - halfH))
        points.add(Offset(cx - halfW + r * 1.5f, cy - halfH))

        // Bottom Right
        points.add(Offset(cx + halfW - r * 1.5f, cy - halfH))
        points.add(Offset(cx + halfW + r, cy - halfH))
        points.add(Offset(cx + halfW, cy - halfH + r * 1.5f))

        // Top Right
        points.add(Offset(cx + halfW, cy + halfH - r * 1.5f))
        points.add(Offset(cx + halfW + r, cy + halfH))
        points.add(Offset(cx + halfW - r * 1.5f, cy + halfH))

        // Top Left
        points.add(Offset(cx - halfW + r * 1.5f, cy + halfH))
        points.add(Offset(cx - halfW - r, cy + halfH))
        points.add(Offset(cx - halfW, cy + halfH - r * 1.5f))

        points.add(points.first())
    }
    return points
}

private fun createPolygonPoints(cx: Float, cy: Float, radius: Float, sides: Int): List<Offset> {
    val points = mutableListOf<Offset>()
    val sCount = sides.coerceAtLeast(3)
    for (i in 0..sCount) {
        val angle = (2.0 * Math.PI * i) / sCount
        val x = cx + radius * Math.cos(angle).toFloat()
        val y = cy + radius * Math.sin(angle).toFloat()
        points.add(Offset(x, y))
    }
    return points
}

private fun createStarPoints(cx: Float, cy: Float, pointsCount: Int, rOuter: Float, rInner: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    val totalSteps = pointsCount * 2
    for (i in 0..totalSteps) {
        val angle = (i * Math.PI / pointsCount) - (Math.PI / 2.0)
        val r = if (i % 2 == 0) rOuter else rInner
        val x = cx + r * Math.cos(angle).toFloat()
        val y = cy + r * Math.sin(angle).toFloat()
        points.add(Offset(x, y))
    }
    return points
}

private fun createTextVectorPoints(text: String, startX: Float, startY: Float, height: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    var cursorX = startX
    text.forEach { char ->
        val glyphPoints = when (char.uppercaseChar()) {
            'C' -> listOf(Offset(1f, 1f), Offset(0f, 1f), Offset(0f, 0f), Offset(1f, 0f))
            'N' -> listOf(Offset(0f, 0f), Offset(0f, 1f), Offset(1f, 0f), Offset(1f, 1f))
            'V' -> listOf(Offset(0f, 1f), Offset(0.5f, 0f), Offset(1f, 1f))
            'E' -> listOf(Offset(1f, 1f), Offset(0f, 1f), Offset(0f, 0.5f), Offset(0.8f, 0.5f), Offset(0f, 0.5f), Offset(0f, 0f), Offset(1f, 0f))
            'T' -> listOf(Offset(0f, 1f), Offset(1f, 1f), Offset(0.5f, 1f), Offset(0.5f, 0f))
            'R' -> listOf(Offset(0f, 0f), Offset(0f, 1f), Offset(1f, 1f), Offset(1f, 0.5f), Offset(0f, 0.5f), Offset(1f, 0f))
            'I' -> listOf(Offset(0f, 1f), Offset(1f, 1f), Offset(0.5f, 1f), Offset(0.5f, 0f), Offset(0f, 0f), Offset(1f, 0f))
            'A' -> listOf(Offset(0f, 0f), Offset(0.5f, 1f), Offset(1f, 0f), Offset(0.8f, 0.4f), Offset(0.2f, 0.4f))
            'S' -> listOf(Offset(1f, 1f), Offset(0f, 1f), Offset(0f, 0.5f), Offset(1f, 0.5f), Offset(1f, 0f), Offset(0f, 0f))
            'P' -> listOf(Offset(0f, 0f), Offset(0f, 1f), Offset(1f, 1f), Offset(1f, 0.5f), Offset(0f, 0.5f))
            ' ' -> emptyList()
            else -> listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f), Offset(0f, 0f))
        }
        if (glyphPoints.isNotEmpty()) {
            glyphPoints.forEach { pt ->
                points.add(Offset(cursorX + pt.x * height * 0.65f, startY + pt.y * height))
            }
        }
        cursorX += height * 0.85f
    }
    return points
}

private fun scaleVector(vector: AspireVector, scale: Float): AspireVector {
    val newPoints = vector.rawPoints.map { pt ->
        val dx = pt.x - vector.cx
        val dy = pt.y - vector.cy
        Offset(vector.cx + dx * scale, vector.cy + dy * scale)
    }
    return vector.copy(
        radius = vector.radius * scale,
        width = vector.width * scale,
        height = vector.height * scale,
        cornerRadius = vector.cornerRadius * scale,
        textHeight = vector.textHeight * scale,
        rawPoints = newPoints
    )
}

private fun rotateVector(vector: AspireVector, angleDegrees: Float): AspireVector {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val cos = Math.cos(radians).toFloat()
    val sin = Math.sin(radians).toFloat()
    val newPoints = vector.rawPoints.map { pt ->
        val dx = pt.x - vector.cx
        val dy = pt.y - vector.cy
        val rotatedX = dx * cos - dy * sin
        val rotatedY = dx * sin + dy * cos
        Offset(vector.cx + rotatedX, vector.cy + rotatedY)
    }
    return vector.copy(rawPoints = newPoints)
}

private fun mirrorVector(vector: AspireVector, horizontal: Boolean): AspireVector {
    val newPoints = vector.rawPoints.map { pt ->
        if (horizontal) {
            val dx = pt.x - vector.cx
            Offset(vector.cx - dx, pt.y)
        } else {
            val dy = pt.y - vector.cy
            Offset(pt.x, vector.cy - dy)
        }
    }
    return vector.copy(rawPoints = newPoints)
}

private fun moveVector(vector: AspireVector, dx: Float, dy: Float): AspireVector {
    val newPoints = vector.rawPoints.map { pt ->
        Offset(pt.x + dx, pt.y + dy)
    }
    return vector.copy(
        cx = vector.cx + dx,
        cy = vector.cy + dy,
        rawPoints = newPoints
    )
}

private fun centerVector(vector: AspireVector): AspireVector {
    val dx = -vector.cx
    val dy = -vector.cy
    return moveVector(vector, dx, dy)
}

private fun generatePocketPaths(vector: AspireVector, toolDiameter: Float): List<List<Offset>> {
    val paths = mutableListOf<List<Offset>>()
    paths.add(vector.rawPoints)
    
    if (vector.type == "Circle" && vector.radius > toolDiameter) {
        var currentRadius = vector.radius - toolDiameter * 0.7f
        while (currentRadius > 0.5f) {
            paths.add(createCirclePoints(vector.cx, vector.cy, currentRadius))
            currentRadius -= toolDiameter * 0.7f
        }
    } else if (vector.type == "Rectangle" && vector.width > toolDiameter && vector.height > toolDiameter) {
        var offsetW = vector.width - toolDiameter * 1.4f
        var offsetH = vector.height - toolDiameter * 1.4f
        while (offsetW > 0.5f && offsetH > 0.5f) {
            paths.add(createRectanglePoints(vector.cx, vector.cy, offsetW, offsetH, vector.cornerRadius))
            offsetW -= toolDiameter * 1.4f
            offsetH -= toolDiameter * 1.4f
        }
    } else {
        for (i in 1..4) {
            val scale = 1f - (i * 0.2f)
            if (scale > 0.1f) {
                val replica = vector.rawPoints.map { pt ->
                    Offset(vector.cx + (pt.x - vector.cx) * scale, vector.cy + (pt.y - vector.cy) * scale)
                }
                paths.add(replica)
            }
        }
    }
    return paths
}

private fun compileToAspireGCode(
    vectors: List<AspireVector>,
    depthZ: Float,
    passZ: Float,
    spindleRpm: Int,
    feedRate: Float,
    plungeRate: Float,
    safeHeight: Float,
    toolName: String,
    sideMode: String,
    toolpathType: String,
    addHoldingTabs: Boolean,
    holdingTabsCount: Int,
    cuttingDirection: String,
    reliefDepthGrid: Array<FloatArray>? = null,
    reliefZMax: Float = 6.0f
): String {
    val builder = StringBuilder()
    
    val toolDiameter = when {
        toolName.contains("1/4") -> 6.35f
        toolName.contains("1/8") -> 3.175f
        toolName.contains("60°") -> 3.175f
        toolName.contains("2mm") -> 2.0f
        else -> 3.175f
    }
    val toolRadius = toolDiameter / 2f
    
    // G-code standard headers (GRBL/FluidNC standard)
    builder.append(";====================================================\n")
    builder.append("; Vectric ASPIRE G-code Post-Processor Compiler v5.0\n")
    builder.append("; CAD/CAM Style: $toolpathType | Direction: $cuttingDirection\n")
    builder.append("; Tool Select: $toolName (Dia: $toolDiameter mm)\n")
    builder.append("; Target Finish Depth: $depthZ mm | Step Down: $passZ mm\n")
    builder.append("; Holding Tabs: ${if (addHoldingTabs) "Enabled ($holdingTabsCount tabs)" else "Disabled"}\n")
    builder.append("; CNC Codebase Compliant: FluidNC / GRBL Controller\n")
    builder.append(";====================================================\n\n")
    
    builder.append("G21 ; Set units to Metric millimetres\n")
    builder.append("G90 ; Set positioning absolute coordinate mode\n")
    builder.append("G17 ; Select XY drawing plane orientation\n")
    builder.append("M3 S$spindleRpm ; Initialize tool spindle RPM clockwise\n")
    builder.append("G0 Z$safeHeight ; Raise spindle cutter index to safe hover Z height\n\n")
    
    val depthValOffset = Math.abs(depthZ)
    val passValOffset = Math.abs(passZ)
    val totalPasses = Math.ceil((depthValOffset / passValOffset).toDouble()).toInt()
    
    if (toolpathType == "3D_ROUGH" || toolpathType == "3D_FINISH") {
        val grid = reliefDepthGrid
        val maxZ = Math.abs(reliefZMax)
        if (grid != null) {
            val w = grid.size
            val h = grid[0].size
            builder.append("; --- Creating Raster Carving Passes for 3D Relief Model ---\n")
            
            val step = if (toolpathType == "3D_ROUGH") 4 else 2
            val mmRange = 120f
            
            for (yIdx in 0 until h step step) {
                val range = if (yIdx % 2 == 0) (0 until w step step) else ((w - 1) downTo 0 step step)
                val mmY = -(yIdx - h/2f) * (mmRange / h)
                
                // Rapid transit to start of row
                val firstXIdx = if (yIdx % 2 == 0) 0 else w - 1
                val mmXFirst = (firstXIdx - w/2f) * (mmRange / w)
                val firstZ = -grid[firstXIdx][yIdx] * maxZ
                
                builder.append("G0 X${String.format("%.3f", mmXFirst)} Y${String.format("%.3f", mmY)} Z$safeHeight ; Transit to start\n")
                builder.append("G1 Z${String.format("%.3f", firstZ)} F${plungeRate.toInt()} ; Plunge\n")
                
                range.forEach { xIdx ->
                    val mmX = (xIdx - w/2f) * (mmRange / w)
                    val zVal = -grid[xIdx][yIdx] * maxZ
                    builder.append("G1 X${String.format("%.3f", mmX)} Y${String.format("%.3f", mmY)} Z${String.format("%.3f", zVal)} F${feedRate.toInt()}\n")
                }
                builder.append("G1 Z$safeHeight F800 ; Lift\n\n")
            }
        } else {
            builder.append("; Warning: No 3D Relief model selected or loaded for carving!\n")
        }
        
        // Standard shutdown sequence
        builder.append("; --- Shutdown sequence ---\n")
        builder.append("M5 ; Spindle tool rotation stop\n")
        builder.append("G0 X0.000 Y0.000 ; Rapid transit axis return to home origin XY zero\n")
        builder.append("M2 ; End G-code programs safely\n")
        return builder.toString()
    }
    
    vectors.forEach { v ->
        builder.append("; --- Constructing Toolpaths for Vector Entity: [${v.type.uppercase()}] ${v.name} ---\n")
        
        // Apply tool radius offset of Profile cut (Outside/Inside)
        var cuttingPoints = v.rawPoints
        if (toolpathType == "PROFILE") {
            if (sideMode == "OUTSIDE") {
                if (v.type == "Circle") {
                    cuttingPoints = createCirclePoints(v.cx, v.cy, v.radius + toolRadius)
                } else if (v.type == "Rectangle") {
                    cuttingPoints = createRectanglePoints(v.cx, v.cy, v.width + toolDiameter, v.height + toolDiameter, v.cornerRadius)
                }
            } else if (sideMode == "INSIDE") {
                if (v.type == "Circle" && v.radius > toolRadius) {
                    cuttingPoints = createCirclePoints(v.cx, v.cy, v.radius - toolRadius)
                } else if (v.type == "Rectangle" && v.width > toolDiameter && v.height > toolDiameter) {
                    cuttingPoints = createRectanglePoints(v.cx, v.cy, v.width - toolDiameter, v.height - toolDiameter, v.cornerRadius)
                }
            }
        }
        
        if (cuttingPoints.isEmpty()) return@forEach
        
        // Multi-passes
        if (toolpathType == "DRILL") {
            // Drill mode: Move to center, slowly plunge to depth, and lift!
            val drillPt = if (v.type == "Polyline" && v.rawPoints.isNotEmpty()) v.rawPoints.first() else Offset(v.cx, v.cy)
            builder.append("; Transit to Center of Hole: X=${drillPt.x}, Y=${drillPt.y}\n")
            builder.append("G0 X${String.format("%.3f", drillPt.x)} Y${String.format("%.3f", drillPt.y)} Z$safeHeight\n")
            builder.append("G1 Z$depthZ F${plungeRate.toInt()} ; Plunge drilling down\n")
            builder.append("G0 Z$safeHeight ; Retract drilling spindle\n\n")
        } else if (toolpathType == "POCKET") {
            // Pocket mode: Spiral clearing
            val pocketPathsList = generatePocketPaths(v, toolDiameter)
            pocketPathsList.forEachIndexed { pathIdx, pPoints ->
                builder.append("; Pocket Pass Spiral Loop $pathIdx\n")
                val fPt = pPoints.first()
                builder.append("G0 X${String.format("%.3f", fPt.x)} Y${String.format("%.3f", fPt.y)} Z$safeHeight ; Rapid transit\n")
                
                for (passIdx in 1..totalPasses) {
                    val activePassZValue = -(passIdx * passValOffset).coerceAtMost(depthValOffset)
                    builder.append("G1 Z${String.format("%.3f", activePassZValue)} F${plungeRate.toInt()} ; Plunge into pocket layer\n")
                    pPoints.forEach { pt ->
                        builder.append("G1 X${String.format("%.3f", pt.x)} Y${String.format("%.3f", pt.y)} F${feedRate.toInt()} ; Pocketing feed\n")
                    }
                }
                builder.append("G0 Z$safeHeight ; Raise cutter clear\n\n")
            }
        } else {
            // General Profiling or centerline carves
            val fPt = cuttingPoints.first()
            builder.append("G0 X${String.format("%.3f", fPt.x)} Y${String.format("%.3f", fPt.y)} Z$safeHeight\n")
            
            for (passIdx in 1..totalPasses) {
                val activePassZValue = -(passIdx * passValOffset).coerceAtMost(depthValOffset)
                builder.append("; Profile Pass Level $passIdx at Depth Z = $activePassZValue mm\n")
                
                builder.append("G1 Z${String.format("%.3f", activePassZValue)} F${plungeRate.toInt()} ; Plunge plunge-rate\n")
                
                // Climb vs Conventional cut ordering: Reverse coordinate array traversal if Conventional cut!
                val orientedPoints = if (cuttingDirection == "CONVENTIONAL") cuttingPoints.reversed() else cuttingPoints
                
                orientedPoints.forEachIndexed { ptIdx, pt ->
                    // Support Holding Tabs Bridge Lift: raise Z-axis for a short range at tab positions
                    val isAtTabLocation = addHoldingTabs && (ptIdx > 0 && @Suppress("INTEGER_DIVIDE_BY_ZERO") ptIdx % (orientedPoints.size / (holdingTabsCount + 1).coerceAtLeast(2)).coerceAtLeast(1) == 0)
                    val activeCutZ = if (isAtTabLocation && passIdx == totalPasses) {
                        // Raise tool halfway through material stock to leave a solid structural hold tab bridge!
                        activePassZValue / 2f
                    } else {
                        activePassZValue
                    }
                    builder.append("G1 X${String.format("%.3f", pt.x)} Y${String.format("%.3f", pt.y)} Z${String.format("%.3f", activeCutZ)} F${feedRate.toInt()} ; Cutting path\n")
                }
            }
            builder.append("G0 Z$safeHeight ; Retract clear\n\n")
        }
    }
    
    // Standard shutdown sequence
    builder.append("; --- Shutdown sequence ---\n")
    builder.append("M5 ; Spindle tool rotation stop\n")
    builder.append("G0 X0.000 Y0.000 ; Rapid transit axis return to home origin XY zero\n")
    builder.append("M2 ; End G-code programs safely\n")
    
    return builder.toString()
}

// =========================================================================
// 3D MODELING & CNC SLICING MACHINERY (VECTRIC ASPIRE STYLE)
// =========================================================================

data class AspireVector3(val x: Float, val y: Float, val z: Float)

/**
 * Robust binary & ASCII CAD STL mesh parser.
 * Projects 3D mesh triangles onto a memory-safe heightmap grid.
 */
private fun parseStlToDepthMap(inputStream: java.io.InputStream, gridWidth: Int = 64, gridHeight: Int = 64): Array<FloatArray> {
    val grid = Array(gridWidth) { FloatArray(gridHeight) { 0f } }
    try {
        val bytes = inputStream.readBytes()
        if (bytes.size < 84) return grid
        
        // Auto-detect format type: ASCII starts with "solid" keyword
        val headerString = bytes.take(80).toByteArray().decodeToString()
        val isAscii = headerString.trimStart().startsWith("solid")
        
        val triangles = mutableListOf<Triple<AspireVector3, AspireVector3, AspireVector3>>()
        
        if (isAscii) {
            val reader = bytes.decodeToString().reader().buffered()
            var line = reader.readLine()
            var v1: AspireVector3? = null
            var v2: AspireVector3? = null
            var v3: AspireVector3? = null
            while (line != null) {
                val trimmed = line.trim().lowercase()
                if (trimmed.startsWith("vertex")) {
                    val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    if (parts.size >= 4) {
                        val x = parts[1].toFloatOrNull() ?: 0f
                        val y = parts[2].toFloatOrNull() ?: 0f
                        val z = parts[3].toFloatOrNull() ?: 0f
                        val vec = AspireVector3(x, y, z)
                        if (v1 == null) v1 = vec
                        else if (v2 == null) v2 = vec
                        else if (v3 == null) {
                            v3 = vec
                            triangles.add(Triple(v1, v2, v3))
                            v1 = null
                            v2 = null
                            v3 = null
                        }
                    }
                }
                line = reader.readLine()
            }
        } else {
            // Binary STL format parsing
            var offset = 80
            val numTriangles = java.nio.ByteBuffer.wrap(bytes, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            offset += 4
            
            val limit = minOf(numTriangles, 6000) // safety limit for quick indexing on mobile
            for (i in 0 until limit) {
                if (offset + 50 > bytes.size) break
                offset += 12 // skip face normals vector coordinates
                
                val buf = java.nio.ByteBuffer.wrap(bytes, offset, 36).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val v1 = AspireVector3(buf.getFloat(), buf.getFloat(), buf.getFloat())
                val v2 = AspireVector3(buf.getFloat(), buf.getFloat(), buf.getFloat())
                val v3 = AspireVector3(buf.getFloat(), buf.getFloat(), buf.getFloat())
                offset += 36
                offset += 2 // skip attributes details
                
                triangles.add(Triple(v1, v2, v3))
            }
        }
        
        if (triangles.isEmpty()) return grid
        
        // Establish bounding bounds
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        
        triangles.forEach { (v1, v2, v3) ->
            listOf(v1, v2, v3).forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
            }
        }
        
        val dx = if (maxX - minX > 0) maxX - minX else 1f
        val dy = if (maxY - minY > 0) maxY - minY else 1f
        val dz = if (maxZ - minZ > 0) maxZ - minZ else 1f
        
        // Raster project heights map
        triangles.forEach { (v1, v2, v3) ->
            val triMinX = minOf(v1.x, v2.x, v3.x)
            val triMaxX = maxOf(v1.x, v2.x, v3.x)
            val triMinY = minOf(v1.y, v2.y, v3.y)
            val triMaxY = maxOf(v1.y, v2.y, v3.y)
            
            val colStart = (((triMinX - minX) / dx) * (gridWidth - 1)).toInt().coerceIn(0, gridWidth - 1)
            val colEnd = (((triMaxX - minX) / dx) * (gridWidth - 1)).toInt().coerceIn(0, gridWidth - 1)
            val rowStart = (((triMinY - minY) / dy) * (gridHeight - 1)).toInt().coerceIn(0, gridHeight - 1)
            val rowEnd = (((triMaxY - minY) / dy) * (gridHeight - 1)).toInt().coerceIn(0, gridHeight - 1)
            
            val avgZ = (v1.z + v2.z + v3.z) / 3f
            val normZ = if (dz > 0) (avgZ - minZ) / dz else 0.5f
            
            for (c in colStart..colEnd) {
                for (r in rowStart..rowEnd) {
                    if (normZ > grid[c][r]) {
                        grid[c][r] = normZ
                    }
                }
            }
        }
        
        // Blurring filter box-blur smoothing
        val smoothGrid = Array(gridWidth) { FloatArray(gridHeight) }
        for (c in 0 until gridWidth) {
            for (r in 0 until gridHeight) {
                var sum = 0f
                var count = 0
                for (dc in -1..1) {
                    for (dr in -1..1) {
                        val nc = c + dc
                        val nr = r + dr
                        if (nc in 0 until gridWidth && nr in 0 until gridHeight) {
                            sum += grid[nc][nr]
                            count++
                        }
                    }
                }
                smoothGrid[c][r] = if (count > 0) sum / count else grid[c][r]
            }
        }
        return smoothGrid
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return grid
}

/**
 * Generates an acanthus floral rosette relief ornament
 */
private fun generateRosetteGrid(w: Int = 64, h: Int = 64): Array<FloatArray> {
    val grid = Array(w) { FloatArray(h) }
    for (x in 0 until w) {
        for (y in 0 until h) {
            val nx = (x - w / 2f) / (w / 2f)
            val ny = (y - h / 2f) / (h / 2f)
            val r = kotlin.math.sqrt((nx * nx + ny * ny).toDouble()).toFloat()
            if (r <= 1.0f) {
                val theta = kotlin.math.atan2(ny.toDouble(), nx.toDouble()).toFloat()
                val petal = kotlin.math.cos((8f * theta).toDouble()).toFloat()
                val height = (1.0f - r) * (0.6f + 0.4f * petal) * kotlin.math.sin((r * kotlin.math.PI).toDouble()).toFloat()
                grid[x][y] = height.coerceIn(0f, 1f)
            } else {
                grid[x][y] = 0f
            }
        }
    }
    return grid
}

/**
 * Generates an imperial crest shield & eagle ornament
 */
private fun generateEagleGrid(w: Int = 64, h: Int = 64): Array<FloatArray> {
    val grid = Array(w) { FloatArray(h) }
    for (x in 0 until w) {
        for (y in 0 until h) {
            val nx = (x - w / 2f) / (w / 2f)
            val ny = (y - h / 2f) / (h / 2f)
            val r = kotlin.math.sqrt((nx * nx + ny * ny).toDouble()).toFloat()
            var z = 0f
            if (r < 0.9f) {
                val body = 0.4f * (1f - (nx * nx * 2.5f + (ny + 0.1f) * (ny + 0.1f) * 1.5f).coerceIn(0f, 1f))
                val wingCurve = 0.5f * (1f - kotlin.math.abs(ny - (nx * nx * 0.4f))) * (1f - kotlin.math.abs(nx))
                val head = if (kotlin.math.abs(nx) < 0.15f && ny > 0.4f && ny < 0.7f) 0.35f else 0f
                val shield = if (kotlin.math.abs(nx) < 0.25f && kotlin.math.abs(ny) < 0.3f) {
                    0.5f * (1f - (nx / 0.25f) * (nx / 0.25f))
                } else 0f
                z = maxOf(body, wingCurve, head, shield).coerceIn(0f, 1f)
            }
            grid[x][y] = z
        }
    }
    return grid
}

/**
 * Generates an organic rose blossom rosette
 */
private fun generateRoseGrid(w: Int = 64, h: Int = 64): Array<FloatArray> {
    val grid = Array(w) { FloatArray(h) }
    for (x in 0 until w) {
        for (y in 0 until h) {
            val nx = (x - w / 2f) / (w / 2f)
            val ny = (y - h / 2f) / (h / 2f)
            val r = kotlin.math.sqrt((nx * nx + ny * ny).toDouble()).toFloat()
            if (r <= 0.85f) {
                val theta = kotlin.math.atan2(ny.toDouble(), nx.toDouble()).toFloat()
                val petal1 = kotlin.math.abs(kotlin.math.sin((5 * theta).toDouble())).toFloat()
                val val1 = (1f - r) * (0.3f + 0.7f * petal1)
                val petal2 = kotlin.math.abs(kotlin.math.sin((3 * theta + 2f).toDouble())).toFloat()
                val val2 = if (r < 0.5f) (0.5f - r) * (0.4f + 0.6f * petal2) else 0f
                grid[x][y] = (val1 + val2).coerceIn(0f, 1f)
            } else {
                grid[x][y] = 0f
            }
        }
    }
    return grid
}

/**
 * Real-time 3D Photorealistic render shading generator. Applies a fast local
 * normal diffuse lighting multiplier to represent real dimensional relief on wood/metals.
 */
private fun generateReliefShadedBitmap(grid: Array<FloatArray>, material: String): android.graphics.Bitmap {
    val w = grid.size
    val h = grid[0].size
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    
    // Choose beautiful color ramps
    val peakR: Float; val peakG: Float; val peakB: Float
    val valleyR: Float; val valleyG: Float; val valleyB: Float
    
    when (material) {
        "OAK" -> {
            peakR = 215/255f; peakG = 161/255f; peakB = 92/255f
            valleyR = 90/255f; valleyG = 49/255f; valleyB = 37/255f
        }
        "CHERRY" -> {
            peakR = 139/255f; peakG = 44/255f; peakB = 25/255f
            valleyR = 59/255f; valleyG = 11/255f; valleyB = 2/255f
        }
        "PINE" -> {
            peakR = 238/255f; peakG = 221/255f; peakB = 146/255f
            valleyR = 107/255f; valleyG = 69/255f; valleyB = 34/255f
        }
        "BRASS" -> {
            peakR = 222/255f; peakG = 195/255f; peakB = 106/255f
            valleyR = 59/255f; valleyG = 47/255f; valleyB = 4/255f
        }
        "SLATE" -> {
            peakR = 76/255f; peakG = 85/255f; peakB = 92/255f
            valleyR = 15/255f; valleyG = 17/255f; valleyB = 21/255f
        }
        else -> { // WALNUT default
            peakR = 122/255f; peakG = 75/255f; peakB = 58/255f
            valleyR = 30/255f; valleyG = 14/255f; valleyB = 10/255f
        }
    }
    
    for (x in 0 until w) {
        for (y in 0 until h) {
            val z = grid[x][y]
            
            val zLeft = if (x > 0) grid[x - 1][y] else z
            val zRight = if (x < w - 1) grid[x + 1][y] else z
            val zUp = if (y > 0) grid[x][y - 1] else z
            val zDown = if (y < h - 1) grid[x][y + 1] else z
            
            val nx = (zLeft - zRight) * 5f
            val ny = (zUp - zDown) * 5f
            val nz = 1.0f
            
            val len = kotlin.math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            val nnx = nx / len
            val nny = ny / len
            val nnz = nz / len
            
            // Light source: top-left overhead hover
            val lx = -0.6f
            val ly = 0.6f
            val lz = 1.2f
            val lLen = kotlin.math.sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()
            val nlx = lx / lLen
            val nly = ly / lLen
            val nlz = lz / lLen
            
            val dot = nnx * nlx + nny * nly + nnz * nlz
            val diffuse = maxOf(0f, dot)
            
            val baseR = valleyR * (1f - z) + peakR * z
            val baseG = valleyG * (1f - z) + peakG * z
            val baseB = valleyB * (1f - z) + peakB * z
            
            val shader = 0.5f + 0.62f * diffuse
            val finalR = (baseR * shader * 255f).coerceIn(0f, 255f).toInt()
            val finalG = (baseG * shader * 255f).coerceIn(0f, 255f).toInt()
            val finalB = (baseB * shader * 255f).coerceIn(0f, 255f).toInt()
            
            val alpha = if (z > 0.015f) 220 else 0 // alpha blend overlay
            val pixel = (alpha shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            bmp.setPixel(x, y, pixel)
        }
    }
    return bmp
}

/**
 * Scans high-resolution image dark regions to trace outlines / contours as vector paths
 */
private fun traceBitmapContours(bmp: android.graphics.Bitmap, threshold: Float): List<List<Offset>> {
    val paths = mutableListOf<List<Offset>>()
    try {
        val w = minOf(bmp.width, 64)
        val h = minOf(bmp.height, 64)
        val resized = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
        
        val isBlack = Array(w) { BooleanArray(h) }
        for (x in 0 until w) {
            for (y in 0 until h) {
                val pixel = resized.getPixel(x, y)
                val r = ((pixel shr 16) and 0xff) / 255f
                val g = ((pixel shr 8) and 0xff) / 255f
                val b = (pixel and 0xff) / 255f
                val brightness = 0.2126f * r + 0.7152f * g + 0.0722f * b
                isBlack[x][y] = brightness < threshold
            }
        }
        
        // Scan horizontal hatch vectors (outlines boundaries)
        for (y in 2 until h - 2 step 2) {
            var startX = -1
            for (x in 2 until w - 2) {
                val solid = isBlack[x][y]
                if (solid && startX == -1) {
                    startX = x
                } else if (!solid && startX != -1) {
                    val mmX1 = (startX - w / 2f) * (120f / w)
                    val mmX2 = (x - w / 2f) * (120f / w)
                    val mmY = -(y - h / 2f) * (120f / h)
                    paths.add(listOf(Offset(mmX1, mmY), Offset(mmX2, mmY)))
                    startX = -1
                }
            }
            if (startX != -1) {
                val mmX1 = (startX - w / 2f) * (120f / w)
                val mmX2 = ((w - 2) - w / 2f) * (120f / w)
                val mmY = -(y - h / 2f) * (120f / h)
                paths.add(listOf(Offset(mmX1, mmY), Offset(mmX2, mmY)))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return paths
}


