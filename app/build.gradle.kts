import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// versionCode = major*10000 + minor*100 + patch (ADR-009). CI passes -PversionName=x.y.z from the tag.
val versionNameProp = (project.findProperty("versionName") as String?) ?: "0.0.1"
val versionParts = versionNameProp.split(".").map { it.toInt() }
require(versionParts.size == 3 && versionParts[1] <= 99 && versionParts[2] <= 99) {
    "versionName must be MAJOR.MINOR.PATCH with minor/patch <= 99, got $versionNameProp"
}
val versionCodeComputed = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

// Update source (docs/07, Q2 in docs/11). Overridable with -PupdateRepoOwner / -PupdateRepoName.
val updateRepoOwner = (project.findProperty("updateRepoOwner") as String?) ?: "adam-czop"
val updateRepoName = (project.findProperty("updateRepoName") as String?) ?: "gabi-games"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.familygames"
    // Current AndroidX releases require compiling against API 37; runtime behaviour stays on targetSdk 36 (ADR-013).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.familygames" // Q1 in docs/11 — must be final before the first release
        minSdk = 28
        targetSdk = 36
        versionCode = versionCodeComputed
        versionName = versionNameProp
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
    }

    signingConfigs {
        create("release") {
            val path = signingValue("storeFile", "KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug" // debug and release can live side by side
            // Kiosk stays off on developer devices; test it on an emulator with `-Pkiosk=true` (docs/06 §2).
            val kiosk = (project.findProperty("kiosk") as String?).toBoolean()
            buildConfigField("boolean", "KIOSK_ENABLED", kiosk.toString())
            manifestPlaceholders["kioskHome"] = kiosk.toString()
        }
        release {
            buildConfigField("boolean", "KIOSK_ENABLED", "true")
            manifestPlaceholders["kioskHome"] = "true"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a keystore (local dev) the release APK stays unsigned instead of failing the build.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Polish only and forced in MainActivity: never split language resources out of a bundle.
    bundle {
        language { enableSplit = false }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        lintConfig = file("lint.xml")
    }

    sourceSets {
        // Robolectric reads the variant's assets, so MigrationTestHelper finds the schemas only here (debug APK only).
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // Robolectric: strings and assets for the seeder tests
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
}
