package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GCodeViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }
                
                if (showSplash) {
                    SplashScreen(
                        onTimeout = { showSplash = false }
                    )
                } else {
                    val gcodeViewModel: GCodeViewModel = viewModel()
                    MainScreen(
                        viewModel = gcodeViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        visible = true
        val duration = 2000L
        val steps = 100
        val stepTime = duration / steps
        for (i in 1..steps) {
            delay(stepTime)
            progress = i / 100f
        }
        visible = false
        delay(400) // Allow exit animation to fully play out
        onTimeout()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(600)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F111A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Dynamic CAD/CAM toolpath vector simulator (No static logo)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "toolpath_anim")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spindle_rotation"
                    )
                    
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laser_pulse"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerVal = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f
                        
                        // 1. Grid/Circular calibration axis
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            radius = maxRadius,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                        )
                        drawCircle(
                            color = Color(0xFF00E676).copy(alpha = 0.25f),
                            radius = maxRadius * 0.7f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        
                        // 2. Crosshairs index lines (Vectric CAM Job Setup style)
                        drawLine(
                            color = Color(0xFF90A4AE).copy(alpha = 0.3f),
                            start = Offset(centerVal.x - maxRadius, centerVal.y),
                            end = Offset(centerVal.x + maxRadius, centerVal.y),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFF90A4AE).copy(alpha = 0.3f),
                            start = Offset(centerVal.x, centerVal.y - maxRadius),
                            end = Offset(centerVal.x, centerVal.y + maxRadius),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // 3. Rotating spindle/laser tool animation
                        rotate(rotation, pivot = centerVal) {
                            // Virtual milling head
                            drawCircle(
                                color = Color.White,
                                radius = 6.dp.toPx(),
                                center = Offset(centerVal.x + maxRadius * 0.7f, centerVal.y)
                            )
                            // Glowing toolpath trace aura
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = pulse),
                                radius = 12.dp.toPx(),
                                center = Offset(centerVal.x + maxRadius * 0.7f, centerVal.y),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title
                Text(
                    text = "G-code editor",
                    color = Color(0xFF00E676), // Glowing green matching circuit traces
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "FluidNC Wi-Fi Control & Code Studio",
                    color = Color(0xFF90A4AE), // Slate grey
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Linear neon custom progress bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(180.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E5FF), // Cyan progress glow
                        trackColor = Color(0xFF1F2633)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "LOADING SYSTEM... ${(progress * 100).toInt()}%",
                        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

