import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "sg.org.bcc.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "sg.org.bcc.attendance"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load secrets from local.properties or env
        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        val googleClientSecretsJson = (properties.getProperty("GOOGLE_CLIENT_SECRETS_JSON")
            ?: System.getenv("GOOGLE_CLIENT_SECRETS_JSON")?.let {
                // If it's base64 encoded (as passed from GitHub Secrets), decode it
                try {
                    Base64.getDecoder().decode(it.trim()).decodeToString()
                } catch (e: Exception) {
                    it
                }
            }
            ?: "").trim()
        val masterSheetId = (properties.getProperty("MASTER_SHEET_ID") ?: System.getenv("MASTER_SHEET_ID") ?: "").trim()
        val eventSheetId = (properties.getProperty("EVENT_SHEET_ID") ?: System.getenv("EVENT_SHEET_ID") ?: "").trim()

        buildConfigField("String", "GOOGLE_CLIENT_SECRETS_JSON", "\"${googleClientSecretsJson.replace("\"", "\\\"")}\"")
        buildConfigField("String", "MASTER_SHEET_ID", "\"$masterSheetId\"")
        buildConfigField("String", "EVENT_SHEET_ID", "\"$eventSheetId\"")
    }

    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("RELEASE_STORE_FILE")
            if (storeFileEnv != null) {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

tasks.withType<Test> {
    useJUnit()
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleProcess)
    implementation(libs.androidxLifecycleLiveDataKtx)
    implementation(libs.androidxActivityCompose)
    implementation(platform(libs.androidxComposeBom))
    implementation(libs.androidxUi)
    implementation(libs.androidxUiGraphics)
    implementation(libs.androidxUiToolingPreview)
    implementation(libs.androidxMaterial3)
    implementation(libs.androidxHiltNavigationCompose)
    implementation(libs.material)
    implementation(libs.androidxWorkRuntimeKtx)
    implementation(libs.androidxHiltWork)
    implementation(libs.androidxSecurityCrypto)
    implementation(libs.androidxBrowser)
    implementation(libs.googleApiClientAndroid)
    implementation(libs.googleSheetsApi)
    implementation(libs.googleAuthLibrary)
    implementation(libs.googleHttpJson)
    implementation(libs.androidxCredentials)
    implementation(libs.androidxCredentialsPlayServices)
    implementation(libs.googleId)
    ksp(libs.androidxHiltCompiler)

    // CameraX
    implementation(libs.androidxCameraCore)
    implementation(libs.androidxCameraCamera2)
    implementation(libs.androidxCameraLifecycle)
    implementation(libs.androidxCameraView)

    // ML Kit
    implementation(libs.mlkitBarcodeScanning)

    // ZXing
    implementation(libs.zxingCore)

    // Room
    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    ksp(libs.roomCompiler)

    // Hilt
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)

    // Utilities
    implementation(libs.kotlinxDatetime)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.semver)
    implementation(libs.truetime)

    // Ktor
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientOkHttp)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    // Testing
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidxTestCore)
    testImplementation(libs.androidxTestExtJunit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.junit)
    testImplementation(libs.androidxWorkTesting)

    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(platform(libs.androidxComposeBom))
    androidTestImplementation(libs.androidxUiTestJunit4)
    debugImplementation(libs.androidxUiTooling)
    debugImplementation(libs.androidxUiTestManifest)
}
