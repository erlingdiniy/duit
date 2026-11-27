import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

if (file("src/beta/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val betaRepository = providers.gradleProperty("duitBetaRepository").orElse("").get()
require(betaRepository.isEmpty() || betaRepository.matches(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+"))) {
    "duitBetaRepository must be a GitHub owner/repository slug"
}
val betaVersionCode = providers.gradleProperty("duitBetaVersionCode").orElse("1").get().toInt()
require(betaVersionCode in 1..2100000000)
val betaVersionName = providers.gradleProperty("duitBetaVersionName").orElse("0.1.0-beta").get()
val betaAppName = providers.gradleProperty("duitBetaAppName").orElse("duit. Beta").get()
// Public Apps Script /exec URL (not a secret: it ships in the APK). Only the public edition uses it.
val feedbackUrl = providers.gradleProperty("duitFeedbackUrl").orElse("").get()
require(feedbackUrl.isEmpty() || Regex("""https://script\.google\.com/macros/s/[A-Za-z0-9_-]+/exec""").matches(feedbackUrl)) {
    "duitFeedbackUrl must be an Apps Script /exec URL"
}
// Stable X.Y.Z must use versionCode X*1_000_000 + Y*1_000 + Z, the rule the in-app updater relies on.
Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(betaVersionName)?.destructured?.let { (major, minor, patch) ->
    require(betaVersionCode == major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()) {
        "Stable $betaVersionName needs duitBetaVersionCode=${major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()}"
    }
}
// Beta X.Y.0-beta.N (N 1..99) sits just below stable X.Y.0: versionCode X*1_000_000 + Y*1_000 - 100 + N,
// e.g. 1.2.0-beta.1 = 1001901. Only .0 releases get betas, so beta codes never collide with each other.
if (betaVersionName.contains("-beta.")) {
    val (major, minor, patch, n) = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)-beta\.([1-9]\d?)""").matchEntire(betaVersionName)) {
        "Beta versionName must look like X.Y.0-beta.N (N 1..99), got $betaVersionName"
    }.destructured
    require(patch == "0" && major.toInt() * 1_000 + minor.toInt() > 0) { "Betas only precede X.Y.0 releases, got $betaVersionName" }
    val expected = major.toInt() * 1_000_000 + minor.toInt() * 1_000 - 100 + n.toInt()
    require(betaVersionCode == expected) { "Beta $betaVersionName needs duitBetaVersionCode=$expected" }
}
val betaKeyStore = providers.environmentVariable("DUIT_BETA_KEYSTORE").orNull
val uploadCrashlyticsMapping = providers.gradleProperty("duitUploadCrashlyticsMapping").orElse("false").get().toBoolean()
val crashlyticsQaTest = providers.gradleProperty("duitCrashlyticsQaTest").orElse("false").get().toBoolean()
val analyticsQaTest = providers.gradleProperty("duitAnalyticsQaTest").orElse("false").get().toBoolean()

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
        // Public edition (beta build type) makes Commission Tracking optional; personal builds keep it always on.
        buildConfigField("boolean", "OPTIONAL_COMMISSION", "false")
        buildConfigField("String", "FEEDBACK_URL", "\"\"")
        buildConfigField("boolean", "SUPPORT_PREVIEW", "false")
        buildConfigField("boolean", "PUBLIC_EDITION", "false")
        buildConfigField("boolean", "FCM_ENABLED", "false")
        buildConfigField("boolean", "PRIVATE_DEV_EDITION", "false")
        buildConfigField("boolean", "CRASHLYTICS_ENABLED", "false")
        buildConfigField("boolean", "ANALYTICS_ENABLED", "false")
        buildConfigField("String", "RELEASE_CHANNEL", "\"personal\"")
        buildConfigField("boolean", "CRASHLYTICS_QA_TEST", "false")
        buildConfigField("boolean", "ANALYTICS_QA_TEST", "false")
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
            buildConfigField("boolean", "OPTIONAL_COMMISSION", "true")
            buildConfigField("String", "FEEDBACK_URL", "\"$feedbackUrl\"")
            buildConfigField("boolean", "PUBLIC_EDITION", "true")
            buildConfigField("boolean", "FCM_ENABLED", "true")
            buildConfigField("boolean", "PRIVATE_DEV_EDITION", "false")
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "true")
            buildConfigField("boolean", "ANALYTICS_ENABLED", "true")
            buildConfigField("String", "RELEASE_CHANNEL", if (betaVersionName.contains("-beta.")) "\"beta\"" else "\"stable\"")
            buildConfigField("boolean", "CRASHLYTICS_QA_TEST", crashlyticsQaTest.toString())
            buildConfigField("boolean", "ANALYTICS_QA_TEST", analyticsQaTest.toString())
            // QA only: shows Support duit. with the placeholder before the real QR exists. Never set for releases.
            buildConfigField("boolean", "SUPPORT_PREVIEW", providers.gradleProperty("duitSupportPreview").orElse("false").get())
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
        (this as org.gradle.api.plugins.ExtensionAware).extensions
            .findByType(com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension::class.java)
            ?.mappingFileUploadEnabled = (name == "beta") && uploadCrashlyticsMapping
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

// Keep Google Services and Crashlytics strictly scoped to beta so personal/debug/release builds remain 100% offline.
tasks.matching {
    it.name.startsWith("process") &&
        it.name.endsWith("GoogleServices") &&
        !it.name.contains("Beta", ignoreCase = true)
}.configureEach {
    enabled = false
}

tasks.matching {
    it.name.contains("Crashlytics", ignoreCase = true) && !it.name.contains("Beta", ignoreCase = true)
}.configureEach {
    enabled = false
}

val verifyBetaReleaseIsolation by tasks.registering {
    group = "verification"
    description = "Verifies public beta/stable APKs and merged manifest enforce privacy flags, permissions, and zero private/QA artifacts."
    val betaApkDir = layout.buildDirectory.dir("outputs/apk/beta")
    val mergedManifestDir = layout.buildDirectory.dir("intermediates/merged_manifests/beta")
    doLast {
        val forbiddenMarkers = listOf(
            "com/diniy/duit/devcenter",
            "DevCenter",
            "devcenter",
            "DeveloperCenter",
            "Developer Center",
            "PRIVATE_DEV_EDITION",
            "PrivateDevUpdates",
            "PrivateDevUpdateModel",
            "PrivateDevInstallStatusReceiver",
            "duit_qa_crash",
            "duit_qa_analytics",
            "oauth2.googleapis.com",
            "analyticsdata.googleapis.com"
        )
        val allowedBetaPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.USE_BIOMETRIC",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.USE_FINGERPRINT",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "com.google.android.c2dm.permission.RECEIVE",
            "android.permission.FOREGROUND_SERVICE",
            "com.diniy.duit.beta.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )

        val manifestFiles = mergedManifestDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .toList()
        if (manifestFiles.isEmpty()) {
            throw GradleException("verifyBetaReleaseIsolation: no merged beta AndroidManifest.xml found under ${mergedManifestDir.get().asFile}")
        }
        manifestFiles.forEach { manifest ->
            val xml = manifest.readText(Charsets.UTF_8)
            forbiddenMarkers.forEach { marker ->
                if (xml.contains(marker)) {
                    throw GradleException("verifyBetaReleaseIsolation: forbidden marker '$marker' found in ${manifest.absolutePath}")
                }
            }
            if (!xml.contains("android:allowBackup=\"false\"")) {
                throw GradleException("verifyBetaReleaseIsolation: android:allowBackup=\"false\" missing in ${manifest.absolutePath}")
            }
            if (!xml.contains("android:usesCleartextTraffic=\"false\"")) {
                throw GradleException("verifyBetaReleaseIsolation: android:usesCleartextTraffic=\"false\" missing in ${manifest.absolutePath}")
            }
            val declaredPerms = Regex("""<uses-permission\b[^>]*android:name="([^"]+)"""")
                .findAll(xml)
                .map { it.groupValues[1] }
                .toSet()
            val unexpectedPerms = declaredPerms - allowedBetaPermissions
            if (unexpectedPerms.isNotEmpty()) {
                throw GradleException("verifyBetaReleaseIsolation: unexpected permissions in ${manifest.absolutePath}: $unexpectedPerms")
            }
        }

        val apkFiles = betaApkDir.get().asFile.listFiles { f -> f.isFile && f.extension == "apk" }?.toList().orEmpty()
        if (apkFiles.isEmpty()) {
            throw GradleException("verifyBetaReleaseIsolation: no beta APK found under ${betaApkDir.get().asFile}")
        }
        apkFiles.forEach { apk ->
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    val isAudited = entryName == "AndroidManifest.xml" ||
                        entryName.startsWith("res/") ||
                        Regex("""classes\d*\.dex""").matches(entryName)
                    if (!isAudited || entry.isDirectory) continue
                    val raw = zip.getInputStream(entry).readBytes()
                    val text = String(raw, Charsets.ISO_8859_1)
                    forbiddenMarkers.forEach { marker ->
                        if (text.contains(marker)) {
                            throw GradleException("verifyBetaReleaseIsolation: forbidden marker '$marker' in ${apk.name}!$entryName")
                        }
                    }
                }
            }
            logger.lifecycle("verifyBetaReleaseIsolation PASS: ${apk.name} (${manifestFiles.size} manifest, clean DEX/resources/permissions)")
        }
    }
}

tasks.matching { it.name == "assembleBeta" }.configureEach {
    finalizedBy(verifyBetaReleaseIsolation)
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

    // Phase 8, 20C & 20D: FCM, Crashlytics & Analytics strictly for beta (public edition). Personal builds include zero Firebase SDK.
    "betaImplementation"(platform(libs.firebase.bom))
    "betaImplementation"(libs.firebase.analytics)
    "betaImplementation"(libs.firebase.messaging)
    "betaImplementation"(libs.firebase.crashlytics)

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
