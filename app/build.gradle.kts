plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.singam.lionlibrary"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.singam.lionlibrary"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStore = providers.environmentVariable("LIONLIBRARY_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("LIONLIBRARY_STORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("LIONLIBRARY_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("LIONLIBRARY_KEY_PASSWORD").orNull
    val canSignRelease = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
    signingConfigs {
        if (canSignRelease) create("publicRelease") {
            storeFile = file(releaseStore!!)
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("publicRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsize)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Prefs
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Images
    implementation(libs.coil.compose)

    // Async
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.androidx.documentfile)

    // Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.inspector)

    // VLC Fallback
    implementation(libs.libvlc.all)

    // Pure logic tests follow the installed Android unit-testing skill.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
    // Existing JUnit 4 suites remain runnable through Vintage.
    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
tasks.withType<Test>().configureEach { useJUnitPlatform() }

val kotlinFormatter = configurations.create("kotlinFormatter")
dependencies { add(kotlinFormatter.name, libs.ktfmt) }
val kotlinSources = fileTree("src") { include("**/*.kt") }
tasks.register<JavaExec>("formatKotlin") {
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args(listOf("--kotlinlang-style") + kotlinSources.files.sorted().map { it.absolutePath })
}
tasks.register<JavaExec>("checkKotlinFormat") {
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args(listOf("--kotlinlang-style", "--dry-run", "--set-exit-if-changed") + kotlinSources.files.sorted().map { it.absolutePath })
}
