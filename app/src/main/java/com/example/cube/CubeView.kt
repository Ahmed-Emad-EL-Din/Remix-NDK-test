package com.example.cube

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.*

/**
 * A Jetpack Compose wrapper around GLSurfaceView that handles user touch gestures
 * and hooks up the Native C++ OpenGL 3D Cube Renderer with a robust Compose-only 3D Canvas fallback.
 */
@Composable
fun CubeView(
    modifier: Modifier = Modifier,
    onFpsUpdated: (Float) -> Unit
) {
    // Default to GLES only if native library loaded successfully, otherwise use 3D Canvas immediately
    var forceComposeCanvas by remember { mutableStateOf(!NativeCubeLib.isLibraryLoaded) }
    
    val useGles = NativeCubeLib.isLibraryLoaded && !forceComposeCanvas
    
    Box(modifier = modifier) {
        if (useGles) {
            GLSurfaceViewContainer(
                modifier = Modifier.fillMaxSize(),
                onFpsUpdated = onFpsUpdated,
                onFailFallback = {
                    forceComposeCanvas = true
                }
            )
        } else {
            Compose3DCubeCanvas(
                modifier = Modifier.fillMaxSize(),
                onFpsUpdated = onFpsUpdated
            )
        }
        
        // Stylish overlay button to toggle between hardware GLES and 3D Canvas Engine
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            FilledTonalButton(
                onClick = { forceComposeCanvas = !forceComposeCanvas },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0x7F0F0F14),
                    contentColor = if (useGles) Color(0xFF00FFCC) else Color(0xFFFFB300)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = if (useGles) Icons.Default.Refresh else Icons.Default.Star,
                    contentDescription = "Switch Graphics Engine",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (useGles) "GLES 3.0" else "Canvas 3D",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun GLSurfaceViewContainer(
    modifier: Modifier,
    onFpsUpdated: (Float) -> Unit,
    onFailFallback: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceViewRef by remember { mutableStateOf<CustomGLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, surfaceViewRef) {
        val sView = surfaceViewRef
        if (sView != null) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> sView.onResume()
                    Lifecycle.Event.ON_PAUSE -> sView.onPause()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        } else {
            onDispose {}
        }
    }

    AndroidView(
        factory = { ctx ->
            try {
                CustomGLSurfaceView(ctx, onFpsUpdated).also {
                    surfaceViewRef = it
                }
            } catch (e: Exception) {
                System.err.println("GLES implementation failed initializing, falling back to 3D Canvas: " + e.message)
                onFailFallback()
                CustomGLSurfaceView(ctx, onFpsUpdated)
            }
        },
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Feed raw drag delta values to JNI + Fallback
                    NativeCubeLib.addRotation(dragAmount.x, -dragAmount.y)
                }
            }
    )
}

@Composable
fun Compose3DCubeCanvas(
    modifier: Modifier,
    onFpsUpdated: (Float) -> Unit
) {
    // Elegant game loop frame tick: forces recomposition smoothly at up to 60 FPS
    val infiniteTransition = rememberInfiniteTransition(label = "cube")
    val frameTimeState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frameTime"
    )

    // Base colors configuration (same as GLSL vertex colors)
    val baseFaceColors = remember {
        listOf(
            Color(0xFF2ECC71), // Face 0: Front (+Z) - Green
            Color(0xFF2980B9), // Face 1: Back (-Z) - Blue
            Color(0xFFECF0F1), // Face 2: Top (+Y) - White
            Color(0xFFF1C40F), // Face 3: Bottom (-Y) - Yellow
            Color(0xFFE74C3C), // Face 4: Right (+X) - Red
            Color(0xFFE67E22)  // Face 5: Left (-X) - Orange
        )
    }

    // Single cubie sticker geometry description
    val s = 0.90f
    val localFaceVertices = remember {
        arrayOf(
            // Face 0: Front (+Z)
            arrayOf(floatArrayOf(-s, -s, s), floatArrayOf(s, -s, s), floatArrayOf(s, s, s), floatArrayOf(-s, s, s)),
            // Face 1: Back (-Z)
            arrayOf(floatArrayOf(-s, -s, -s), floatArrayOf(-s, s, -s), floatArrayOf(s, s, -s), floatArrayOf(s, -s, -s)),
            // Face 2: Top (+Y)
            arrayOf(floatArrayOf(-s, s, -s), floatArrayOf(-s, s, s), floatArrayOf(s, s, s), floatArrayOf(s, s, -s)),
            // Face 3: Bottom (-Y)
            arrayOf(floatArrayOf(-s, -s, -s), floatArrayOf(s, -s, -s), floatArrayOf(s, -s, s), floatArrayOf(-s, -s, s)),
            // Face 4: Right (+X)
            arrayOf(floatArrayOf(s, -s, -s), floatArrayOf(s, s, -s), floatArrayOf(s, s, s), floatArrayOf(s, -s, s)),
            // Face 5: Left (-X)
            arrayOf(floatArrayOf(-s, -s, -s), floatArrayOf(-s, -s, s), floatArrayOf(-s, s, s), floatArrayOf(-s, s, -s))
        )
    }

    // Continuous update tick
    LaunchedEffect(frameTimeState.value) {
        KotlinCubeEngine.step()
        onFpsUpdated(60.0f) // Keep UI refreshed
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    KotlinCubeEngine.addRotation(dragAmount.x, -dragAmount.y)
                }
            }
    ) {
        val aspect = size.width / size.height
        val f = 2.4142f
        val halfWidth = size.width / 2f
        val halfHeight = size.height / 2f
        val centerX = halfWidth
        val centerY = halfHeight

        // Setup draw buffer lists
        val drawList = java.util.ArrayList<DrawableFace>()

        for (cubie in KotlinCubeEngine.cubies) {
            if (cubie.cx == 0 && cubie.cy == 0 && cubie.cz == 0) continue

            for (face_i in 0..5) {
                val worldPoints = Array(4) { FloatArray(3) }
                for (v_j in 0..3) {
                    val p_src = localFaceVertices[face_i][v_j]
                    // Orientation matrix rotation
                    val p_oriented = KotlinCubeEngine.transformPoint3x3(cubie.matrix, p_src)
                    // Translation spacing
                    val d = 2.12f
                    var wx = p_oriented[0] + cubie.cx * d
                    var wy = p_oriented[1] + cubie.cy * d
                    var wz = p_oriented[2] + cubie.cz * d

                    // Active Turn Animation
                    if (KotlinCubeEngine.isAnimating && KotlinCubeEngine.isCubieInLayer(cubie, KotlinCubeEngine.animAxis, KotlinCubeEngine.animLayer)) {
                        var p_anim = floatArrayOf(wx, wy, wz)
                        val angle = KotlinCubeEngine.animDirection * KotlinCubeEngine.animProgress * 90f
                        p_anim = when (KotlinCubeEngine.animAxis) {
                            0 -> rotateX(p_anim, angle)
                            1 -> rotateY(p_anim, angle)
                            2 -> rotateZ(p_anim, angle)
                            else -> p_anim
                        }
                        wx = p_anim[0]; wy = p_anim[1]; wz = p_anim[2]
                    }

                    // Global Camera Drag Orbits
                    var p_global = floatArrayOf(wx, wy, wz)
                    p_global = rotateY(p_global, KotlinCubeEngine.angleY)
                    p_global = rotateX(p_global, KotlinCubeEngine.angleX)
                    p_global = rotateZ(p_global, KotlinCubeEngine.angleZ)

                    worldPoints[v_j] = p_global
                }

                // Average depth on Z axis in World Coordinates
                val avgZ = (worldPoints[0][2] + worldPoints[1][2] + worldPoints[2][2] + worldPoints[3][2]) / 4f
                val camZAvg = avgZ - 9.5f

                // Project Points to 2D
                val screenPoints = ArrayList<Offset>()
                var clipped = false
                for (v_j in 0..3) {
                    val camX = worldPoints[v_j][0]
                    val camY = worldPoints[v_j][1]
                    val camZ = worldPoints[v_j][2] - 9.5f

                    val div = -camZ
                    if (div <= 0.05f) {
                        clipped = true
                        break
                    }

                    val ndcX = camX * (f / aspect) / div
                    val ndcY = camY * f / div

                    val px = centerX + ndcX * halfWidth
                    val py = centerY - ndcY * halfHeight
                    screenPoints.add(Offset(px, py))
                }

                if (clipped || screenPoints.size < 4) continue

                // Check 2D projected winding order (backface culling)
                val A = screenPoints[0]
                val B = screenPoints[1]
                val C = screenPoints[2]
                val cross = (B.x - A.x) * (C.y - B.y) - (B.y - A.y) * (C.x - B.x)
                if (cross >= 0f) {
                    continue // Culled back face
                }

                val isExterior = cubie.isFaceExterior[face_i]
                val faceColor = when (KotlinCubeEngine.activeRenderMode) {
                    1 -> { // NEON SOLID
                        if (isExterior) {
                            Color(KotlinCubeEngine.uniformR, KotlinCubeEngine.uniformG, KotlinCubeEngine.uniformB, KotlinCubeEngine.uniformA)
                        } else {
                            Color(0xFF0F0F14)
                        }
                    }
                    2 -> { // WIREFRAME
                        if (isExterior) {
                            Color(KotlinCubeEngine.uniformR, KotlinCubeEngine.uniformG, KotlinCubeEngine.uniformB, KotlinCubeEngine.uniformA)
                        } else {
                            continue
                        }
                    }
                    else -> { // STANDARD
                        if (isExterior) {
                            baseFaceColors[face_i]
                        } else {
                            Color(0xFF0F0F14)
                        }
                    }
                }

                drawList.add(
                    DrawableFace(
                        points = screenPoints,
                        averageDepth = camZAvg,
                        color = faceColor,
                        isWireframe = (KotlinCubeEngine.activeRenderMode == 2),
                        outlineOnly = !isExterior
                    )
                )
            }
        }

        // Sort Painter's Algorithm: Furthest first
        drawList.sortBy { it.averageDepth }

        // Draw Sorted Polygons
        drawList.forEach { face ->
            val path = Path().apply {
                moveTo(face.points[0].x, face.points[0].y)
                lineTo(face.points[1].x, face.points[1].y)
                lineTo(face.points[2].x, face.points[2].y)
                lineTo(face.points[3].x, face.points[3].y)
                close()
            }

            if (face.isWireframe) {
                drawPath(
                    path = path,
                    color = face.color,
                    style = Stroke(width = 4f)
                )
            } else {
                drawPath(
                    path = path,
                    color = face.color,
                    style = Fill
                )
                // Premium sharp separators
                drawPath(
                    path = path,
                    color = Color(0xFF07070B),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

data class DrawableFace(
    val points: List<Offset>,
    val averageDepth: Float,
    val color: Color,
    val isWireframe: Boolean,
    val outlineOnly: Boolean
)

// ----------------------------------------------------------------------------
// Simple 3D Rotation Math Helpers
// ----------------------------------------------------------------------------

private fun rotateX(p: FloatArray, angleDeg: Float): FloatArray {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val y = p[1] * c - p[2] * s
    val z = p[1] * s + p[2] * c
    return floatArrayOf(p[0], y, z)
}

private fun rotateY(p: FloatArray, angleDeg: Float): FloatArray {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val x = p[0] * c + p[2] * s
    val z = -p[0] * s + p[2] * c
    return floatArrayOf(x, p[1], z)
}

private fun rotateZ(p: FloatArray, angleDeg: Float): FloatArray {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val x = p[0] * c - p[1] * s
    val y = p[0] * s + p[1] * c
    return floatArrayOf(x, y, p[2])
}

/**
 * Underlying standard GLSurfaceView configured to run OpenGL ES 3.0.
 */
class CustomGLSurfaceView(
    context: Context,
    onFpsUpdated: (Float) -> Unit
) : GLSurfaceView(context) {

    init {
        try {
            // Try to request an OpenGL ES 3.0 context
            setEGLContextClientVersion(3)
        } catch (e: Exception) {
            System.err.println("GLES 3 Context not supported, trying fallback to ES 2: " + e.message)
            try {
                setEGLContextClientVersion(2)
            } catch (e2: Exception) {
                System.err.println("GLES 2 Fallback context also failed: " + e2.message)
            }
        }
        
        try {
            // Explicitly choose an EGL config with 8-bit channels and a 16-bit depth buffer for compatibility
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        } catch (e: Exception) {
            System.err.println("Explicit EGL config chooser failed, falling back to auto configuration: " + e.message)
            try {
                // Fallback: request standard configuration supporting depth-testing
                setEGLConfigChooser(true)
            } catch (e2: Exception) {
                System.err.println("EGLConfigChooser fallback also failed: " + e2.message)
            }
        }
        
        // Preserve context when paused (prevents shader re-creation overhead on rotate/switch)
        preserveEGLContextOnPause = true

        // Mount our JNI-powered renderer
        setRenderer(NativeCubeRenderer(onFpsUpdated))

        // Render continuously to animate rotating cube states smoothly
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
