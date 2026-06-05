plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.example"
  compileSdk = 35
  ndkVersion = "28.2.13676358"

  defaultConfig {
    applicationId = "com.aistudio.ndkcube.tgkwxv"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val customKeystore = file("${rootDir}/debug.keystore")
      if (customKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.compose.ui:ui:1.7.5")
  implementation("androidx.compose.ui:ui-graphics:1.7.5")
  implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material:material-icons-core:1.7.5")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

  debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.test.ext:junit:1.2.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.robolectric:robolectric:4.14.1")
}
