package com.example.cube

/**
 * Kotlin JNI Wrapper to bridge native interactive OpenGL 3D Cube rendering C++ APIs.
 * Supports safety fallbacks for headless unit testing environments (e.g. Robolectric/Roborazzi).
 */
object NativeCubeLib {
    var isLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("ndkcube")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("Warning: Could not load ndkcube library (expected in headless JVM tests)")
        }
    }

    fun onSurfaceCreated() {
        if (isLibraryLoaded) {
            nativeOnSurfaceCreated()
        }
        KotlinCubeEngine.resetCube()
    }

    fun init(width: Int, height: Int) {
        if (isLibraryLoaded) {
            nativeInit(width, height)
        }
    }

    fun step(): Float {
        if (isLibraryLoaded) {
            return nativeStep()
        } else {
            KotlinCubeEngine.step()
            return 60.0f
        }
    }

    fun setRotationSpeed(speedX: Float, speedY: Float, speedZ: Float) {
        if (isLibraryLoaded) {
            nativeSetRotationSpeed(speedX, speedY, speedZ)
        }
        KotlinCubeEngine.setRotationSpeed(speedX, speedY, speedZ)
    }

    fun toggleRotationAxis(rx: Boolean, ry: Boolean, rz: Boolean) {
        if (isLibraryLoaded) {
            nativeToggleRotationAxis(rx, ry, rz)
        }
        KotlinCubeEngine.toggleRotationAxis(rx, ry, rz)
    }

    fun setRenderMode(mode: Int) {
        if (isLibraryLoaded) {
            nativeSetRenderMode(mode)
        }
        KotlinCubeEngine.setRenderMode(mode)
    }

    fun setUniformColor(r: Float, g: Float, b: Float, a: Float) {
        if (isLibraryLoaded) {
            nativeSetUniformColor(r, g, b, a)
        }
        KotlinCubeEngine.setUniformColor(r, g, b, a)
    }

    fun addRotation(dx: Float, dy: Float) {
        if (isLibraryLoaded) {
            nativeAddRotation(dx, dy)
        }
        KotlinCubeEngine.addRotation(dx, dy)
    }

    fun rotateLayer(axis: Int, layer: Int, direction: Int): Boolean {
        if (isLibraryLoaded) {
            nativeRotateLayer(axis, layer, direction)
        }
        return KotlinCubeEngine.rotateLayer(axis, layer, direction)
    }

    fun scramble(moves: Int) {
        if (isLibraryLoaded) {
            nativeScramble(moves)
        }
        KotlinCubeEngine.scramble(moves)
    }

    fun isSolved(): Boolean {
        return if (isLibraryLoaded) {
            nativeIsSolved()
        } else {
            KotlinCubeEngine.isSolved()
        }
    }

    fun resetCube() {
        if (isLibraryLoaded) {
            nativeResetCube()
        }
        KotlinCubeEngine.resetCube()
    }

    fun getMoveCount(): Int {
        return if (isLibraryLoaded) {
            nativeGetMoveCount()
        } else {
            KotlinCubeEngine.moveCount
        }
    }

    // Underlying Native external declarations
    private external fun nativeOnSurfaceCreated()
    private external fun nativeInit(width: Int, height: Int)
    private external fun nativeStep(): Float
    private external fun nativeSetRotationSpeed(speedX: Float, speedY: Float, speedZ: Float)
    private external fun nativeToggleRotationAxis(rx: Boolean, ry: Boolean, rz: Boolean)
    private external fun nativeSetRenderMode(mode: Int)
    private external fun nativeSetUniformColor(r: Float, g: Float, b: Float, a: Float)
    private external fun nativeAddRotation(dx: Float, dy: Float)
    private external fun nativeRotateLayer(axis: Int, layer: Int, direction: Int): Boolean
    private external fun nativeScramble(moves: Int)
    private external fun nativeIsSolved(): Boolean
    private external fun nativeResetCube()
    private external fun nativeGetMoveCount(): Int
}
