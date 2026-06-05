package com.example.cube

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer implementation invoking native C++ graphics pipeline routines.
 */
class NativeCubeRenderer(
    private val onFpsUpdated: (Float) -> Unit
) : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        NativeCubeLib.onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        NativeCubeLib.init(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val fps = NativeCubeLib.step()
        if (fps > 0.0f) {
            onFpsUpdated(fps)
        }
    }
}
