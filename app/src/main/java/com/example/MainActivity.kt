package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
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
// Rubik's Cube Levels and Schemes
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
    RubikLevel(1, "Learning Loop", "Beginner", 1, 3, "Only 3 random turns! Perfect for learning standard mechanics and testing combinations.", Color(0xFF2ECC71)),
    RubikLevel(2, "Novice Twister", "Easy", 2, 6, "6 scrambles. Requires a bit of pattern recognition to re-align.", Color(0xFF3498DB)),
    RubikLevel(3, "Intermediate Spin", "Medium", 3, 12, "12 scrambles. Perfect practice for casual solvers looking for a moderate challenge.", Color(0xFFF1C40F)),
    RubikLevel(4, "Expert Grid", "Hard", 4, 22, "22 scrambles. Reaches a fully chaotic state. Prepare your speedcubing algorithms!", Color(0xFFE67E22)),
    RubikLevel(5, "Grandmaster Matrix", "Very Hard", 5, 40, "40 scrambles. Total entropic decay. Only elite Rubik's masters should attempt.", Color(0xFFE74C3C)),
    RubikLevel(6, "Infinite Entropy", "Expert", 5, 60, "60 random turns. Deep-space disorder! Can you solve the ultimate puzzle?", Color(0xFF9B59B6))
)

@Composable
fun CubeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("rubik_records", Context.MODE_PRIVATE) }

    // Core configuration states
    var renderMode by remember { mutableIntStateOf(0) } // 0 = Standard, 1 = Neon, 2 = Wireframe
    var currentFps by remember { mutableFloatStateOf(60.0f) }

    // Gamification states
    var selectedLevelId by remember { mutableIntStateOf(1) }
    val currentLevel = remember(selectedLevelId) { RubikLevels.first { it.id == selectedLevelId } }

    var moveCount by remember { mutableIntStateOf(0) }
    var isSolved by remember { mutableStateOf(false) }
    var gameTimerSeconds by remember { mutableIntStateOf(0) }
    var isGameStarted by remember { mutableStateOf(false) }
    var showCompleteModal by remember { mutableStateOf(false) }

    val recentMoves = remember { mutableStateListOf<String>() }

    // Records
    var personalBestMoves by remember(selectedLevelId) { mutableStateOf(sp.getInt("moves_level_$selectedLevelId", -1)) }
    var personalBestTime by remember(selectedLevelId) { mutableStateOf(sp.getInt("time_level_$selectedLevelId", -1)) }

    // Synchronize Shader Modes
    LaunchedEffect(renderMode) {
        NativeCubeLib.setRenderMode(renderMode)
    }

    // Active Timer Thread
    LaunchedEffect(isGameStarted, isSolved) {
        if (isGameStarted && !isSolved) {
            while (true) {
                delay(1000L)
                gameTimerSeconds++
            }
        }
    }

    // Helper functions
    fun triggerScramble() {
        NativeCubeLib.scramble(currentLevel.scrambles)
        moveCount = 0
        isSolved = false
        gameTimerSeconds = 0
        isGameStarted = true
        showCompleteModal = false
        recentMoves.clear()
    }

    fun makeRubikMove(symbol: String, axis: Int, layer: Int, direction: Int) {
        if (!isGameStarted) {
            triggerScramble()
        }
        val success = NativeCubeLib.rotateLayer(axis, layer, direction)
        if (success) {
            moveCount++
            if (recentMoves.size >= 8) {
                recentMoves.removeAt(0)
            }
            recentMoves.add(symbol)
        }
    }

    // Polled Solved-State Checker
    LaunchedEffect(isGameStarted, moveCount) {
        if (isGameStarted && !isSolved) {
            delay(280L) // Wait slightly for layer transition to snap completely in C++
            if (NativeCubeLib.isSolved()) {
                isSolved = true
                
                // Record check & commit
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

    // Main scrollable grid
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game Header
        Text(
            text = "3D RUBIK'S CUBE",
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
            text = "Modern OpenGL ES 3.0 & Native C++ Speedcube Game",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E9F),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Game HUD / Stats Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MOVES", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E9F))
                Text(
                    text = if (isGameStarted) "$moveCount" else "--",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                    color = currentLevel.accentColor
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GAME TIME", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E9F))
                val minutes = gameTimerSeconds / 60
                val seconds = gameTimerSeconds % 60
                Text(
                    text = if (isGameStarted) "%02d:%02d".format(minutes, seconds) else "00:00",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("LEVEL STATUS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E9F))
                Text(
                    text = if (isSolved) "SOLVED 🎉" else if (isGameStarted) "PLAYING" else "READY",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (isSolved) Color(0xFF2ECC71) else if (isGameStarted) Color(0xFFF1C40F) else Color(0xFF8E8E9F)
                )
            }
        }

        // Beautiful 3D OpenGL Surface View Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF07070A))
                .border(2.dp, Brush.linearGradient(
                    colors = listOf(Color(0xFF1F1F2E), Color(0xFF0F0F15))
                ), RoundedCornerShape(24.dp))
        ) {
            // Mounting GLES3 Canvas Surface
            CubeView(
                modifier = Modifier.fillMaxSize(),
                onFpsUpdated = { fpsVal ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        currentFps = fpsVal
                    }
                }
            )

            // GLES + NDK Performance Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xDD040406))
                    .border(1.dp, Color(0xFF1F1F2F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (currentFps > 45) Color(0xFF2ECC71) else Color(0xFFF39C12))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GLES 3.0: ${"%.1f".format(currentFps)} FPS",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color(0xFF00D1FF)
                )
            }

            // Swipe instruction HUD
            Text(
                text = "Hold & Swipe background to inspect 3D layers",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x55FFFFFF),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )

            // Celebratory Win Overlay
            if (isSolved && showCompleteModal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC080C0B))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0E1A14))
                            .border(1.dp, Color(0xFF2ECC71).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "LEVEL SOLVED! 🎉",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFF2ECC71)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Amazing speedcubing pattern recognition!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A499),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Moves taken", style = MaterialTheme.typography.labelSmall, color = Color(0xFF758F81))
                                Text("$moveCount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                val mins = gameTimerSeconds / 60
                                val secs = gameTimerSeconds % 60
                                Text("Time taken", style = MaterialTheme.typography.labelSmall, color = Color(0xFF758F81))
                                Text("%02d:%02d".format(mins, secs), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showCompleteModal = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142019), contentColor = Color(0xFF2ECC71)),
                                shape = RoundedCornerShape(10.dp)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("NEXT LEVEL", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Move Log Trace Bar (Only visible if moves are made)
        AnimatedVisibility(
            visible = recentMoves.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0F14))
                    .border(1.dp, Color(0xFF1A1A26), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MOVE LOG:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF8E8E9F))
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    recentMoves.forEach { move ->
                        Text(
                            text = move,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (move.endsWith("'")) Color(0xFF00D1FF) else Color.White
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Undo Settings",
                    tint = Color(0xFF5E5E6E),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { recentMoves.clear() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Level Select Carousel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "SELECT LEVEL CHALLENGE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Horizontal scrolling or vertical options list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RubikLevels.forEach { level ->
                    val isSelected = selectedLevelId == level.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) level.accentColor.copy(alpha = 0.12f) else Color(0xFF181824))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) level.accentColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedLevelId = level.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Difficulty badge
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(level.accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Lvl ${level.id}: ${level.name}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row {
                                    repeat(level.stars) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Star",
                                            tint = level.accentColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${level.scrambles}-turn random chaos • ${level.difficulty}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E8E9F)
                            )
                        }

                        if (isSelected) {
                            Button(
                                onClick = { triggerScramble() },
                                colors = ButtonDefaults.buttonColors(containerColor = level.accentColor, contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("scramble_btn_$level.id")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Scramble", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("START", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Level Description Callout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF181824))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Level info",
                    tint = currentLevel.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentLevel.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC5C5D2)
                )
            }

            // Records Box inside Level Select
            if (personalBestMoves != -1 || personalBestTime != -1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(currentLevel.accentColor.copy(alpha = 0.05f))
                        .border(1.dp, currentLevel.accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🏆 PERSONAL RECORD:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = currentLevel.accentColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (personalBestMoves != -1) {
                            Text(
                                text = "Moves: $personalBestMoves",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        if (personalBestTime != -1) {
                            val mins = personalBestTime / 60
                            val secs = personalBestTime % 60
                            Text(
                                text = "Time: %02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Face Turns Control Rig Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "INTERACTIVE LAYER MOVES",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F)
            )
            Text(
                text = "Standard speedcube turning notation. Prime notations (') rotate counter-clockwise.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x66FFFFFF),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Grid of 6 Faces controls (FaceControlBlock layout)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "TOP FACE (U)",
                            cwNotation = "U",
                            ccwNotation = "U'",
                            color = Color(0xFFEEEEEE),
                            onClickCw = { makeRubikMove("U", 1, 1, 1) },
                            onClickCcw = { makeRubikMove("U'", 1, 1, -1) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "BOTTOM FACE (D)",
                            cwNotation = "D",
                            ccwNotation = "D'",
                            color = Color(0xFFF1C40F),
                            onClickCw = { makeRubikMove("D", 1, -1, 1) },
                            onClickCcw = { makeRubikMove("D'", 1, -1, -1) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "FRONT FACE (F)",
                            cwNotation = "F",
                            ccwNotation = "F'",
                            color = Color(0xFF2ECC71),
                            onClickCw = { makeRubikMove("F", 2, 1, 1) },
                            onClickCcw = { makeRubikMove("F'", 2, 1, -1) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "BACK FACE (B)",
                            cwNotation = "B",
                            ccwNotation = "B'",
                            color = Color(0xFF3498DB),
                            onClickCw = { makeRubikMove("B", 2, -1, 1) },
                            onClickCcw = { makeRubikMove("B'", 2, -1, -1) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "RIGHT FACE (R)",
                            cwNotation = "R",
                            ccwNotation = "R'",
                            color = Color(0xFFE74C3C),
                            onClickCw = { makeRubikMove("R", 0, 1, 1) },
                            onClickCcw = { makeRubikMove("R'", 0, 1, -1) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FaceControlBlock(
                            label = "LEFT FACE (L)",
                            cwNotation = "L",
                            ccwNotation = "L'",
                            color = Color(0xFFE67E22),
                            onClickCw = { makeRubikMove("L", 0, -1, 1) },
                            onClickCcw = { makeRubikMove("L'", 0, -1, -1) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game Control Actions footer (Reset cube, Scramble)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        NativeCubeLib.resetCube()
                        moveCount = 0
                        isSolved = false
                        gameTimerSeconds = 0
                        isGameStarted = false
                        showCompleteModal = false
                        recentMoves.clear()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_puzzle_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F2F), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Puzzle", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESET CUBE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { triggerScramble() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scramble_puzzle_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = currentLevel.accentColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Scramble Puzzle", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SCRAMBLE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced GLES Rendering Customizer Settings Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "ENGINE VISUAL CUSTOMIZER",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF8E8E9F),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val modes = listOf("Traditional", "Neon Solid", "Sticker Outline")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEachIndexed { idx, label ->
                    val isSelected = renderMode == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF00D1FF) else Color(0xFF1C1C28))
                            .clickable { renderMode = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaceControlBlock(
    label: String,
    cwNotation: String,
    ccwNotation: String,
    color: Color,
    onClickCw: () -> Unit,
    onClickCcw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181824))
            .border(1.dp, Color(0xFF222230), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Face Color Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = if (color == Color(0xFFEEEEEE)) Color.White else color
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // CW Button
            Button(
                onClick = onClickCw,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D2C), contentColor = Color.White),
                contentPadding = PaddingValues(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(cwNotation, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
            }
            // CCW Button
            Button(
                onClick = onClickCcw,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D2C), contentColor = Color(0xFF00D1FF)),
                contentPadding = PaddingValues(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(ccwNotation, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF00D1FF))
            }
        }
    }
}
