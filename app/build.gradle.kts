plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val betaRepository = providers.gradleProperty("duitBetaRepository").orElse("").get()
require(betaRepository.isEmpty() || betaRepository.matches(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+"))) {
    "duitBetaRepository must be a GitHub owner/repository slug"
}
val betaVersionCode = providers.gradleProperty("duitBetaVersionCode").orElse("1").get().toInt()
require(betaVersionCode in 1..2100000000)
val betaVersionName = providers.gradleProperty("duitBetaVersionName").orElse("0.1.0-beta").get()
val betaAppName = providers.gradleProperty("duitBetaAppName").orElse("duit. Beta").get()
// Stable X.Y.Z must use versionCode X*1_000_000 + Y*1_000 + Z, the rule the in-app updater relies on.
Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(betaVersionName)?.destructured?.let { (major, minor, patch) ->
    require(betaVersionCode == major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()) {
        "Stable $betaVersionName needs duitBetaVersionCode=${major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()}"
    }
}
val betaKeyStore = providers.environmentVariable("DUIT_BETA_KEYSTORE").orNull

android {
    namespace = "com.diniy.duit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diniy.duit"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (betaKeyStore != null) {
            create("beta") {
                storeFile = file(betaKeyStore)
                storePassword = providers.environmentVariable("DUIT_BETA_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DUIT_BETA_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("DUIT_BETA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // R8 compiles Compose to fast, optimised code; debug builds feel noticeably slower (Phase 11P).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            signingConfig = signingConfigs.findByName("beta")
            matchingFallbacks += listOf("release")
            buildConfigField("String", "BETA_REPOSITORY", "\"$betaRepository\"")
            manifestPlaceholders["appLabel"] = betaAppName
        }
        // Release-like build for on-device performance checks. Separate app ID, so it never touches
        // the installed app or its data; signed with the debug key so it can be sideloaded.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            matchingFallbacks += listOf("release")
        }
        // Everyday sideload build: release speed (R8), same app ID and debug key as the debug build,
        // so it installs over the existing app and keeps its data.
        create("fluid") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildTypes.configureEach {
        if (name != "beta") {
            sourceSets.getByName(name).java.srcDir("src/noUpdater/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("beta")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(betaVersionCode)
            output.versionName.set(betaVersionName)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Robolectric UI test of the beta update dialog (same catalog entries as androidTest).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
