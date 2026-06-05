package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cube.CubeView
import com.example.cube.NativeCubeLib
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

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
                        containerColor = Color(0xFF07070B) // Ultra premium deep obsidian space
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

// ----------------------------------------------------------------------------
// Rubik's Cube Structures & Data Presets
// ----------------------------------------------------------------------------

data class RubikLevel(
    val id: Int,
    val name: String,
    val difficulty: String,
    val stars: Int,
    val scrambles: Int,
    val desc: String,
    val accentColor: Color
)

val RubikLevels = listOf(
    RubikLevel(1, "Learning Loop", "Beginner", 1, 3, "Only 3 random turns! Perfect for standard mechanics.", Color(0xFF00FFCC)),
    RubikLevel(2, "Novice Twister", "Easy", 2, 6, "6 scrambles. Requires standard pattern recognition to re-align.", Color(0xFF3498DB)),
    RubikLevel(3, "Intermediate Spin", "Medium", 3, 12, "12 scrambles. Perfect practice for casual speed solvers.", Color(0xFFF1C40F)),
    RubikLevel(4, "Expert Grid", "Hard", 4, 22, "22 scrambles. Fully chaotic state. Prepare your speedcubing algorithms!", Color(0xFFE67E22)),
    RubikLevel(5, "Grandmaster Matrix", "Very Hard", 5, 40, "40 scrambles. Total entropic decay. Only elite Rubik's masters.", Color(0xFFE74C3C)),
    RubikLevel(6, "Infinite Entropy", "Expert", 5, 60, "60 random turns. Deep-space disorder! Can you solve the ultimate puzzle?", Color(0xFF9B59B6))
).distinctBy { it.id } // safety remove duplicate levels

data class ColorThemePreset(val name: String, val r: Float, val g: Float, val b: Float, val colorAccent: Color)

val ThemePresets = listOf(
    ColorThemePreset("Hologram Cyan", 0.0f, 0.82f, 1.00f, Color(0xFF00E5FF)),
    ColorThemePreset("Infrared Orange", 1.0f, 0.35f, 0.10f, Color(0xFFFF5722)),
    ColorThemePreset("Acid Emerald", 0.15f, 0.95f, 0.45f, Color(0xFF00E676)),
    ColorThemePreset("Cyber Fuchsia", 1.0f, 0.15f, 0.65f, Color(0xFFF50057)),
    ColorThemePreset("Solar Gold", 1.0f, 0.80f, 0.05f, Color(0xFFFFD600))
)

data class SequenceStep(val name: String, val axis: Int, val layer: Int, val direction: Int)
data class CubeAlgorithm(val name: String, val difficulty: String, val formula: String, val steps: List<SequenceStep>)

val SpeedtubeAlgorithms = listOf(
    CubeAlgorithm(
        "Sexy Move", 
        "Essential", 
        "R U R' U'", 
        listOf(
            SequenceStep("R", 0, 1, 1),
            SequenceStep("U", 1, 1, 1),
            SequenceStep("R'", 0, 1, -1),
            SequenceStep("U'", 1, 1, -1)
        )
    ),
    CubeAlgorithm(
        "The Sune", 
        "Intermediate", 
        "R U R' U R U2 R'", 
        listOf(
            SequenceStep("R", 0, 1, 1),
            SequenceStep("U", 1, 1, 1),
            SequenceStep("R'", 0, 1, -1),
            SequenceStep("U", 1, 1, 1),
            SequenceStep("R", 0, 1, 1),
            SequenceStep("U", 1, 1, 1),
            SequenceStep("U", 1, 1, 1),
            SequenceStep("R'", 0, 1, -1)
        )
    ),
    CubeAlgorithm(
        "Sledgehammer", 
        "Advanced", 
        "R' F R F'", 
        listOf(
            SequenceStep("R'", 0, 1, -1),
            SequenceStep("F", 2, 1, 1),
            SequenceStep("R", 0, 1, 1),
            SequenceStep("F'", 2, 1, -1)
        )
    )
)

data class SavedMove(val notation: String, val axis: Int, val layer: Int, val direction: Int)

// ----------------------------------------------------------------------------
// Simple Confetti Particle State
// ----------------------------------------------------------------------------
class ConfettiState(
    var x: Float,
    var y: Float,
    val size: Float,
    val color: Color,
    val velocityX: Float,
    val velocityY: Float,
    var angle: Float,
    val rotationSpeed: Float
)

@Composable
fun CubeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sp = remember { context.getSharedPreferences("rubik_records", Context.MODE_PRIVATE) }

    // Navigation and Visual configuration states
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Challenges, 1 = Controller Console, 2 = Academy Solves, 3 = Engine Settings
    var renderMode by remember { mutableIntStateOf(0) } // 0 = Standard, 1 = Neon, 2 = Wireframe
    var currentFps by remember { mutableFloatStateOf(60.0f) }
    var selectedThemeIdx by remember { mutableIntStateOf(0) }
    val activeTheme = ThemePresets[selectedThemeIdx]

    // Gamification state managers
    var selectedLevelId by remember { mutableIntStateOf(1) }
    val currentLevel = remember(selectedLevelId) { RubikLevels.first { it.id == selectedLevelId } }

    var moveCount by remember { mutableIntStateOf(0) }
    var isSolved by remember { mutableStateOf(false) }
    var gameTimerSeconds by remember { mutableIntStateOf(0) }
    var isGameStarted by remember { mutableStateOf(false) }
    var showCompleteModal by remember { mutableStateOf(false) }

    // Live logging and backstack
    val recentMovesLog = remember { mutableStateListOf<String>() }
    val moveBackStack = remember { mutableStateListOf<SavedMove>() }

    // Personal Records
    var personalBestMoves by remember(selectedLevelId) { mutableStateOf(sp.getInt("moves_level_$selectedLevelId", -1)) }
    var personalBestTime by remember(selectedLevelId) { mutableStateOf(sp.getInt("time_level_$selectedLevelId", -1)) }

    // Auto Playing Algorithm Thread Status
    var isPlayingAlgorithm by remember { mutableStateOf(false) }
    var activeStepName by remember { mutableStateOf("") }
    var activeStepIndex by remember { mutableIntStateOf(0) }
    var activeStepTotal by remember { mutableIntStateOf(0) }

    // Rotating Speed engine settings
    var activeAngleXSpin by remember { mutableStateOf(false) }
    var activeAngleYSpin by remember { mutableStateOf(false) }
    var activeAngleZSpin by remember { mutableStateOf(false) }

    // Particle engine variables
    var confettiTrigger by remember { mutableIntStateOf(0) }
    val particles = remember {
        mutableStateListOf<ConfettiState>().apply {
            val colors = listOf(
                Color(0xFF00E5FF), Color(0xFFFF5722), Color(0xFF00E676),
                Color(0xFFF50057), Color(0xFFFFD600), Color(0xFF9C27B0), Color(0xFFFFFFFF)
            )
            repeat(100) {
                add(
                    ConfettiState(
                        x = (100..900).random().toFloat(),
                        y = (-200..0).random().toFloat(),
                        size = (10..30).random().toFloat(),
                        color = colors.random(),
                        velocityX = (-4..4).random().toFloat(),
                        velocityY = (6..18).random().toFloat(),
                        angle = (0..360).random().toFloat(),
                        rotationSpeed = (-8..8).random().toFloat()
                    )
                )
            }
        }
    }

    // Thread loop updater for Level Solved Confetti Canvas
    LaunchedEffect(isSolved, showCompleteModal, confettiTrigger) {
        if (isSolved && showCompleteModal) {
            delay(16L) // ~60 FPS update
            for (p in particles) {
                p.y += p.velocityY
                p.x += p.velocityX
                p.angle += p.rotationSpeed
                if (p.y > 1600f) {
                    p.y = -50f
                    p.x = (100..900).random().toFloat()
                }
            }
            confettiTrigger++
        }
    }

    // Keep Uniform colors & Render mode updated with C++ Engine
    LaunchedEffect(renderMode, selectedThemeIdx) {
        NativeCubeLib.setRenderMode(renderMode)
        NativeCubeLib.setUniformColor(activeTheme.r, activeTheme.g, activeTheme.b, 1.0f)
    }

    // Toggle Axis Spining
    LaunchedEffect(activeAngleXSpin, activeAngleYSpin, activeAngleZSpin) {
        NativeCubeLib.toggleRotationAxis(activeAngleXSpin, activeAngleYSpin, activeAngleZSpin)
        NativeCubeLib.setRotationSpeed(
            if (activeAngleXSpin) 1.5f else 0f,
            if (activeAngleYSpin) 1.5f else 0f,
            if (activeAngleZSpin) 1.5f else 0f
        )
    }

    // Game Session Duration Timer Thread
    LaunchedEffect(isGameStarted, isSolved) {
        if (isGameStarted && !isSolved) {
            while (true) {
                delay(1000L)
                gameTimerSeconds++
            }
        }
    }

    // Controller actions and helpers
    fun triggerScramble() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        NativeCubeLib.scramble(currentLevel.scrambles)
        moveCount = 0
        isSolved = false
        gameTimerSeconds = 0
        isGameStarted = true
        showCompleteModal = false
        recentMovesLog.clear()
        moveBackStack.clear()
    }

    fun makeRubikMove(symbol: String, axis: Int, layer: Int, direction: Int) {
        if (isPlayingAlgorithm) return // block manual override during auto scripts
        
        if (!isGameStarted) {
            triggerScramble()
        }
        
        val success = NativeCubeLib.rotateLayer(axis, layer, direction)
        if (success) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            moveCount++
            
            // Record Move Back Stack for Undo
            moveBackStack.add(SavedMove(symbol, axis, layer, direction))
            
            // Display Log strip
            if (recentMovesLog.size >= 8) {
                recentMovesLog.removeAt(0)
            }
            recentMovesLog.add(symbol)
        }
    }

    // Satisfying Live Undo Step function
    fun undoLastMove() {
        if (moveBackStack.isNotEmpty() && !isSolved && !isPlayingAlgorithm) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val lastMove = moveBackStack.removeAt(moveBackStack.size - 1)
            // Undo is rotating key dimensions in opposite direction (-direction)
            val success = NativeCubeLib.rotateLayer(lastMove.axis, lastMove.layer, lastMove.direction * -1)
            if (success) {
                moveCount = maxOf(0, moveCount - 1)
                if (recentMovesLog.isNotEmpty()) {
                    recentMovesLog.removeAt(recentMovesLog.size - 1)
                }
            }
        }
    }

    // Solve State Checker Core Thread
    LaunchedEffect(isGameStarted, moveCount) {
        if (isGameStarted && !isSolved && !isPlayingAlgorithm) {
            delay(320L) // Wait slightly for OpenGL matrix to snap before checking solved hash states map
            if (NativeCubeLib.isSolved()) {
                isSolved = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                
                // Record personal best values
                val bestMoves = sp.getInt("moves_level_$selectedLevelId", -1)
                val bestTime = sp.getInt("time_level_$selectedLevelId", -1)

                val editor = sp.edit()
                if (bestMoves == -1 || moveCount < bestMoves) {
                    editor.putInt("moves_level_$selectedLevelId", moveCount)
                    personalBestMoves = moveCount
                }
                if (bestTime == -1 || gameTimerSeconds < bestTime) {
                    editor.putInt("time_level_$selectedLevelId", gameTimerSeconds)
                    personalBestTime = gameTimerSeconds
                }
                editor.commit()
                showCompleteModal = true
            }
        }
    }

    // Play Sequential Algo script coroutine helper
    fun executeAlgorithmSeq(algorithmName: String, steps: List<SequenceStep>) {
        if (isPlayingAlgorithm) return
        isPlayingAlgorithm = true
        activeStepName = algorithmName
        activeStepTotal = steps.size
        
        // Temporarily reset rotation and auto spin to show cleanly
        val prevXSpin = activeAngleXSpin
        val prevYSpin = activeAngleYSpin
        val prevZSpin = activeAngleZSpin
        activeAngleXSpin = false
        activeAngleYSpin = false
        activeAngleZSpin = false
        
        recentMovesLog.clear()
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val runner = object : Runnable {
                var stepIdx = 0
                override fun run() {
                    if (stepIdx < steps.size) {
                        activeStepIndex = stepIdx + 1
                        val s = steps[stepIdx]
                        
                        // Rotates layer natively
                        NativeCubeLib.rotateLayer(s.axis, s.layer, s.direction)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        recentMovesLog.add(s.name)
                        
                        stepIdx++
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 500L)
                    } else {
                        isPlayingAlgorithm = false
                        activeAngleXSpin = prevXSpin
                        activeAngleYSpin = prevYSpin
                        activeAngleZSpin = prevZSpin
                    }
                }
            }
            runner.run()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Futuristic App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "3D RUBIK PRO",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.5.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = Color.White
                )
                Text(
                    text = "High-Fidelity Speedcubing Simulator",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
            }
            
            // Neon Level Indicator Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(currentLevel.accentColor.copy(alpha = 0.15f))
                    .border(1.dp, currentLevel.accentColor.copy(alpha = 0.3f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentLevel.difficulty.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = currentLevel.accentColor,
                    letterSpacing = 1.sp
                )
            }
        }

        // Live Gaming HUD panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101016))
                .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MOVES", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                Text(
                    text = if (isGameStarted) "$moveCount" else "--",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = currentLevel.accentColor
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DURATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                val minutes = gameTimerSeconds / 60
                val seconds = gameTimerSeconds % 60
                Text(
                    text = if (isGameStarted) "%02d:%01d".format(minutes, seconds).replace(":", "m ") + "s" else "00s",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("SOLVED TRACKER", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isSolved) Color(0xFF10B981) else if (isGameStarted) Color(0xFFFBBF24) else Color(0xFF4B5563))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSolved) "COMPLETED" else if (isGameStarted) "SOLVING" else "READY",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSolved) Color(0xFF10B981) else if (isGameStarted) Color(0xFFFBBF24) else Color(0xFF6B7280)
                    )
                }
            }
        }

        // Beautiful 3D OpenGL Surface View Frame inside Obsidian Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF040407))
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E2135), Color(0xFF0F101A))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            // Live Mounting GLES3 Canvas Surface
            CubeView(
                modifier = Modifier.fillMaxSize(),
                onFpsUpdated = { fps ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        currentFps = fps
                    }
                }
            )

            // Neon Performance overlay (displays GLES engine status)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE006060A))
                    .border(1.dp, Color(0xFF1F2235), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (currentFps > 45) Color(0xFF10B981) else Color(0xFFFBBF24))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GLES 3.0: ${"%.1f".format(currentFps)} FPS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00E5FF)
                )
            }

            // Real Time Swipe instruction overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xB3101017))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Swipe background to inspect 3D camera orbits",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFA1A1AA)
                )
            }

            // Auto Playing algorithm overlay state
            if (isPlayingAlgorithm) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D0D15))
                            .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "DEMONSTRATING ALGORITHM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFF9E9E9E)
                        )
                        Text(
                            text = activeStepName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Step $activeStepIndex / $activeStepTotal",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // High Fidelity Celebration Canvas & Particle engine
            if (isSolved && showCompleteModal) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (p in particles) {
                        rotate(p.angle, pivot = androidx.compose.ui.geometry.Offset(p.x, p.y)) {
                            drawRect(
                                color = p.color,
                                topLeft = androidx.compose.ui.geometry.Offset(p.x, p.y),
                                size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                            )
                        }
                    }
                }

                // Solved result card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE608090F))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0A1C16))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "CUBE SOLVED! 🎉",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Excellent speedcubing patterns and solutions!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9ACCA9),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Moves Count", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7BA686))
                                Text("$moveCount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val mins = gameTimerSeconds / 60
                                val secs = gameTimerSeconds % 60
                                Text("Time Elapsed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7BA686))
                                Text("%02d:%02d".format(mins, secs), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showCompleteModal = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF11261B), contentColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("DISMISS", fontWeight = FontWeight.Bold)
                            }

                            if (selectedLevelId < RubikLevels.size) {
                                Button(
                                    onClick = {
                                        selectedLevelId++
                                        triggerScramble()
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("NEXT LEVEL", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------------------------
        // Obsidian Style Tab bar selection row
        // ----------------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF101016))
                .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf("🏆 LEVEL", "🕹️ MOVE", "🪄 SEXY", "⚙️ ENGINE")
            tabs.forEachIndexed { index, title ->
                val isSelected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF1F2235) else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeTab = index
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF9CA3AF)
                    )
                }
            }
        }

        // Live Dynamic move history strip log (always visible if elements exist)
        AnimatedVisibility(
            visible = recentMovesLog.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0D14))
                    .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTATION LINE:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    recentMovesLog.forEach { valStr ->
                        Text(
                            text = valStr,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (valStr.endsWith("'")) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear History",
                    tint = Color(0xFF4B5563),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            recentMovesLog.clear()
                            moveBackStack.clear()
                        }
                )
            }
        }

        // ----------------------------------------------------------------------------
        // Tab Content Router
        // ----------------------------------------------------------------------------
        when (activeTab) {
            0 -> {
                // Tab 0: CHALLENGES LIST & CHOOSE CAROUSEL
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101016))
                        .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CHALLENGES PLAYLIST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF9CA3AF)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RubikLevels.forEach { lvl ->
                            val isSelected = selectedLevelId == lvl.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) lvl.accentColor.copy(alpha = 0.12f) else Color(0xFF15151F))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) lvl.accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedLevelId = lvl.id
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dot badge
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(lvl.accentColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Lvl ${lvl.id}: ${lvl.name}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row {
                                            repeat(lvl.stars) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Difficulty Point",
                                                    tint = lvl.accentColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${lvl.scrambles} scrambles • ${lvl.difficulty}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }

                                if (isSelected) {
                                    Button(
                                        onClick = { triggerScramble() },
                                        colors = ButtonDefaults.buttonColors(containerColor = lvl.accentColor, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("scramble_btn_${lvl.id}")
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("START", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black))
                                    }
                                }
                            }
                        }
                    }

                    // Level Info Description callout Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF15151F))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Descriptor Icon",
                            tint = currentLevel.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = currentLevel.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE5E7EB)
                        )
                    }

                    // Personal Highscores statistics
                    if (personalBestMoves != -1 || personalBestTime != -1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentLevel.accentColor.copy(alpha = 0.05f))
                                .border(1.dp, currentLevel.accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🏆 LEVEL RECORD BESTS:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = currentLevel.accentColor
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (personalBestMoves != -1) {
                                    Text(
                                        text = "$personalBestMoves moves",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                if (personalBestTime != -1) {
                                    val mins = personalBestTime / 60
                                    val secs = personalBestTime % 60
                                    Text(
                                        text = "%02d:%02d".format(mins, secs),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Tab 1: INTERACTIVE ADVANCED CONTROL RIG
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101016))
                        .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "MANUAL SPEEDCUBE STEPS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF9CA3AF)
                    )

                    // Touch Gamepad Grid Control
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "TOP FACE (U)",
                                    cw = "U",
                                    ccw = "U'",
                                    color = Color(0xFFFFFFFF),
                                    onCw = { makeRubikMove("U", 1, 1, 1) },
                                    onCcw = { makeRubikMove("U'", 1, 1, -1) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "BOTTOM FACE (D)",
                                    cw = "D",
                                    ccw = "D'",
                                    color = Color(0xFFFFEB3B),
                                    onCw = { makeRubikMove("D", 1, -1, 1) },
                                    onCcw = { makeRubikMove("D'", 1, -1, -1) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "FRONT FACE (F)",
                                    cw = "F",
                                    ccw = "F'",
                                    color = Color(0xFF00E676),
                                    onCw = { makeRubikMove("F", 2, 1, 1) },
                                    onCcw = { makeRubikMove("F'", 2, 1, -1) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "BACK FACE (B)",
                                    cw = "B",
                                    ccw = "B'",
                                    color = Color(0xFF29B6F6),
                                    onCw = { makeRubikMove("B", 2, -1, 1) },
                                    onCcw = { makeRubikMove("B'", 2, -1, -1) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "RIGHT FACE (R)",
                                    cw = "R",
                                    ccw = "R'",
                                    color = Color(0xFFFF5252),
                                    onCw = { makeRubikMove("R", 0, 1, 1) },
                                    onCcw = { makeRubikMove("R'", 0, 1, -1) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ConsoleFaceControl(
                                    label = "LEFT FACE (L)",
                                    cw = "L",
                                    ccw = "L'",
                                    color = Color(0xFFFF9800),
                                    onCw = { makeRubikMove("L", 0, -1, 1) },
                                    onCcw = { makeRubikMove("L'", 0, -1, -1) }
                                )
                            }
                        }
                    }

                    // Tactical Solved / State Controls Panel (Scramble, Reset, Satisfying UNDO!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { undoLastMove() },
                            enabled = moveBackStack.isNotEmpty() && !isSolved,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F2B2B), 
                                contentColor = Color(0xFFFF5252),
                                disabledContainerColor = Color(0xFF1A1515),
                                disabledContentColor = Color(0xFF5A4444)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("↩️ UNDO LAST", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                NativeCubeLib.resetCube()
                                moveCount = 0
                                isSolved = false
                                gameTimerSeconds = 0
                                isGameStarted = false
                                showCompleteModal = false
                                recentMovesLog.clear()
                                moveBackStack.clear()
                            },
                            modifier = Modifier.weight(0.9f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1E2B), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("RESET CUBE", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { triggerScramble() },
                            modifier = Modifier.weight(1.1f),
                            colors = ButtonDefaults.buttonColors(containerColor = currentLevel.accentColor, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SCRAMBLE", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }

            2 -> {
                // Tab 2: SPEEDCUBING ACADEMY (Auto algorithm executor sequences)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101016))
                        .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SPEEDCUBE ALGORITHM SIMULATOR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF9CA3AF)
                    )

                    Text(
                        text = "Learn how professional speedcubers solve their patterns. Tap any sequence to play it live on the C++ OpenGL model below!",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpeedtubeAlgorithms.forEach { algo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF15151F))
                                    .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = algo.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = algo.difficulty,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF00E5FF)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            executeAlgorithmSeq(algo.name, algo.steps)
                                        },
                                        enabled = !isPlayingAlgorithm,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6), contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("DEMO LIVE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }

                                // Interactive formula tag
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0D0D14))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = algo.formula,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Tab 3: ENGINE GEAR AND COSMIC VALUE SETUP
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101016))
                        .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Text(
                        text = "ENGINE GRAPHICS SYSTEM",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF9CA3AF)
                    )

                    // 1: Render modes toggler
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OpenGL Shading Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE5E7EB)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf("Classic 3D", "Solid Glow", "Holo Wire")
                            modes.forEachIndexed { idx, label ->
                                val isSelected = renderMode == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF15151F))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            renderMode = idx
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                        ),
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 2: Glow theme color presets chooser
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Engine Sticker Glow Palette",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE5E7EB)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemePresets.forEachIndexed { idx, p ->
                                val isSelected = selectedThemeIdx == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) p.colorAccent.copy(alpha = 0.25f) else Color(0xFF15151F))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) p.colorAccent else Color(0xFF1D1F2B),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedThemeIdx = idx
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(p.colorAccent)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = p.name.split(" ").last(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3: Spinning Axis and Gyro Controllers
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OpenGL Continuous Idle Spin",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE5E7EB)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpinAxisRow(label = "Rotate X-Axis", isOn = activeAngleXSpin, onToggle = { activeAngleXSpin = !activeAngleXSpin }, accent = Color(0xFFFF5252), modifier = Modifier.weight(1f))
                            SpinAxisRow(label = "Rotate Y-Axis", isOn = activeAngleYSpin, onToggle = { activeAngleYSpin = !activeAngleYSpin }, accent = Color(0xFF00FFCC), modifier = Modifier.weight(1f))
                            SpinAxisRow(label = "Rotate Z-Axis", isOn = activeAngleZSpin, onToggle = { activeAngleZSpin = !activeAngleZSpin }, accent = Color(0xFFF1C40F), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpinAxisRow(
    label: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOn) accent.copy(alpha = 0.15f) else Color(0xFF15151F))
            .border(1.dp, if (isOn) accent else Color(0xFF222435), RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isOn) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = if (isOn) accent else Color(0xFF6B7280)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
fun ConsoleFaceControl(
    label: String,
    cw: String,
    ccw: String,
    color: Color,
    onCw: () -> Unit,
    onCcw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15151F))
            .border(1.dp, Color(0xFF1D1F2B), RoundedCornerShape(14.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Colored face badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
                color = if (color == Color(0xFFFFFFFF)) Color.White else color
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onCw,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1E2C), contentColor = Color.White),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(cw, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
            }
            Button(
                onClick = onCcw,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1E2C), contentColor = Color(0xFF00E5FF)),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(ccw, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = Color(0xFF00E5FF))
            }
        }
    }
}
