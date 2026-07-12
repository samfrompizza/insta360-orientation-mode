import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android.gradle.plugin)
    kotlin("kapt")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val localProps =
    Properties().apply {
        load(rootProject.file("local.properties").inputStream())
    }
android {
    namespace = "com.arashivision.sdk.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arashivision.sdk.demo"
        minSdk = 29
        targetSdk = 36
        versionCode = 58
        versionName = libs.versions.insta.get()
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        resources {
            pickFirsts +=
                listOf(
                    "lib/arm64-v8a/libc++_shared.so",
                )
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("G:\\camerasdk\\sdkdemo2\\app\\sdk.jks")
            storePassword = localProps.getProperty("signing.storePassword")
            keyAlias = localProps.getProperty("signing.keyAlias")
            keyPassword = localProps.getProperty("signing.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    applicationVariants.configureEach {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "insta_sdk_demo_${buildType.name}_$versionName.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    ndkVersion = "25.2.9519653"
}

dependencies {

    implementation(project(":domain"))
    implementation(project(":core:base"))
    implementation(project(":core:math"))
    implementation(project(":core:sensor-fusion"))
    implementation(project(":core:detection"))
    implementation(project(":core:vr"))
    implementation(project(":data:camera"))
    implementation(project(":data:sensor"))
    implementation(project(":data:media"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:player"))
    implementation(project(":feature:connect"))
    implementation(project(":feature:shot"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.preference)
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.androidx.viewbinding)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.swiperefreshlayout)

    implementation(libs.xx.permissions)
    implementation(libs.flowlayout)
    implementation(libs.lottie)
    implementation(libs.glide)
    kapt(libs.glide.compiler)
    implementation(libs.immersionbar)
    implementation(libs.xlog)
    implementation(libs.filepicker)

    implementation("androidx.media3:media3-common:1.6.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")

    implementation(libs.insta.camera)
    implementation(libs.insta.media)

    implementation(files("libs/glide_transformations.jar"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

ktlint {
    version = "1.5.0"
    android = true
}

detekt {
    config = files("$rootDir/detekt-config.yml")
    baseline = file("$rootDir/detekt-baseline.xml")
}
