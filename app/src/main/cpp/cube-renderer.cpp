#include <jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <cmath>
#include <cstdlib>
#include <time.h>

#define LOG_TAG "CubeRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ----------------------------------------------------------------------------
// Mathematical Helpers (4x4 Matrix)
// ----------------------------------------------------------------------------

void matrixIdentity(float* m) {
    for (int i = 0; i < 16; i++) {
        m[i] = (i % 5 == 0) ? 1.0f : 0.0f;
    }
}

void matrixMultiply(float* result, const float* lhs, const float* rhs) {
    float tmp[16];
    for (int i = 0; i < 4; i++) { // Row of lhs
        for (int j = 0; j < 4; j++) { // Column of rhs
            float sum = 0.0f;
            for (int k = 0; k < 4; k++) {
                sum += lhs[i * 4 + k] * rhs[k * 4 + j];
            }
            tmp[i * 4 + j] = sum;
        }
    }
    for (int i = 0; i < 16; i++) {
        result[i] = tmp[i];
    }
}

void matrixPerspective(float* m, float fovy, float aspect, float zNear, float zFar) {
    float f = 1.0f / tanf(static_cast<float>(fovy * M_PI / 360.0f));
    matrixIdentity(m);
    m[0] = f / aspect;
    m[5] = f;
    m[10] = (zFar + zNear) / (zNear - zFar);
    m[11] = -1.0f;
    m[14] = (2.0f * zFar * zNear) / (zNear - zFar);
    m[15] = 0.0f;
}

void matrixTranslate(float* m, float x, float y, float z) {
    float t[16];
    matrixIdentity(t);
    t[12] = x;
    t[13] = y;
    t[14] = z;
    float res[16];
    matrixMultiply(res, m, t);
    for (int i = 0; i < 16; i++) {
        m[i] = res[i];
    }
}

void matrixRotateX(float* m, float angle) {
    float rad = static_cast<float>(angle * M_PI / 180.0f);
    float c = cosf(rad);
    float s = sinf(rad);
    float r[16];
    matrixIdentity(r);
    r[5] = c;  r[6] = s;
    r[9] = -s; r[10] = c;
    float res[16];
    matrixMultiply(res, m, r);
    for (int i = 0; i < 16; i++) {
        m[i] = res[i];
    }
}

void matrixRotateY(float* m, float angle) {
    float rad = static_cast<float>(angle * M_PI / 180.0f);
    float c = cosf(rad);
    float s = sinf(rad);
    float r[16];
    matrixIdentity(r);
    r[0] = c; r[2] = -s;
    r[8] = s; r[10] = c;
    float res[16];
    matrixMultiply(res, m, r);
    for (int i = 0; i < 16; i++) {
        m[i] = res[i];
    }
}

void matrixRotateZ(float* m, float angle) {
    float rad = static_cast<float>(angle * M_PI / 180.0f);
    float c = cosf(rad);
    float s = sinf(rad);
    float r[16];
    matrixIdentity(r);
    r[0] = c; r[1] = s;
    r[4] = -s; r[5] = c;
    float res[16];
    matrixMultiply(res, m, r);
    for (int i = 0; i < 16; i++) {
        m[i] = res[i];
    }
}

long long currentTimeInMs() {
    struct timespec res;
    clock_gettime(CLOCK_MONOTONIC, &res);
    return (res.tv_sec * 1000LL) + (res.tv_nsec / 1000000LL);
}

// ----------------------------------------------------------------------------
// State Data Structure
// ----------------------------------------------------------------------------

struct CubeState {
    float angleX = 0.0f;
    float angleY = 0.0f;
    float angleZ = 0.0f;

    float speedX = 1.0f;
    float speedY = 0.8f;
    float speedZ = 0.5f;

    bool rotateXEnabled = true;
    bool rotateYEnabled = true;
    bool rotateZEnabled = true;

    // Rendering modes:
    // 0 = Poly-Color Face (Unique colored faces)
    // 1 = Neon Single Color (Filled uniform layout)
    // 2 = Wireframe Neon Mode
    int renderMode = 0;

    // Custom uniform color (RGBA values from controls)
    float uniformR = 0.0f;
    float uniformG = 0.82f;
    float uniformB = 1.00f;
    float uniformA = 1.0f;

    // Viewport size
    int width = 0;
    int height = 0;

    // Viewport Aspect and matrices
    float aspect = 1.0f;
    float projectionMatrix[16];
    float mModelViewMatrix[16];
    float mMVPMatrix[16];

    // GLES objects
    GLuint programObject = 0;
    GLint mvpLoc = -1;
    GLint uniformColorLoc = -1;
    GLint useUniformColorLoc = -1;

    // Geometry buffers
    GLuint vboPosition = 0;
    GLuint vboColor = 0;
    GLuint iboIndex = 0;
    GLuint vao = 0;

    // FPS calculation
    long long lastFpsTime = 0;
    int frameCount = 0;
    float currentFps = 60.0f;
};

static CubeState gState;

// ----------------------------------------------------------------------------
// Geometry Data Definitions (24 vertices, 6 faces)
// ----------------------------------------------------------------------------

static const float cubeVertices[] = {
    // Front face
    -1.0f, -1.0f,  1.0f,
     1.0f, -1.0f,  1.0f,
     1.0f,  1.0f,  1.0f,
    -1.0f,  1.0f,  1.0f,

    // Back face
    -1.0f, -1.0f, -1.0f,
    -1.0f,  1.0f, -1.0f,
     1.0f,  1.0f, -1.0f,
     1.0f, -1.0f, -1.0f,

    // Top face
    -1.0f,  1.0f, -1.0f,
    -1.0f,  1.0f,  1.0f,
     1.0f,  1.0f,  1.0f,
     1.0f,  1.0f, -1.0f,

    // Bottom face
    -1.0f, -1.0f, -1.0f,
     1.0f, -1.0f, -1.0f,
     1.0f, -1.0f,  1.0f,
    -1.0f, -1.0f,  1.0f,

    // Right face
     1.0f, -1.0f, -1.0f,
     1.0f,  1.0f, -1.0f,
     1.0f,  1.0f,  1.0f,
     1.0f, -1.0f,  1.0f,

    // Left face
    -1.0f, -1.0f, -1.0f,
    -1.0f, -1.0f,  1.0f,
    -1.0f,  1.0f,  1.0f,
    -1.0f,  1.0f, -1.0f
};

static const float cubeColors[] = {
    // Front Face (Warm Coral Red)
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,

    // Back Face (Emerald Mint)
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,

    // Top Face (Vibrant Royal Indigo)
    0.35f, 0.45f, 0.95f, 1.0f,
    0.35f, 0.45f, 0.95f, 1.0f,
    0.35f, 0.45f, 0.95f, 1.0f,
    0.35f, 0.45f, 0.95f, 1.0f,

    // Bottom Face (Glow Bright Orange)
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f,

    // Right Face (Candy Orchid Purple)
    0.73f, 0.33f, 0.83f, 1.0f,
    0.73f, 0.33f, 0.83f, 1.0f,
    0.73f, 0.33f, 0.83f, 1.0f,
    0.73f, 0.33f, 0.83f, 1.0f,

    // Left Face (Electric Ocean Cyan)
    0.05f, 0.72f, 0.88f, 1.0f,
    0.05f, 0.72f, 0.88f, 1.0f,
    0.05f, 0.72f, 0.88f, 1.0f,
    0.05f, 0.72f, 0.88f, 1.0f
};

static const GLushort cubeIndices[] = {
    0,  1,  2,      0,  2,  3,    // front
    4,  5,  6,      4,  6,  7,    // back
    8,  9,  10,     8,  10, 11,   // top
    12, 13, 14,     12, 14, 15,   // bottom
    16, 17, 18,     16, 18, 19,   // right
    20, 21, 22,     20, 22, 23    // left
};

// ----------------------------------------------------------------------------
// Shader Sources
// ----------------------------------------------------------------------------

static const char* vShaderStr = R"glsl(#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec4 aColor;
uniform mat4 uMVPMatrix;
out vec4 vColor;
void main() {
    gl_Position = uMVPMatrix * aPosition;
    vColor = aColor;
}
)glsl";

static const char* fShaderStr = R"glsl(#version 300 es
precision mediump float;
in vec4 vColor;
uniform vec4 uUniformColor;
uniform int uUseUniformColor;
out vec4 fragColor;
void main() {
    if (uUseUniformColor == 1) {
        fragColor = uUniformColor;
    } else {
        fragColor = vColor;
    }
}
)glsl";

GLuint loadShader(GLenum type, const char* shaderSrc) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;
    glShaderSource(shader, 1, &shaderSrc, nullptr);
    glCompileShader(shader);
    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            char* infoLog = static_cast<char*>(malloc(sizeof(char) * infoLen));
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog);
            LOGE("Error compiling shader type %d:\n%s\n", type, infoLog);
            free(infoLog);
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

// ----------------------------------------------------------------------------
// JNI Method Declarations and Implementations
// ----------------------------------------------------------------------------

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeOnSurfaceCreated(JNIEnv* env, jobject obj) {
    LOGI("onSurfaceCreated: resetting GLES handles");
    gState.programObject = 0;
    gState.vao = 0;
    gState.vboPosition = 0;
    gState.vboColor = 0;
    gState.iboIndex = 0;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeInit(JNIEnv* env, jobject obj, jint width, jint height) {
    LOGI("init width: %d, height: %d", width, height);
    gState.width = width;
    gState.height = height;
    gState.aspect = static_cast<float>(width) / static_cast<float>(height);

    // Set viewports and projection matrices
    glViewport(0, 0, width, height);
    matrixPerspective(gState.projectionMatrix, 45.0f, gState.aspect, 1.0f, 10.0f);

    // Set OpenGL Settings: Enable Depth Testing for rendering 3D safely.
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);

    // Disable culling initially to render full interior / translucent wireframe
    glDisable(GL_CULL_FACE);

    // Clear background to matches Obsidian dark visual direction
    glClearColor(0.05f, 0.05f, 0.08f, 1.0f);

    // Compile and link shader program once
    if (gState.programObject == 0) {
        GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vShaderStr);
        GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, fShaderStr);
        if (vertexShader == 0 || fragmentShader == 0) {
            LOGE("Shader compilation failed!");
            return;
        }

        gState.programObject = glCreateProgram();
        if (gState.programObject == 0) {
            LOGE("Failed to create GLES3 program!");
            return;
        }

        glAttachShader(gState.programObject, vertexShader);
        glAttachShader(gState.programObject, fragmentShader);
        glLinkProgram(gState.programObject);

        GLint linked;
        glGetProgramiv(gState.programObject, GL_LINK_STATUS, &linked);
        if (!linked) {
            GLint infoLen = 0;
            glGetProgramiv(gState.programObject, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen > 1) {
                char* infoLog = static_cast<char*>(malloc(sizeof(char) * infoLen));
                glGetProgramInfoLog(gState.programObject, infoLen, nullptr, infoLog);
                LOGE("Error linking program:\n%s\n", infoLog);
                free(infoLog);
            }
            glDeleteProgram(gState.programObject);
            gState.programObject = 0;
            return;
        }

        gState.mvpLoc = glGetUniformLocation(gState.programObject, "uMVPMatrix");
        gState.uniformColorLoc = glGetUniformLocation(gState.programObject, "uUniformColor");
        gState.useUniformColorLoc = glGetUniformLocation(gState.programObject, "uUseUniformColor");

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    // Set buffer layout (VAO & VBOs)
    if (gState.vao == 0) {
        glGenVertexArrays(1, &gState.vao);
        glBindVertexArray(gState.vao);

        // Position Buffer Setup
        glGenBuffers(1, &gState.vboPosition);
        glBindBuffer(GL_ARRAY_BUFFER, gState.vboPosition);
        glBufferData(GL_ARRAY_BUFFER, sizeof(cubeVertices), cubeVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), nullptr);

        // Color Buffer Setup
        glGenBuffers(1, &gState.vboColor);
        glBindBuffer(GL_ARRAY_BUFFER, gState.vboColor);
        glBufferData(GL_ARRAY_BUFFER, sizeof(cubeColors), cubeColors, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 4 * sizeof(float), nullptr);

        // Indices Buffer Setup
        glGenBuffers(1, &gState.iboIndex);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gState.iboIndex);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(cubeIndices), cubeIndices, GL_STATIC_DRAW);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_cube_NativeCubeLib_nativeStep(JNIEnv* env, jobject obj) {
    // ------------------------------------------------------------------------
    // Clear back buffer and depth buffer
    // ------------------------------------------------------------------------
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    if (gState.programObject == 0) {
        return 0.0f;
    }

    // Use shader program
    glUseProgram(gState.programObject);

    // ------------------------------------------------------------------------
    // Calculate rotation angles
    // ------------------------------------------------------------------------
    if (gState.rotateXEnabled) {
        gState.angleX += gState.speedX * 1.5f;
        if (gState.angleX >= 360.0f) gState.angleX -= 360.0f;
    }
    if (gState.rotateYEnabled) {
        gState.angleY += gState.speedY * 1.5f;
        if (gState.angleY >= 360.0f) gState.angleY -= 360.0f;
    }
    if (gState.rotateZEnabled) {
        gState.angleZ += gState.speedZ * 1.5f;
        if (gState.angleZ >= 360.0f) gState.angleZ -= 360.0f;
    }

    // ------------------------------------------------------------------------
    // Calculate Model-View Projection (MVP) Matrix
    // ------------------------------------------------------------------------
    matrixIdentity(gState.mModelViewMatrix);
    // Push the cube back into the scene to display correctly
    matrixTranslate(gState.mModelViewMatrix, 0.0f, 0.0f, -4.5f);

    // Apply translations & rotation angles
    matrixRotateX(gState.mModelViewMatrix, gState.angleX);
    matrixRotateY(gState.mModelViewMatrix, gState.angleY);
    matrixRotateZ(gState.mModelViewMatrix, gState.angleZ);

    // Multiply Projection x ModelView
    matrixMultiply(gState.mMVPMatrix, gState.projectionMatrix, gState.mModelViewMatrix);

    // Pass the calculated MVP transform Matrix to screen shaders
    glUniformMatrix4fv(gState.mvpLoc, 1, GL_FALSE, gState.mMVPMatrix);

    // ------------------------------------------------------------------------
    // Set shader uniforms for color styling logic
    // ------------------------------------------------------------------------
    if (gState.renderMode == 0) {
        // Multi-Color Face mode (uses per-vertex attributes)
        glUniform1i(gState.useUniformColorLoc, 0);
    } else {
        // Uniform Color Mode (Neon Single or Wireframe)
        glUniform1i(gState.useUniformColorLoc, 1);
        glUniform4f(gState.uniformColorLoc, gState.uniformR, gState.uniformG, gState.uniformB, gState.uniformA);
    }

    // Bind geometry
    glBindVertexArray(gState.vao);

    // ------------------------------------------------------------------------
    // Core Rendering Drawing Executions
    // ------------------------------------------------------------------------
    if (gState.renderMode == 2) {
        // Wireframe Mode
        // We draw indices as lines instead of triangles!
        // To get a wireframe, line width parameter is set and we draw with line strip or lines.
        // GLES 3 requires drawing individual lines using GL_LINES or GL_LINE_STRIP/LOOP.
        // A standard index format can represent lines perfectly. Here we draw with GL_LINE_STRIP
        // of individual face outlines, but GL_LINE_LOOP of each face makes it incredibly precise.
        // To keep it standard and highly optimized, we'll draw using line indices or GL_LINE_STRIP.
        glLineWidth(3.0f);
        // We can draw each of the 6 faces using subranges with GL_LINE_LOOP
        for (int i = 0; i < 6; ++i) {
            glDrawElements(GL_LINE_LOOP, 4, GL_UNSIGNED_SHORT, reinterpret_cast<void*>(i * 6 * sizeof(GLushort)));
        }
    } else {
        // Standard Solid or Neon Colored Cube
        glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, nullptr);
    }

    glBindVertexArray(0);

    // ------------------------------------------------------------------------
    // Frame Rate Calculations
    // ------------------------------------------------------------------------
    long long now = currentTimeInMs();
    gState.frameCount++;
    if (gState.lastFpsTime == 0) {
        gState.lastFpsTime = now;
    } else {
        long long duration = now - gState.lastFpsTime;
        if (duration >= 1000) {
            gState.currentFps = static_cast<float>(gState.frameCount) * 1000.0f / static_cast<float>(duration);
            gState.frameCount = 0;
            gState.lastFpsTime = now;
            LOGI("Calculated Rendering Performance: %.1f FPS", gState.currentFps);
        }
    }

    return gState.currentFps;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeSetRotationSpeed(JNIEnv* env, jobject obj, jfloat speedX, jfloat speedY, jfloat speedZ) {
    gState.speedX = speedX;
    gState.speedY = speedY;
    gState.speedZ = speedZ;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeToggleRotationAxis(JNIEnv* env, jobject obj, jboolean rx, jboolean ry, jboolean rz) {
    gState.rotateXEnabled = rx;
    gState.rotateYEnabled = ry;
    gState.rotateZEnabled = rz;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeSetRenderMode(JNIEnv* env, jobject obj, jint mode) {
    gState.renderMode = mode;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeSetUniformColor(JNIEnv* env, jobject obj, jfloat r, jfloat g, jfloat b, jfloat a) {
    gState.uniformR = r;
    gState.uniformG = g;
    gState.uniformB = b;
    gState.uniformA = a;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeAddRotation(JNIEnv* env, jobject obj, jfloat dx, jfloat dy) {
    gState.angleY += dx * 0.4f;
    gState.angleX += dy * 0.4f;
}

} // extern "C"
