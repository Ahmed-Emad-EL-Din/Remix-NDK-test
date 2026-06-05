package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cube.CubeView
import com.example.cube.NativeCubeLib
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup persistent crash log/diagnostics system
        val sharedPrefs = getSharedPreferences("engine_debug", MODE_PRIVATE)
        val lastCrashTrace = sharedPrefs.getString("last_crash_trace", null)
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            val fullTrace = sw.toString()
            
            sharedPrefs.edit()
                .putString("last_crash_trace", "Thread: ${thread.name}\nException Message: ${throwable.message}\n\n$fullTrace")
                .commit()
            
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Enable edge-to-edge drawing
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                var crashTrace by remember { mutableStateOf(lastCrashTrace) }
                
                if (crashTrace != null) {
                    DiagnosticScreen(
                        trace = crashTrace!!,
                        onClearAndRestart = {
                            sharedPrefs.edit().remove("last_crash_trace").commit()
                            crashTrace = null
                        },
                        context = this
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color(0xFF0D0D12) // Deep space obsidian background
                    ) { innerPadding ->
                        CubeDashboardScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticScreen(
    trace: String,
    onClearAndRestart: () -> Unit,
    context: android.content.Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0A0A)) // Deep warning red-black tint
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Red Pulsing Hazard/Warning Symbol
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFE74C3C).copy(alpha = 0.15f))
                .border(2.dp, Color(0xFFE74C3C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠️",
                style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "ENGINE DIAGNOSTICS",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            ),
            color = Color(0xFFE74C3C)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "An exception on the OpenGL or JNI layer was safely intercepted. Please review the trace log below:",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB3A2A2),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Log trace console
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF050303))
                .border(1.dp, Color(0xFF331C1C), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = trace,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFA6A6)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.Context
                        ?: return@Button
                    try {
                        val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Crash Trace", trace)
                        manager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied log to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        // ignore
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1212), contentColor = Color(0xFFFFA6A6)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("COPY TRACE", fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = onClearAndRestart,
                modifier = Modifier.weight(1.2f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CLEAR & RELAUNCH", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// Pre-defined color presets for the neon cube styling
data class ColorPreset(
    val name: String,
    val r: Float,
    val g: Float,
    val b: Float,
    val composeColor: Color
)

val ColorPresets = listOf(
    ColorPreset("Cyan Glow", 0.0f, 0.82f, 1.0f, Color(0xFF00D1FF)),
    ColorPreset("Orchid Purple", 0.73f, 0.33f, 0.83f, Color(0xFFBA55D3)),
    ColorPreset("Mint Green", 0.18f, 0.80f, 0.44f, Color(0xFF2ECC71)),
    ColorPreset("Solar Yellow", 0.95f, 0.80f, 0.10f, Color(0xFFF1C40F)),
    ColorPreset("Fire Coral", 0.95f, 0.35f, 0.38f, Color(0xFFF35C61)),
    ColorPreset("Sunset Orange", 0.95f, 0.60f, 0.15f, Color(0xFFF39C12))
)

@Composable
fun CubeDashboardScreen(modifier: Modifier = Modifier) {
    // Shared states feeding back configuration values directly into JNI
    var renderMode by remember { mutableIntStateOf(0) } // 0 = Poly-Color Face, 1 = Neon Solid, 2 = Wireframe
    
    var speedX by remember { mutableFloatStateOf(1.0f) }
    var speedY by remember { mutableFloatStateOf(0.8f) }
    var speedZ by remember { mutableFloatStateOf(0.5f) }

    var rotateX by remember { mutableStateOf(true) }
    var rotateY by remember { mutableStateOf(true) }
    var rotateZ by remember { mutableStateOf(true) }

    var selectedColorIndex by remember { mutableIntStateOf(0) }
    
    // FPS stats monitored directly from OpenGLES Frame cycle
    var currentFps by remember { mutableFloatStateOf(60.0f) }

    // Sync parameters initially into our loaded JNI C++ layer
    LaunchedEffect(renderMode) {
        NativeCubeLib.setRenderMode(renderMode)
    }
    LaunchedEffect(speedX, speedY, speedZ) {
        NativeCubeLib.setRotationSpeed(speedX, speedY, speedZ)
    }
    LaunchedEffect(rotateX, rotateY, rotateZ) {
        NativeCubeLib.toggleRotationAxis(rotateX, rotateY, rotateZ)
    }
    LaunchedEffect(selectedColorIndex) {
        val preset = ColorPresets[selectedColorIndex]
        NativeCubeLib.setUniformColor(preset.r, preset.g, preset.b, 1.0f)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Row
        Text(
            text = "NDK 3D ENGINE",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Text(
            text = "Native C++ & OpenGL ES 3.0 Real-time Rendering Pipeline",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E9F),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Rendering Display Area (Wrapped in a beautiful textured card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF08080C))
                .border(2.dp, Brush.linearGradient(
                    colors = listOf(Color(0xFF222230), Color(0xFF15151F))
                ), RoundedCornerShape(24.dp))
        ) {
            // Our 3D Custom GL Surface View Component
            CubeView(
                modifier = Modifier.fillMaxSize(),
                onFpsUpdated = { fpsVal ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        currentFps = fpsVal
                    }
                }
            )

            // Live Performance HUD overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC050508))
                    .border(1.dp, Color(0xFF2A2A3F), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (currentFps > 45) Color(0xFF2ECC71) else Color(0xFFF1C40F))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "JNI: ${"%.1f".format(currentFps)} FPS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00D1FF)
                )
            }

            // Swipe/Drag gesture helpful indicator
            Text(
                text = "Swipe to rotate manual controls",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x66FFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dashboard Settings Panel Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            // Section 1: Render Mode Selector
            Text(
                text = "RENDERING SHADER MODE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F)
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf("Poly-Color", "Neon Solid", "Wireframe")
                modes.forEachIndexed { idx, label ->
                    val isSelected = renderMode == idx
                    val testTagValue = when(idx) {
                        0 -> "mode_poly_color"
                        1 -> "mode_neon"
                        else -> "mode_wireframe"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF00D1FF) else Color(0xFF1C1C28))
                            .clickable { renderMode = idx }
                            .padding(vertical = 10.dp)
                            .testTag(testTagValue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Uniform Tint Palette (Visually visible only if mode needs uniform color)
            if (renderMode != 0) {
                Text(
                    text = "NEON COLOR SHADER TINT",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                    color = Color(0xFF8E8E9F)
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ColorPresets.forEachIndexed { index, preset ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(preset.composeColor)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Section 2: Rotation Axis Toggles
            Text(
                text = "ACTIVE TRANSFORMATION AXES",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Axis-X Switch block
                AxisToggleBlock(
                    label = "X-Axis",
                    isChecked = rotateX,
                    onCheckedChange = { rotateX = it },
                    testTag = "switch_axis_x",
                    modifier = Modifier.weight(1f)
                )
                // Axis-Y Switch block
                AxisToggleBlock(
                    label = "Y-Axis",
                    isChecked = rotateY,
                    onCheckedChange = { rotateY = it },
                    testTag = "switch_axis_y",
                    modifier = Modifier.weight(1f)
                )
                // Axis-Z Switch block
                AxisToggleBlock(
                    label = "Z-Axis",
                    isChecked = rotateZ,
                    onCheckedChange = { rotateZ = it },
                    testTag = "switch_axis_z",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Rotation Speed Sliders
            Text(
                text = "VELOCITY COEFFICIENTS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // X-Axis Speed Slider (Only relevant if dynamic auto-rotation on X is active)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("X Rotate Speed", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("${"%.2f".format(speedX)}x", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00D1FF))
                }
                Slider(
                    value = speedX,
                    onValueChange = { speedX = it },
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00D1FF),
                        activeTrackColor = Color(0xFF00D1FF),
                        inactiveTrackColor = Color(0xFF1E1E2F)
                    ),
                    modifier = Modifier.testTag("slider_speed_x")
                )
            }

            // Y-Axis Speed Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Y Rotate Speed", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("${"%.2f".format(speedY)}x", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00D1FF))
                }
                Slider(
                    value = speedY,
                    onValueChange = { speedY = it },
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00D1FF),
                        activeTrackColor = Color(0xFF00D1FF),
                        inactiveTrackColor = Color(0xFF1E1E2F)
                    ),
                    modifier = Modifier.testTag("slider_speed_y")
                )
            }

            // Z-Axis Speed Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Z Rotate Speed", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("${"%.2f".format(speedZ)}x", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00D1FF))
                }
                Slider(
                    value = speedZ,
                    onValueChange = { speedZ = it },
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00D1FF),
                        activeTrackColor = Color(0xFF00D1FF),
                        inactiveTrackColor = Color(0xFF1E1E2F)
                    ),
                    modifier = Modifier.testTag("slider_speed_z")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reset transform settings button
            Button(
                onClick = {
                    speedX = 1.0f
                    speedY = 0.8f
                    speedZ = 0.5f
                    rotateX = true
                    rotateY = true
                    rotateZ = true
                    selectedColorIndex = 0
                    renderMode = 0
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F1F2F),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Settings",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESTORE ENGINE DEFAULTS", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AxisToggleBlock(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isChecked) Color(0xFF1A2633) else Color(0xFF181822))
            .border(
                width = 1.dp,
                color = if (isChecked) Color(0xFF00D1FF).copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (isChecked) Color(0xFF00D1FF) else Color(0xFF8E8E9F)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00D1FF),
                checkedTrackColor = Color(0xFF00D1FF).copy(alpha = 0.2f),
                uncheckedThumbColor = Color(0xFF5E5E6E),
                uncheckedTrackColor = Color(0xFF2C2C3D)
            )
        )
    }
}
