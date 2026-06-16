plugins {
    alias(libs.plugins.android.library)
    // No kotlin.android: AGP 9.0+ built-in Kotlin.
}
android {
    namespace = "com.panorama.android"
    compileSdk = 36          // androidx.core 1.19 + media3 1.10 require compileSdk >= 36
    ndkVersion = "29.0.14206865"   // Cardboard SDK native build (see src/main/cpp/README.md)
    defaultConfig {
        minSdk = 29
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17   // kotlin jvmTarget inherits from this
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true   // Robolectric
        unitTests.all { it.maxHeapSize = "1g" }      // Robolectric headroom over the 512m default
    }
    packaging {
        // The Cardboard SDK .aar ships libGfxPluginCardboard.so, and CMake also links the same
        // (byte-identical) .so from src/main/cpp/jni. Take either copy instead of failing on the dup.
        jniLibs.pickFirsts += "**/libGfxPluginCardboard.so"
    }
}
kotlin { jvmToolchain(17) }   // built-in Kotlin: top-level kotlin {} block
dependencies {
    api(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    // Cardboard SDK Java layer (prebuilt from the cardboard-spike :sdk module). The native
    // libGfxPluginCardboard.so calls into these classes via JNI; without them VR aborts with
    // NoClassDefFoundError on protobuf parsing. QR scanning (play-services-vision) is intentionally
    // omitted — we only use the built-in Cardboard V1 viewer profile.
    implementation(files("libs/cardboard/sdk-release.aar"))
    implementation("com.google.protobuf:protobuf-javalite:3.19.4")
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
