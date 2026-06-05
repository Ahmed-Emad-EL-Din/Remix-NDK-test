package com.example.cube

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * High-fidelity pure Kotlin 3D Rubik's Cube state and math simulator.
 * Serves as a perfect fallback for headless JVM tests or environments experiencing OpenGL issues.
 */
object KotlinCubeEngine {
    // 27 Cubies
    val cubies = Array(27) { KotlinCubie() }
    
    // Global camera orientations
    var angleX = 30.0f
    var angleY = -45.0f
    var angleZ = 0.0f
    
    // Auto rotation settings
    var speedX = 0f
    var speedY = 0f
    var speedZ = 0f
    var rotateXEnabled = false
    var rotateYEnabled = false
    var rotateZEnabled = false
    
    // Settings
    var activeRenderMode = 0 // 0 = Standard, 1 = Neon, 2 = Wireframe
    var uniformR = 0.0f
    var uniformG = 0.82f
    var uniformB = 1.0f
    var uniformA = 1.0f

    // Move statistics
    var moveCount = 0
        get() = field
        set(value) { field = value }
        
    // Animation state
    var isAnimating = false
    var animAxis = 0        // 0=X, 1=Y, 2=Z
    var animLayer = 0       // -1, 0, 1
    var animDirection = 1   // 1 or -1
    var animProgress = 0.0f
    const val animSpeed = 0.10f // Animate at 10% per frame step
    
    // Predefined 3D base normals for the 6 cube facets
    val N_orig = arrayOf(
        floatArrayOf(0.0f, 0.0f, 1.0f),  // Face 0 (Front +Z)
        floatArrayOf(0.0f, 0.0f, -1.0f), // Face 1 (Back -Z)
        floatArrayOf(0.0f, 1.0f, 0.0f),  // Face 2 (Top +Y)
        floatArrayOf(0.0f, -1.0f, 0.0f), // Face 3 (Bottom -Y)
        floatArrayOf(1.0f, 0.0f, 0.0f),  // Face 4 (Right +X)
        floatArrayOf(-1.0f, 0.0f, 0.0f)  // Face 5 (Left -X)
    )

    init {
        resetCube()
    }

    fun resetCube() {
        var index = 0
        for (x in -1..1) {
            for (y in -1..1) {
                for (z in -1..1) {
                    val c = cubies[index]
                    c.cx = x
                    c.cy = y
                    c.cz = z
                    
                    // Reset orientation matrix to identity
                    c.matrix = floatArrayOf(
                        1f, 0f, 0f,
                        0f, 1f, 0f,
                        0f, 0f, 1f
                    )
                    
                    // Assign physical exterior faces
                    c.isFaceExterior[0] = (z == 1)   // Front (+Z)
                    c.isFaceExterior[1] = (z == -1)  // Back (-Z)
                    c.isFaceExterior[2] = (y == 1)   // Top (+Y)
                    c.isFaceExterior[3] = (y == -1)  // Bottom (-Y)
                    c.isFaceExterior[4] = (x == 1)   // Right (+X)
                    c.isFaceExterior[5] = (x == -1)  // Left (-X)
                    
                    index++
                }
            }
        }
        moveCount = 0
        isAnimating = false
        animProgress = 0.0f
    }

    fun addRotation(dx: Float, dy: Float) {
        angleY += dx * 0.4f
        angleX += dy * 0.4f
    }

    fun setRotationSpeed(sx: Float, sy: Float, sz: Float) {
        speedX = sx
        speedY = sy
        speedZ = sz
    }

    fun toggleRotationAxis(rx: Boolean, ry: Boolean, rz: Boolean) {
        rotateXEnabled = rx
        rotateYEnabled = ry
        rotateZEnabled = rz
    }

    fun setRenderMode(mode: Int) {
        activeRenderMode = mode
    }

    fun setUniformColor(r: Float, g: Float, b: Float, a: Float) {
        uniformR = r
        uniformG = g
        uniformB = b
        uniformA = a
    }

    fun rotateLayer(axis: Int, layer: Int, direction: Int): Boolean {
        if (isAnimating) return false
        isAnimating = true
        animAxis = axis
        animLayer = layer
        animDirection = direction
        animProgress = 0.0f
        moveCount++
        return true
    }

    fun scramble(moves: Int) {
        resetCube()
        val r = java.util.Random()
        var lastAxis = -1
        for (m in 0 until moves) {
            var axis = r.nextInt(3)
            while (axis == lastAxis) {
                axis = r.nextInt(3)
            }
            lastAxis = axis
            val layer = if (r.nextBoolean()) -1 else 1
            val direction = if (r.nextBoolean()) 1 else -1
            rotateLayerInstant(axis, layer, direction)
        }
        moveCount = 0
        isAnimating = false
        animProgress = 0.0f
    }

    private fun rotateLayerInstant(axis: Int, layer: Int, direction: Int) {
        for (i in 0 until 27) {
            val cubie = cubies[i]
            if (isCubieInLayer(cubie, axis, layer)) {
                rotateCubieMatrix(cubie, axis, direction)
                val rotatedCoords = rotateLogicalCoords(axis, direction, cubie.cx, cubie.cy, cubie.cz)
                cubie.cx = rotatedCoords[0]
                cubie.cy = rotatedCoords[1]
                cubie.cz = rotatedCoords[2]
            }
        }
    }

    fun isCubieInLayer(cubie: KotlinCubie, axis: Int, layer: Int): Boolean {
        return when (axis) {
            0 -> cubie.cx == layer
            1 -> cubie.cy == layer
            2 -> cubie.cz == layer
            else -> false
        }
    }

    fun rotateLogicalCoords(axis: Int, direction: Int, x: Int, y: Int, z: Int): IntArray {
        var rx = x
        var ry = y
        var rz = z
        if (axis == 0) { // Rotation around X-axis
            if (direction == 1) {
                ry = -z
                rz = y
            } else {
                ry = z
                rz = -y
            }
        } else if (axis == 1) { // Rotation around Y-axis
            if (direction == 1) {
                rx = z
                rz = -x
            } else {
                rx = -z
                rz = x
            }
        } else if (axis == 2) { // Rotation around Z-axis
            if (direction == 1) {
                rx = -y
                ry = x
            } else {
                rx = y
                ry = -x
            }
        }
        return intArrayOf(rx, ry, rz)
    }

    fun rotateCubieMatrix(cubie: KotlinCubie, axis: Int, direction: Int) {
        val r = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
        val s = if (direction == 1) 1.0f else -1.0f
        if (axis == 0) { // X-Axis
            r[4] = 0.0f;  r[5] = s
            r[7] = -s;   r[8] = 0.0f
        } else if (axis == 1) { // Y-Axis
            r[0] = 0.0f;  r[2] = -s
            r[6] = s;    r[8] = 0.0f
        } else if (axis == 2) { // Z-Axis
            r[0] = 0.0f;  r[1] = s
            r[3] = -s;   r[4] = 0.0f
        }
        cubie.matrix = multiply3x3(r, cubie.matrix)
    }

    fun multiply3x3(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var sum = 0.0f
                for (k in 0..2) {
                    sum += a[i * 3 + k] * b[k * 3 + j]
                }
                r[i * 3 + j] = sum
            }
        }
        return r
    }

    fun transformPoint3x3(m: FloatArray, p: FloatArray): FloatArray {
        val rx = m[0] * p[0] + m[1] * p[1] + m[2] * p[2]
        val ry = m[3] * p[0] + m[4] * p[1] + m[5] * p[2]
        val rz = m[6] * p[0] + m[7] * p[1] + m[8] * p[2]
        return floatArrayOf(rx, ry, rz)
    }

    fun isSolved(): Boolean {
        if (isAnimating) return false
        val expectedFace = IntArray(6) { -1 }
        for (c in 0 until 27) {
            val cubie = cubies[c]
            if (cubie.cx == 0 && cubie.cy == 0 && cubie.cz == 0) {
                continue // Ignore center cubie
            }
            for (i in 0 until 6) {
                val ncv = transformPoint3x3(cubie.matrix, N_orig[i])
                val nx = ncv[0].roundToInt()
                val ny = ncv[1].roundToInt()
                val nz = ncv[2].roundToInt()

                var direction = -1
                if (nx == 1 && cubie.cx == 1) direction = 0
                else if (nx == -1 && cubie.cx == -1) direction = 1
                else if (ny == 1 && cubie.cy == 1) direction = 2
                else if (ny == -1 && cubie.cy == -1) direction = 3
                else if (nz == 1 && cubie.cz == 1) direction = 4
                else if (nz == -1 && cubie.cz == -1) direction = 5

                if (direction != -1) {
                    if (expectedFace[direction] == -1) {
                        expectedFace[direction] = i
                    } else if (expectedFace[direction] != i) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun step() {
        if (isAnimating) {
            animProgress += animSpeed
            if (animProgress >= 1.0f) {
                animProgress = 1.0f
                // Snap completion
                for (i in 0 until 27) {
                    val cubie = cubies[i]
                    if (isCubieInLayer(cubie, animAxis, animLayer)) {
                        rotateCubieMatrix(cubie, animAxis, animDirection)
                        val rotatedCoords = rotateLogicalCoords(animAxis, animDirection, cubie.cx, cubie.cy, cubie.cz)
                        cubie.cx = rotatedCoords[0]
                        cubie.cy = rotatedCoords[1]
                        cubie.cz = rotatedCoords[2]
                    }
                }
                isAnimating = false
            }
        }

        // Spin
        if (rotateXEnabled) {
            angleX += speedX * 0.4f
            if (angleX >= 360f) angleX -= 360f
        }
        if (rotateYEnabled) {
            angleY += speedY * 0.4f
            if (angleY >= 360f) angleY -= 360f
        }
        if (rotateZEnabled) {
            angleZ += speedZ * 0.4f
            if (angleZ >= 360f) angleZ -= 360f
        }
    }
}

class KotlinCubie {
    var cx = 0
    var cy = 0
    var cz = 0
    
    var matrix = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
    
    val isFaceExterior = BooleanArray(6)
}
