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

    // Load secrets from local.properties or env
    val props = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { props.load(it) }
    }

    defaultConfig {
        applicationId = "sg.org.bcc.attendance"
        minSdk = 30
        targetSdk = 36
        versionCode = 120
        versionName = "1.0.0-beta.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val googleClientSecretsJson =
            (
                props.getProperty("GOOGLE_CLIENT_SECRETS_JSON")
                    ?: System.getenv("GOOGLE_CLIENT_SECRETS_JSON")?.let {
                        // If it's base64 encoded (as passed from GitHub Secrets), decode it
                        try {
                            Base64.getDecoder().decode(it.trim()).decodeToString()
                        } catch (e: Exception) {
                            it
                        }
                    }
                    ?: ""
            ).trim()
        val masterSheetId = (props.getProperty("MASTER_SHEET_ID") ?: System.getenv("MASTER_SHEET_ID") ?: "").trim()
        val eventSheetId = (props.getProperty("EVENT_SHEET_ID") ?: System.getenv("EVENT_SHEET_ID") ?: "").trim()

        buildConfigField("String", "GOOGLE_CLIENT_SECRETS_JSON", "\"${googleClientSecretsJson.replace("\"", "\\\"")}\"")
        buildConfigField("String", "MASTER_SHEET_ID", "\"$masterSheetId\"")
        buildConfigField("String", "EVENT_SHEET_ID", "\"$eventSheetId\"")
    }

    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("RELEASE_STORE_FILE") ?: props.getProperty("RELEASE_STORE_FILE")
            if (storeFileEnv != null) {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: props.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: props.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: props.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    androidComponents {
        onVariants { variant ->
            if (variant.buildType == "release") {
                variant.outputs.forEach { output ->
                    val abi =
                        output.filters
                            .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                            ?.identifier

                    // Increment version code based on ABI to avoid conflicts in Play Store/Installers
                    // Priority: arm64-v8a > armeabi-v7a > x86_64 > x86
                    val abiCode =
                        when (abi) {
                            "arm64-v8a" -> 4
                            "armeabi-v7a" -> 3
                            "x86_64" -> 2
                            "x86" -> 1
                            else -> 0
                        }
                    if (abiCode > 0) {
                        (output as com.android.build.api.variant.impl.VariantOutputImpl).versionCode.set(
                            variant.outputs
                                .first()
                                .versionCode
                                .get()!! * 10 + abiCode,
                        )
                    }

                    val archSuffix = if (abi != null) "-$abi" else "-universal"
                    (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                        "attendance-v${output.versionName.get()}$archSuffix.apk",
                    )
                }
            }
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
