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
// Rubik's Cube Structures & States
// ----------------------------------------------------------------------------

struct Cubie {
    int cx = 0, cy = 0, cz = 0; // Current logical coordinates in {-1, 0, 1}
    float matrix[16];           // Current model matrix (orientation + translation)
    bool isFaceExterior[6];     // Whether face [0..5] is physically exterior
};

struct CubeState {
    float angleX = 30.0f; // Beautiful tilted start orientation
    float angleY = -45.0f;
    float angleZ = 0.0f;

    float speedX = 0.0f; // Standard dynamic auto-rotation values
    float speedY = 0.0f;
    float speedZ = 0.0f;

    bool rotateXEnabled = false;
    bool rotateYEnabled = false;
    bool rotateZEnabled = false;

    // Rendering modes:
    // 0 = Poly-Color Face (Unique colored stickers, black interior)
    // 1 = Neon Single Color (Stickers filled with uniform color)
    // 2 = Wireframe Neon Mode (Outlines of stickers only)
    int renderMode = 0;

    // Custom uniform color
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

    // 27 Cubies
    Cubie cubies[27];
    
    // Animation Engine
    bool isAnimating = false;
    int animAxis = 0;        // 0=X, 1=Y, 2=Z
    int animLayer = 0;       // -1, 0, 1
    int animDirection = 1;   // 1 or -1
    float animProgress = 0.0f;
    float animSpeed = 0.10f; // Visual speed step per frame
    int moveCount = 0;
};

static CubeState gState;
static bool gIsCubeInitialized = false;

// ----------------------------------------------------------------------------
// Geometry Data Definitions (24 vertices, 6 faces)
// ----------------------------------------------------------------------------

static const float cubeVertices[] = {
    // Front face (+Z)
    -1.0f, -1.0f,  1.0f,
     1.0f, -1.0f,  1.0f,
     1.0f,  1.0f,  1.0f,
    -1.0f,  1.0f,  1.0f,

    // Back face (-Z)
    -1.0f, -1.0f, -1.0f,
    -1.0f,  1.0f, -1.0f,
     1.0f,  1.0f, -1.0f,
     1.0f, -1.0f, -1.0f,

    // Top face (+Y)
    -1.0f,  1.0f, -1.0f,
    -1.0f,  1.0f,  1.0f,
     1.0f,  1.0f,  1.0f,
     1.0f,  1.0f, -1.0f,

    // Bottom face (-Y)
    -1.0f, -1.0f, -1.0f,
     1.0f, -1.0f, -1.0f,
     1.0f, -1.0f,  1.0f,
    -1.0f, -1.0f,  1.0f,

    // Right face (+X)
     1.0f, -1.0f, -1.0f,
     1.0f,  1.0f, -1.0f,
     1.0f,  1.0f,  1.0f,
     1.0f, -1.0f,  1.0f,

    // Left face (-X)
    -1.0f, -1.0f, -1.0f,
    -1.0f, -1.0f,  1.0f,
    -1.0f,  1.0f,  1.0f,
    -1.0f,  1.0f, -1.0f
};

// Original color scheme (Rubik's stickers)
static const float cubeColors[] = {
    // Front Face (Emerald Mint Green)
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,
    0.18f, 0.80f, 0.44f, 1.0f,

    // Back Face (Vibrant Royal Blue)
    0.15f, 0.45f, 0.95f, 1.0f,
    0.15f, 0.45f, 0.95f, 1.0f,
    0.15f, 0.45f, 0.95f, 1.0f,
    0.15f, 0.45f, 0.95f, 1.0f,

    // Top Face (Clean Polar White)
    0.95f, 0.95f, 0.95f, 1.0f,
    0.95f, 0.95f, 0.95f, 1.0f,
    0.95f, 0.95f, 0.95f, 1.0f,
    0.95f, 0.95f, 0.95f, 1.0f,

    // Bottom Face (Glow Bright Yellow)
    0.95f, 0.85f, 0.15f, 1.0f,
    0.95f, 0.85f, 0.15f, 1.0f,
    0.95f, 0.85f, 0.15f, 1.0f,
    0.95f, 0.85f, 0.15f, 1.0f,

    // Right Face (Warm Coral Red)
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,
    0.95f, 0.35f, 0.38f, 1.0f,

    // Left Face (Sunset Neon Orange)
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f,
    0.95f, 0.60f, 0.15f, 1.0f
};

static const GLushort cubeIndices[] = {
    0,  1,  2,      0,  2,  3,    // front (+Z)
    4,  5,  6,      4,  6,  7,    // back (-Z)
    8,  9,  10,     8,  10, 11,   // top (+Y)
    12, 13, 14,     12, 14, 15,   // bottom (-Y)
    16, 17, 18,     16, 18, 19,   // right (+X)
    20, 21, 22,     20, 22, 23    // left (-X)
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
// Mathematics / Geometry Helpers for Rubik's Cube
// ----------------------------------------------------------------------------

static const float N_orig[6][3] = {
    {0.0f, 0.0f, 1.0f},  // Face 0 (Front +Z)
    {0.0f, 0.0f, -1.0f}, // Face 1 (Back -Z)
    {0.0f, 1.0f, 0.0f},  // Face 2 (Top +Y)
    {0.0f, -1.0f, 0.0f}, // Face 3 (Bottom -Y)
    {1.0f, 0.0f, 0.0f},  // Face 4 (Right +X)
    {-1.0f, 0.0f, 0.0f}  // Face 5 (Left -X)
};

void transformVector(const float* m, const float* v, float* out) {
    out[0] = m[0] * v[0] + m[4] * v[1] + m[8] * v[2];
    out[1] = m[1] * v[0] + m[5] * v[1] + m[9] * v[2];
    out[2] = m[2] * v[0] + m[6] * v[1] + m[10] * v[2];
}

bool isCubieInLayer(const Cubie& cubie, int axis, int layer) {
    if (axis == 0) return cubie.cx == layer;
    if (axis == 1) return cubie.cy == layer;
    if (axis == 2) return cubie.cz == layer;
    return false;
}

void rotateLogicalCoords(int axis, int direction, int& cx, int& cy, int& cz) {
    int x = cx, y = cy, z = cz;
    if (axis == 0) { // Rotation around X-axis
        if (direction == 1) {
            cy = -z;
            cz = y;
        } else {
            cy = z;
            cz = -y;
        }
    } else if (axis == 1) { // Rotation around Y-axis
        if (direction == 1) {
            cx = z;
            cz = -x;
        } else {
            cx = -z;
            cz = x;
        }
    } else if (axis == 2) { // Rotation around Z-axis
        if (direction == 1) {
            cx = -y;
            cy = x;
        } else {
            cx = y;
            cy = -x;
        }
    }
}

void cubieRotateSnap(float* m, int axis, int direction) {
    float r[16];
    matrixIdentity(r);
    float s = (direction == 1) ? 1.0f : -1.0f;
    if (axis == 0) { // Rotation Matrix for X-Axis (using pre-multiplication)
        r[5]  = 0.0f; r[6]  = s;
        r[9]  = -s;   r[10] = 0.0f;
    } else if (axis == 1) { // Rotation Matrix for Y-Axis
        r[0]  = 0.0f; r[2]  = -s;
        r[8]  = s;    r[10] = 0.0f;
    } else if (axis == 2) { // Rotation Matrix for Z-Axis
        r[0]  = 0.0f; r[1]  = s;
        r[4]  = -s;   r[5]  = 0.0f;
    }
    float res[16];
    matrixMultiply(res, r, m);
    for (int i = 0; i < 16; i++) {
        m[i] = res[i];
    }
}

void resetCubeData() {
    int index = 0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            for (int z = -1; z <= 1; ++z) {
                gState.cubies[index].cx = x;
                gState.cubies[index].cy = y;
                gState.cubies[index].cz = z;

                matrixIdentity(gState.cubies[index].matrix);
                // We use 2.12f to separate mini-cubes by a small gap that looks beautiful!
                float d = 2.12f;
                matrixTranslate(gState.cubies[index].matrix, x * d, y * d, z * d);

                // Set physical exterior faces
                gState.cubies[index].isFaceExterior[0] = (z == 1);  // Front (+Z)
                gState.cubies[index].isFaceExterior[1] = (z == -1); // Back (-Z)
                gState.cubies[index].isFaceExterior[2] = (y == 1);  // Top (+Y)
                gState.cubies[index].isFaceExterior[3] = (y == -1); // Bottom (-Y)
                gState.cubies[index].isFaceExterior[4] = (x == 1);  // Right (+X)
                gState.cubies[index].isFaceExterior[5] = (x == -1); // Left (-X)

                index++;
            }
        }
    }
    gState.moveCount = 0;
    gState.isAnimating = false;
}

void rotateLayerInstant(int axis, int layer, int direction) {
    for (int i = 0; i < 27; ++i) {
        if (isCubieInLayer(gState.cubies[i], axis, layer)) {
            cubieRotateSnap(gState.cubies[i].matrix, axis, direction);
            rotateLogicalCoords(axis, direction, gState.cubies[i].cx, gState.cubies[i].cy, gState.cubies[i].cz);
        }
    }
}

void scrambleCube(int moves) {
    resetCubeData();
    srand(static_cast<unsigned int>(time(nullptr)));
    int lastAxis = -1;
    for (int m = 0; m < moves; ++m) {
        int axis = rand() % 3;
        while (axis == lastAxis) {
            axis = rand() % 3;
        }
        lastAxis = axis;
        int layer = (rand() % 2 == 0) ? -1 : 1;
        int direction = (rand() % 2 == 0) ? 1 : -1;
        rotateLayerInstant(axis, layer, direction);
    }
    gState.moveCount = 0;
    gState.isAnimating = false;
}

bool checkSolved() {
    int expectedFace[6] = {-1, -1, -1, -1, -1, -1};
    for (int c = 0; c < 27; ++c) {
        if (gState.cubies[c].cx == 0 && gState.cubies[c].cy == 0 && gState.cubies[c].cz == 0) {
            continue; // Ignore interior center cubie
        }
        for (int i = 0; i < 6; ++i) {
            float ncv[3];
            transformVector(gState.cubies[c].matrix, N_orig[i], ncv);
            int nx = static_cast<int>(round(ncv[0]));
            int ny = static_cast<int>(round(ncv[1]));
            int nz = static_cast<int>(round(ncv[2]));

            int direction = -1;
            if (nx == 1 && gState.cubies[c].cx == 1) direction = 0;       // Right Facet
            else if (nx == -1 && gState.cubies[c].cx == -1) direction = 1; // Left Facet
            else if (ny == 1 && gState.cubies[c].cy == 1) direction = 2;   // Top Facet
            else if (ny == -1 && gState.cubies[c].cy == -1) direction = 3; // Bottom Facet
            else if (nz == 1 && gState.cubies[c].cz == 1) direction = 4;   // Front Facet
            else if (nz == -1 && gState.cubies[c].cz == -1) direction = 5; // Back Facet

            if (direction != -1) {
                if (expectedFace[direction] == -1) {
                    expectedFace[direction] = i;
                } else if (expectedFace[direction] != i) {
                    return false;
                }
            }
        }
    }
    return true;
}

void drawCubie(const Cubie& cubie, const float* baseViewMatrix) {
    float mModel[16];
    for (int i = 0; i < 16; i++) {
        mModel[i] = cubie.matrix[i];
    }

    if (gState.isAnimating && isCubieInLayer(cubie, gState.animAxis, gState.animLayer)) {
        float rMat[16];
        matrixIdentity(rMat);
        float angle = gState.animDirection * gState.animProgress * 90.0f;
        if (gState.animAxis == 0) matrixRotateX(rMat, angle);
        else if (gState.animAxis == 1) matrixRotateY(rMat, angle);
        else if (gState.animAxis == 2) matrixRotateZ(rMat, angle);

        float temp[16];
        matrixMultiply(temp, rMat, cubie.matrix);
        for (int i = 0; i < 16; i++) {
            mModel[i] = temp[i];
        }
    }

    matrixMultiply(gState.mModelViewMatrix, baseViewMatrix, mModel);
    matrixMultiply(gState.mMVPMatrix, gState.projectionMatrix, gState.mModelViewMatrix);
    glUniformMatrix4fv(gState.mvpLoc, 1, GL_FALSE, gState.mMVPMatrix);

    for (int i = 0; i < 6; ++i) {
        if (gState.renderMode == 2) {
            // WIREFRAME mode - outer outlines of stickers only
            if (!cubie.isFaceExterior[i]) {
                continue;
            }
            glUniform1i(gState.useUniformColorLoc, 1);
            glUniform4f(gState.uniformColorLoc, gState.uniformR, gState.uniformG, gState.uniformB, gState.uniformA);
            glLineWidth(5.0f);
            glDrawElements(GL_LINE_LOOP, 4, GL_UNSIGNED_SHORT, reinterpret_cast<void*>(i * 6 * sizeof(GLushort)));
        } else if (gState.renderMode == 1) {
            // NEON SOLID mode - stickers use single glow uniform color
            if (cubie.isFaceExterior[i]) {
                glUniform1i(gState.useUniformColorLoc, 1);
                glUniform4f(gState.uniformColorLoc, gState.uniformR, gState.uniformG, gState.uniformB, gState.uniformA);
            } else {
                glUniform1i(gState.useUniformColorLoc, 1);
                glUniform4f(gState.uniformColorLoc, 0.08f, 0.08f, 0.10f, 1.0f);
            }
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, reinterpret_cast<void*>(i * 6 * sizeof(GLushort)));
        } else {
            // STANDARD mode - multi-colored face stickers, black interior
            if (cubie.isFaceExterior[i]) {
                glUniform1i(gState.useUniformColorLoc, 0); // use vertex/preset color
            } else {
                glUniform1i(gState.useUniformColorLoc, 1);
                glUniform4f(gState.uniformColorLoc, 0.08f, 0.08f, 0.10f, 1.0f); // dark inner bodies
            }
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, reinterpret_cast<void*>(i * 6 * sizeof(GLushort)));
        }
    }
}

// ----------------------------------------------------------------------------
// JNI Methods
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

    glViewport(0, 0, width, height);
    matrixPerspective(gState.projectionMatrix, 45.0f, gState.aspect, 1.0f, 20.0f);

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);

    // Enable culling since cubies are solid convex hulls
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);

    // Deep premium background
    glClearColor(0.04f, 0.04f, 0.06f, 1.0f);

    if (gState.programObject == 0) {
        GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vShaderStr);
        GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, fShaderStr);
        if (vertexShader == 0 || fragmentShader == 0) {
            LOGE("Shader compilation failed!");
            return;
        }

        gState.programObject = glCreateProgram();
        glAttachShader(gState.programObject, vertexShader);
        glAttachShader(gState.programObject, fragmentShader);
        glLinkProgram(gState.programObject);

        GLint linked;
        glGetProgramiv(gState.programObject, GL_LINK_STATUS, &linked);
        if (!linked) {
            LOGE("Error linking shader program!");
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

    if (gState.vao == 0) {
        glGenVertexArrays(1, &gState.vao);
        glBindVertexArray(gState.vao);

        glGenBuffers(1, &gState.vboPosition);
        glBindBuffer(GL_ARRAY_BUFFER, gState.vboPosition);
        glBufferData(GL_ARRAY_BUFFER, sizeof(cubeVertices), cubeVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), nullptr);

        glGenBuffers(1, &gState.vboColor);
        glBindBuffer(GL_ARRAY_BUFFER, gState.vboColor);
        glBufferData(GL_ARRAY_BUFFER, sizeof(cubeColors), cubeColors, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 4 * sizeof(float), nullptr);

        glGenBuffers(1, &gState.iboIndex);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gState.iboIndex);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(cubeIndices), cubeIndices, GL_STATIC_DRAW);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    // Initialize core Rubik's Cube once to persist status
    if (!gIsCubeInitialized) {
        resetCubeData();
        gIsCubeInitialized = true;
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_cube_NativeCubeLib_nativeStep(JNIEnv* env, jobject obj) {
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    if (gState.programObject == 0) {
        return 0.0f;
    }

    glUseProgram(gState.programObject);

    // ------------------------------------------------------------------------
    // Process Turn Animation Frame
    // ------------------------------------------------------------------------
    if (gState.isAnimating) {
        gState.animProgress += gState.animSpeed;
        if (gState.animProgress >= 1.0f) {
            gState.animProgress = 1.0f;

            // Apply turn completion instantly and physically align
            for (int i = 0; i < 27; ++i) {
                if (isCubieInLayer(gState.cubies[i], gState.animAxis, gState.animLayer)) {
                    cubieRotateSnap(gState.cubies[i].matrix, gState.animAxis, gState.animDirection);
                    rotateLogicalCoords(gState.animAxis, gState.animDirection, gState.cubies[i].cx, gState.cubies[i].cy, gState.cubies[i].cz);
                }
            }
            gState.isAnimating = false;
        }
    }

    // ------------------------------------------------------------------------
    // Apply auto spinning if enabled
    // ------------------------------------------------------------------------
    if (gState.rotateXEnabled) {
        gState.angleX += gState.speedX * 0.4f;
        if (gState.angleX >= 360.0f) gState.angleX -= 360.0f;
    }
    if (gState.rotateYEnabled) {
        gState.angleY += gState.speedY * 0.4f;
        if (gState.angleY >= 360.0f) gState.angleY -= 360.0f;
    }
    if (gState.rotateZEnabled) {
        gState.angleZ += gState.speedZ * 0.4f;
        if (gState.angleZ >= 360.0f) gState.angleZ -= 360.0f;
    }

    // ------------------------------------------------------------------------
    // Draw the 27 separated cubies
    // ------------------------------------------------------------------------
    float mBaseView[16];
    matrixIdentity(mBaseView);
    // Push the group back so the entire 3x3 array fits perfectly
    matrixTranslate(mBaseView, 0.0f, 0.0f, -9.5f);

    // Global drag rotation controls
    matrixRotateX(mBaseView, gState.angleX);
    matrixRotateY(mBaseView, gState.angleY);
    matrixRotateZ(mBaseView, gState.angleZ);

    glBindVertexArray(gState.vao);

    for (int i = 0; i < 27; ++i) {
        // Skip central hidden cubie rendering to optimize drawing speed
        if (gState.cubies[i].cx == 0 && gState.cubies[i].cy == 0 && gState.cubies[i].cz == 0) {
            continue;
        }
        drawCubie(gState.cubies[i], mBaseView);
    }

    glBindVertexArray(0);

    // ------------------------------------------------------------------------
    // FPS Engine calculations
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

// ----------------------------------------------------------------------------
// New Rubik's Cube Specific Game JNI Interfaces
// ----------------------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_example_cube_NativeCubeLib_nativeRotateLayer(JNIEnv* env, jobject obj, jint axis, jint layer, jint direction) {
    if (gState.isAnimating) {
        return JNI_FALSE;
    }
    gState.isAnimating = true;
    gState.animAxis = axis;
    gState.animLayer = layer;
    gState.animDirection = direction;
    gState.animProgress = 0.0f;
    gState.moveCount++;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeScramble(JNIEnv* env, jobject obj, jint moves) {
    scrambleCube(moves);
}

JNIEXPORT jboolean JNICALL
Java_com_example_cube_NativeCubeLib_nativeIsSolved(JNIEnv* env, jobject obj) {
    if (gState.isAnimating) {
        return JNI_FALSE;
    }
    return checkSolved() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_cube_NativeCubeLib_nativeResetCube(JNIEnv* env, jobject obj) {
    resetCubeData();
}

JNIEXPORT jint JNICALL
Java_com_example_cube_NativeCubeLib_nativeGetMoveCount(JNIEnv* env, jobject obj) {
    return gState.moveCount;
}

} // extern "C"
