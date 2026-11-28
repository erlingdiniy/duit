import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject

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
val localProps = Properties().apply {
    val lpFile = rootProject.file("local.properties")
    if (lpFile.isFile) {
        lpFile.inputStream().use { load(it) }
    }
}
val devAnalyticsOAuthClientId = providers.gradleProperty("duitDevAnalyticsOAuthClientId").orNull
    ?.trim()
    .orEmpty()
    .ifEmpty { localProps.getProperty("duitDevAnalyticsOAuthClientId", "").trim() }
val devAnalyticsOAuthScheme = if (devAnalyticsOAuthClientId.endsWith(".apps.googleusercontent.com")) {
    "com.googleusercontent.apps.${devAnalyticsOAuthClientId.removeSuffix(".apps.googleusercontent.com")}"
} else {
    "duit-dev-oauth-unconfigured"
}
val devBackendUrl = providers.gradleProperty("duitDevBackendUrl").orNull
    ?.trim()
    .orEmpty()
    .ifEmpty { localProps.getProperty("duitDevBackendUrl", "").trim() }
    .trimEnd('/')
require(
    devBackendUrl.isEmpty() ||
        Regex("""https://[A-Za-z0-9.-]+(?:/[A-Za-z0-9_./-]*)?""").matches(devBackendUrl) ||
        Regex("""http://127\.0\.0\.1:\d+""").matches(devBackendUrl)
) {
    "duitDevBackendUrl must be an HTTPS URL (or loopback 127.0.0.1 for local QA), got $devBackendUrl"
}

// Private developer-enabled build (`betaDev`) shares the com.diniy.duit.beta package and signing key
// and uses major*10_000_000 + minor*10_000 + patch*100 + devN (e.g. 1.2.0-dev.1 = 10020001) so private
// updates stay strictly monotonic above public stable/beta codes (1_002_000) and cannot be downgraded.
val betaDevVersionName = providers.gradleProperty("duitBetaDevVersionName")
    .orElse("${betaVersionName.substringBefore("-beta.")}-dev").get()
val betaDevMatch = requireNotNull(
    Regex("""(\d+)\.(\d+)\.(\d+)-dev(?:\.([1-9]\d?))?""").matchEntire(betaDevVersionName)
) {
    "betaDev versionName must match X.Y.Z-dev or X.Y.Z-dev.N (N 1..99), got $betaDevVersionName"
}
val (bdMajor, bdMinor, bdPatch, bdDevPatch) = betaDevMatch.destructured
val expectedBetaDevCode = bdMajor.toInt() * 10_000_000 +
    bdMinor.toInt() * 10_000 +
    bdPatch.toInt() * 100 +
    bdDevPatch.ifEmpty { "0" }.toInt()
val betaDevVersionCode = providers.gradleProperty("duitBetaDevVersionCode")
    .orElse(expectedBetaDevCode.toString()).get().toInt()
require(betaDevVersionCode == expectedBetaDevCode && betaDevVersionCode >= 10_020_000) {
    "betaDev $betaDevVersionName needs duitBetaDevVersionCode=$expectedBetaDevCode (>= 10020000)"
}

// Personal Edition versioning follows duit's X*1_000_000 + Y*1_000 + Z monotonic rule so upgrades
// over earlier personal installs (e.g. versionCode 1) always succeed without colliding with beta (-beta.).
val personalVersionName = providers.gradleProperty("duitPersonalVersionName").orElse("1.2.0-personal").get()
val personalVersionCode = providers.gradleProperty("duitPersonalVersionCode").orElse("1002000").get().toInt()
require(!personalVersionName.contains("-beta.")) {
    "Personal versionName must not use a -beta. suffix, got $personalVersionName"
}
val personalVersionMatch = requireNotNull(
    Regex("""(\d+)\.(\d+)\.(\d+)(?:-personal(?:\.([1-9]\d?))?)?""").matchEntire(personalVersionName)
) {
    "Personal versionName must match X.Y.Z or X.Y.Z-personal[.N], got $personalVersionName"
}
val (pMajor, pMinor, pPatch, _) = personalVersionMatch.destructured
val expectedPersonalCode = pMajor.toInt() * 1_000_000 + pMinor.toInt() * 1_000 + pPatch.toInt()
require(personalVersionCode == expectedPersonalCode && personalVersionCode >= 1_002_000) {
    "Personal $personalVersionName needs duitPersonalVersionCode=$expectedPersonalCode (>= 1002000)"
}

android {
    namespace = "com.diniy.duit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diniy.duit"
        minSdk = 29
        targetSdk = 35
        versionCode = personalVersionCode
        versionName = personalVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Align Personal Edition (debug/fluid/release) with the exact public v1.2.0 feature set and UX;
        // DEVELOPER_CENTER_ENABLED, Firebase, updater, and feedback URL remain strictly variant-isolated.
        buildConfigField("boolean", "OPTIONAL_COMMISSION", "true")
        buildConfigField("String", "FEEDBACK_URL", "\"\"")
        buildConfigField("boolean", "SUPPORT_PREVIEW", "false")
        buildConfigField("boolean", "PUBLIC_EDITION", "true")
        buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "false")
        buildConfigField("boolean", "FCM_ENABLED", "false")
        buildConfigField("boolean", "PRIVATE_DEV_EDITION", "false")
        buildConfigField("boolean", "CRASHLYTICS_ENABLED", "false")
        buildConfigField("boolean", "ANALYTICS_ENABLED", "false")
        buildConfigField("String", "RELEASE_CHANNEL", "\"personal\"")
        buildConfigField("boolean", "CRASHLYTICS_QA_TEST", "false")
        buildConfigField("boolean", "ANALYTICS_QA_TEST", "false")
        buildConfigField("String", "DEV_ANALYTICS_OAUTH_CLIENT_ID", "\"\"")
        buildConfigField("String", "DEV_BACKEND_URL", "\"\"")
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
        debug {
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "true")
            buildConfigField("String", "DEV_BACKEND_URL", "\"$devBackendUrl\"")
        }
        release {
            // R8 compiles Compose to fast, optimised code; debug builds feel noticeably slower (Phase 11P).
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "true")
            buildConfigField("String", "DEV_BACKEND_URL", "\"$devBackendUrl\"")
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
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "true")
            buildConfigField("boolean", "FCM_ENABLED", "true")
            buildConfigField("boolean", "PRIVATE_DEV_EDITION", "false")
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "true")
            buildConfigField("boolean", "ANALYTICS_ENABLED", "true")
            buildConfigField("String", "RELEASE_CHANNEL", if (betaVersionName.contains("-beta.")) "\"beta\"" else "\"stable\"")
            buildConfigField("boolean", "CRASHLYTICS_QA_TEST", crashlyticsQaTest.toString())
            buildConfigField("boolean", "ANALYTICS_QA_TEST", analyticsQaTest.toString())
            // QA only: shows Support duit. with the placeholder before the real QR exists. Never set for releases.
            buildConfigField("boolean", "SUPPORT_PREVIEW", providers.gradleProperty("duitSupportPreview").orElse("false").get())
            buildConfigField("String", "DEV_ANALYTICS_OAUTH_CLIENT_ID", "\"\"")
            buildConfigField("String", "DEV_BACKEND_URL", "\"$devBackendUrl\"")
        }
        // Private developer-enabled build over com.diniy.duit.beta: exact public v1.2.0 experience + FCM + Developer Center,
        // with public in-app GitHub auto-updates disabled at compile time so public APKs cannot overwrite it.
        create("betaDev") {
            initWith(getByName("beta"))
            matchingFallbacks += listOf("beta", "release")
            buildConfigField("String", "BETA_REPOSITORY", "\"\"")
            buildConfigField("boolean", "FCM_ENABLED", "true")
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "true")
            buildConfigField("boolean", "PRIVATE_DEV_EDITION", "true")
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "true")
            buildConfigField("boolean", "ANALYTICS_ENABLED", "true")
            buildConfigField("String", "RELEASE_CHANNEL", "\"private_dev\"")
            buildConfigField("boolean", "CRASHLYTICS_QA_TEST", crashlyticsQaTest.toString())
            buildConfigField("boolean", "ANALYTICS_QA_TEST", analyticsQaTest.toString())
            buildConfigField("String", "DEV_ANALYTICS_OAUTH_CLIENT_ID", "\"$devAnalyticsOAuthClientId\"")
            buildConfigField("String", "DEV_BACKEND_URL", "\"$devBackendUrl\"")
            manifestPlaceholders["devAnalyticsOAuthScheme"] = devAnalyticsOAuthScheme
        }
        // Release-like build for on-device performance checks. Separate app ID, so it never touches
        // the installed app or its data; signed with the debug key so it can be sideloaded.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "false")
        }
        // Everyday sideload build: release speed (R8), same app ID and debug key as the debug build,
        // so it installs over the existing app and keeps its data.
        create("fluid") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DEVELOPER_CENTER_ENABLED", "true")
        }
    }

    val personalBuildTypes = setOf("debug", "betaDev", "fluid", "release")
    buildTypes.configureEach {
        val isCrashlyticsType = name == "beta" || name == "betaDev"
        (this as org.gradle.api.plugins.ExtensionAware).extensions
            .findByType(com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension::class.java)
            ?.mappingFileUploadEnabled = isCrashlyticsType && uploadCrashlyticsMapping
        when (name) {
            "betaDev" -> {
                sourceSets.getByName(name).res.srcDir("src/beta/res")
                sourceSets.getByName(name).java.srcDir("src/personal/java")
            }
            "beta" -> {
                sourceSets.getByName(name).java.srcDir("src/personal/java")
            }
            else -> {
                sourceSets.getByName(name).java.srcDir("src/noUpdater/java")
                if (name in personalBuildTypes) {
                    sourceSets.getByName(name).java.srcDir("src/personal/java")
                    if (name != "debug") {
                        sourceSets.getByName(name).manifest.srcFile("src/personal/AndroidManifest.xml")
                    }
                } else {
                    sourceSets.getByName(name).java.srcDir("src/noDevCenter/java")
                }
            }
        }
    }

    // Fail configuration immediately if public or benchmark source sets ever pick up forbidden private artifacts.
    listOf("beta", "benchmark").forEach { publicType ->
        val srcSet = sourceSets.getByName(publicType)
        require(
            srcSet.java.srcDirs.none {
                it.invariantSeparatorsPath.endsWith("src/betaDev/java") ||
                    (publicType == "benchmark" && it.invariantSeparatorsPath.endsWith("src/personal/java"))
            }
        ) {
            "Build type '$publicType' must never include forbidden source directories"
        }
        val manifestPath = srcSet.manifest.srcFile.invariantSeparatorsPath
        require(
            !manifestPath.endsWith("src/betaDev/AndroidManifest.xml") &&
                (publicType != "benchmark" || !manifestPath.endsWith("src/personal/AndroidManifest.xml"))
        ) {
            "Build type '$publicType' must never include private developer manifests"
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

abstract class SyncBetaDevSharedSourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(inputDir) {
                exclude("com/diniy/duit/update/**")
            }
            into(outputDir)
        }
    }
}

val syncBetaDevSharedSources by tasks.registering(SyncBetaDevSharedSourcesTask::class) {
    inputDir.set(layout.projectDirectory.dir("src/beta/java"))
}

androidComponents {
    onVariants(selector().withBuildType("beta")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(betaVersionCode)
            output.versionName.set(betaVersionName)
        }
    }
    onVariants(selector().withBuildType("betaDev")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(betaDevVersionCode)
            output.versionName.set(betaDevVersionName)
        }
        variant.sources.java?.addGeneratedSourceDirectory(
            syncBetaDevSharedSources,
            SyncBetaDevSharedSourcesTask::outputDir
        )
    }
}

val syncBetaDevGoogleServices by tasks.registering {
    val srcFile = file("src/beta/google-services.json")
    val dstFile = file("src/betaDev/google-services.json")
    onlyIf { srcFile.exists() }
    if (srcFile.exists()) {
        inputs.file(srcFile)
    }
    outputs.file(dstFile)
    doLast {
        dstFile.parentFile.mkdirs()
        srcFile.copyTo(dstFile, overwrite = true)
    }
}

// Keep Google Services and Crashlytics strictly scoped to beta and betaDev so debug/fluid/release remain Firebase-free.
tasks.matching {
    it.name.startsWith("process") && it.name.endsWith("GoogleServices")
}.configureEach {
    if (name == "processBetaDevGoogleServices") {
        dependsOn(syncBetaDevGoogleServices)
    }
    if (!name.contains("Beta", ignoreCase = true)) {
        enabled = false
    }
}

tasks.matching {
    it.name.contains("Crashlytics", ignoreCase = true) && !it.name.contains("Beta", ignoreCase = true)
}.configureEach {
    enabled = false
}

val verifyBetaReleaseIsolation by tasks.registering {
    group = "verification"
    description = "Verifies public unified beta/stable APKs exclude secrets, PrivateDev artifacts, cleartext traffic, and unexpected permissions."
    val betaApkDir = layout.buildDirectory.dir("outputs/apk/beta")
    val mergedManifestDir = layout.buildDirectory.dir("intermediates/merged_manifests/beta")
    doLast {
        val forbiddenMarkers = listOf(
            "PRIVATE_DEV_EDITION",
            "PrivateDevUpdates",
            "PrivateDevUpdateModel",
            "PrivateDevInstallStatusReceiver",
            "duit-dev-install",
            "DevAnalyticsOAuthRedirectActivity",
            "duit_qa_crash",
            "Phase 20C isolated QA verification crash",
            "duit_qa_analytics",
            "BEGIN PRIVATE KEY",
            "BEGIN RSA PRIVATE KEY",
            "ADMIN_BOOTSTRAP_SECRET",
            "SYNC_HMAC_SECRET",
            "JWT_SIGNING_KEY",
            "duit-beta.p12",
            "github_pat" + "_11",
            "ghp" + "_",
            "gho" + "_",
            "ghs" + "_",
            "ya29" + ".",
            "drc" + "_"
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
            if (xml.contains("networkSecurityConfig")) {
                throw GradleException("verifyBetaReleaseIsolation: debug networkSecurityConfig leaked into ${manifest.absolutePath}")
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

    // Phase 8, 20C & 20D: FCM, Crashlytics & Analytics strictly for beta and private betaDev. Offline personal/debug/release exclude Firebase SDK.
    "betaImplementation"(platform(libs.firebase.bom))
    "betaImplementation"(libs.firebase.analytics)
    "betaImplementation"(libs.firebase.messaging)
    "betaImplementation"(libs.firebase.crashlytics)
    "betaImplementation"(libs.firebase.config)
    "betaDevImplementation"(platform(libs.firebase.bom))
    "betaDevImplementation"(libs.firebase.analytics)
    "betaDevImplementation"(libs.firebase.messaging)
    "betaDevImplementation"(libs.firebase.crashlytics)
    "betaDevImplementation"(libs.firebase.config)

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
