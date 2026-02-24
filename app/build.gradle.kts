import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "sg.org.bcc.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "sg.org.bcc.attendance"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load secrets from local.properties
        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        val googleClientSecretsJson = properties.getProperty("GOOGLE_CLIENT_SECRETS_JSON") ?: ""
        val masterSheetId = properties.getProperty("MASTER_SHEET_ID") ?: ""
        val eventSheetId = properties.getProperty("EVENT_SHEET_ID") ?: ""

        buildConfigField("String", "GOOGLE_CLIENT_SECRETS_JSON", "\"${googleClientSecretsJson.replace("\"", "\\\"")}\"")
        buildConfigField("String", "MASTER_SHEET_ID", "\"$masterSheetId\"")
        buildConfigField("String", "EVENT_SHEET_ID", "\"$eventSheetId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
        }
    }
}

tasks.withType<Test> {
    useJUnit()
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleLiveDataKtx)
    implementation(libs.androidxActivityCompose)
    implementation(platform(libs.androidxComposeBom))
    implementation(libs.androidxUi)
    implementation(libs.androidxUiGraphics)
    implementation(libs.androidxUiToolingPreview)
    implementation(libs.androidxMaterial3)
    implementation(libs.androidxMaterialIconsExtended)
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
    implementation(libs.truetime)

    // Testing
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidxTestCore)
    testImplementation(libs.androidxTestExtJunit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(platform(libs.androidxComposeBom))
    androidTestImplementation(libs.androidxUiTestJunit4)
    debugImplementation(libs.androidxUiTooling)
    debugImplementation(libs.androidxUiTestManifest)
}
