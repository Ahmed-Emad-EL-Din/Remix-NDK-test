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
    }

    fun init(width: Int, height: Int) {
        if (isLibraryLoaded) {
            nativeInit(width, height)
        }
    }

    fun step(): Float {
        return if (isLibraryLoaded) {
            nativeStep()
        } else {
            60.0f
        }
    }

    fun setRotationSpeed(speedX: Float, speedY: Float, speedZ: Float) {
        if (isLibraryLoaded) {
            nativeSetRotationSpeed(speedX, speedY, speedZ)
        }
    }

    fun toggleRotationAxis(rx: Boolean, ry: Boolean, rz: Boolean) {
        if (isLibraryLoaded) {
            nativeToggleRotationAxis(rx, ry, rz)
        }
    }

    fun setRenderMode(mode: Int) {
        if (isLibraryLoaded) {
            nativeSetRenderMode(mode)
        }
    }

    fun setUniformColor(r: Float, g: Float, b: Float, a: Float) {
        if (isLibraryLoaded) {
            nativeSetUniformColor(r, g, b, a)
        }
    }

    fun addRotation(dx: Float, dy: Float) {
        if (isLibraryLoaded) {
            nativeAddRotation(dx, dy)
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
}
