import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.artifact.SingleArtifact
import com.google.protobuf.gradle.id
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.openapi.generator)
}

// This entire source tree is an isolated test overlay, never shipping source.
check(rootProject.file("ISOLATED_UPDATER07").isFile &&
    providers.gradleProperty("lunoco.isolatedUpdater07").orNull == "true") {
    "Isolated updater07 requires its marker and explicit opt-in"
}
check(System.getenv("CI") != "true") { "No CI signing or deployment for this local test copy" }
val isolatedStore = rootProject.file("../private/isolated-test07.keystore")
val secretFile = rootProject.file("../private/test-password.dpapi")
check(isolatedStore.isFile && secretFile.isFile) { "Owner-local test key is required; never use the production key" }
val secretProcess = ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
    "$"+"s=Get-Content -Raw -LiteralPath $"+"env:LUNOCO_TEST_SECRET_FILE | ConvertTo-SecureString; "+
    "$"+"p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($"+"s); "+
    "try {[Console]::Write([Runtime.InteropServices.Marshal]::PtrToStringBSTR($"+"p))} "+
    "finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($"+"p); $"+"s.Dispose()}")
    .apply { environment()["LUNOCO_TEST_SECRET_FILE"] = secretFile.path }.start()
val isolatedPassword = secretProcess.inputStream.bufferedReader().readText()
check(secretProcess.waitFor(20, TimeUnit.SECONDS) &&
    secretProcess.exitValue() == 0 && isolatedPassword.isNotEmpty()) { "Test credential unlock failed" }
val isolatedCert = KeyStore.getInstance("PKCS12").apply {
    isolatedStore.inputStream().use { load(it, isolatedPassword.toCharArray()) }
}.getCertificate("isolated-updater07")
val isolatedFingerprint = MessageDigest.getInstance("SHA-256")
    .digest(isolatedCert.encoded).joinToString("") { "%02x".format(it) }
check(isolatedFingerprint == "45359a801cfc4271823d0822ee64f18ba9b057b71ea7f8c2746aae98a1414d92")
check(isolatedFingerprint != "57a0d2a501b9a679311a88aad8d9b41a1b605119ce2cf638694dae2563cf1045")
val isolatedProtocol = rootProject.file("app/src/main/java/com/github/damontecres/wholphin/services/LunocoReleaseProtocol.kt").readText()
check(isolatedProtocol.contains("https://api.github.com/repos/rgordonSJO/lunoco-updater-isolated-test-07/releases/latest"))
check(!isolatedProtocol.contains("https://api.github.com/repos/rgordonSJO/lunoco/releases/latest"))
val isCI = providers.environmentVariable("CI").orElse("false").map { it.toBoolean() }
val shouldSign =
    isCI.zip(
        providers.environmentVariable("KEY_ALIAS").orElse("").map { it.isNotBlank() },
    ) { isCI, hasKey ->
        isCI && hasKey
    }
val ffmpegModuleExists =
    providers.provider { project.file("libs/lib-decoder-ffmpeg-release.aar").exists() }
val av1ModuleExists =
    providers.provider { project.file("libs/lib-decoder-av1-release.aar").exists() }
val mpvModuleExists =
    providers.provider { project.file("libs/wholphin-mpv-release.aar").exists() }
val extensionsRepoActive =
    providers.provider { project.hasProperty("WholphinExtensionsUsername") }
val requireMpv = providers.gradleProperty("lunatv.requireMpv").map(String::toBoolean).orElse(false)
check(!requireMpv.get() || mpvModuleExists.get() || extensionsRepoActive.get()) {
    "Real MPV is required. Configure the official WholphinExtensions Gradle credentials " +
        "or supply the upstream-built app/libs/wholphin-mpv-release.aar. The stub cannot play media."
}

// See https://issuetracker.google.com/issues/402800800
val isBuildingBundle =
    providers.provider {
        gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
    }

// ZIP downloads, untagged repositories, and machines without Git must still build.
fun gitOutput(vararg arguments: String): String? =
    runCatching {
        val output =
            providers.exec {
                workingDir(rootProject.projectDir)
                commandLine("git", *arguments)
                isIgnoreExitValue = true
            }
        if (output.result.get().exitValue == 0) {
            output.standardOutput.asText.get().trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
    }.getOrNull()

val gitTags = gitOutput("tag", "--list", "v*", "p*")
val gitDescribe = gitOutput("describe", "--tags", "--long", "--match=v*")

// Explicit, reviewed stable releases only. Local/performance suffixes are never release versions.
val lunocoReleaseVersion = providers.gradleProperty("lunoco.releaseVersion").orNull
val lunocoReleaseCode = providers.gradleProperty("lunoco.releaseVersionCode").orNull?.toIntOrNull()
val lunocoStableRelease = lunocoReleaseVersion != null && lunocoReleaseCode != null
check((lunocoReleaseVersion == null) == !providers.gradleProperty("lunoco.releaseVersionCode").isPresent) {
    "Supply both lunoco.releaseVersion and lunoco.releaseVersionCode"
}
check(!lunocoStableRelease || (Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(lunocoReleaseVersion!!) &&
    lunocoReleaseCode!! in 2..2_100_000_000)) { "Stable versionName/code invalid" }
check(!providers.gradleProperty("lunoco.releaseVersionCode").isPresent || lunocoReleaseCode != null)


kotlin {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        jvmTarget = JvmTarget.JVM_11
        javaParameters = true

        // Workaround for https://github.com/google/ksp/issues/2964
        moduleName = "${project.group}_${project.name}"
    }
}

private fun Provider<String>.getInt() = get().toInt()

configure<ApplicationExtension> {
    namespace = "com.github.damontecres.wholphin"
    compileSdk = libs.versions.compileSdk.getInt()

    defaultConfig {
        applicationId = "com.ryan.lunoco.updatertest"
        buildConfigField("boolean", "LUNOCO_ISOLATED_TEST", "true")
        buildConfigField("String", "LUNOCO_FEED_REPOSITORY", "\"rgordonSJO/lunoco-updater-isolated-test-07\"")
        minSdk = libs.versions.minSdk.getInt()
        targetSdk = libs.versions.targetSdk.getInt()
        versionCode = lunocoReleaseCode ?: (gitTags?.lineSequence()?.count { it.isNotBlank() }?.coerceAtLeast(1) ?: 1)
        versionName = lunocoReleaseVersion ?: gitDescribe?.removePrefix("v") ?: "0.0.0-local"
        buildConfigField("boolean", "LUNOCO_STABLE_RELEASE", lunocoStableRelease.toString())
        testInstrumentationRunner = "com.github.damontecres.wholphin.test.WholphinTestRunner"

        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString())
    }

    signingConfigs {
        getByName("debug") {
            storeFile = isolatedStore
            keyAlias = "isolated-updater07"
            storePassword = isolatedPassword
            keyPassword = isolatedPassword
        }
        if (shouldSign.get()) {
            create("ci") {
                file("ci.keystore").writeBytes(
                    Base64.getDecoder().decode(System.getenv("SIGNING_KEY")),
                )
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                storeFile = file("ci.keystore")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isDebuggable = false
            if (shouldSign.get()) {
                signingConfig = signingConfigs.getByName("ci")
            } else {
                val localPropertiesFile = project.rootProject.file("local.properties")
                if (localPropertiesFile.exists()) {
                    val properties = Properties()
                    properties.load(localPropertiesFile.inputStream())
                    val signingConfigName = properties["release.signing.config"]?.toString()
                    if (signingConfigName != null) {
                        signingConfig = signingConfigs.getByName(signingConfigName)
                    }
                }
            }
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "LUNOCO_STABLE_RELEASE", "false")
        }

        // Isolated, release-optimized local diagnostic app; reuse the existing debug key.
        create("performance") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = null // Exact isolated identity; never the production package.
            versionNameSuffix = if (lunocoStableRelease) null else
                "-perf-" + providers.gradleProperty("lunatv.performanceBuildId").orElse("local").get()
            matchingFallbacks += "release"
        }
    }
    flavorDimensions += "version"
    productFlavors {
        val featureLeanback = "leanback"
        val featureUpdate = "UPDATING_ENABLED"
        val featureDiscover = "DISCOVER_ENABLED"

        fun ProductFlavor.setFeatureFlag(
            name: String,
            enabled: Boolean,
        ) {
            this.buildConfigField("boolean", name, "Boolean.parseBoolean(\"${enabled}\")")
        }
        create("default") {
            dimension = "version"
            isDefault = true
            manifestPlaceholders += mapOf(featureLeanback to false)
            // Keep the inherited customizable/upstream updater disabled.
            setFeatureFlag(featureUpdate, false)
            setFeatureFlag("LUNOCO_GITHUB_UPDATES_ENABLED", true)
            setFeatureFlag(featureDiscover, true)
        }
        create("appstore") {
            dimension = "version"
            manifestPlaceholders += mapOf(featureLeanback to true)
            setFeatureFlag(featureUpdate, false)
            setFeatureFlag("LUNOCO_GITHUB_UPDATES_ENABLED", false)
            setFeatureFlag(featureDiscover, true)
        }
        create("firetv") {
            dimension = "version"
            manifestPlaceholders += mapOf(featureLeanback to true)
            setFeatureFlag(featureUpdate, false)
            setFeatureFlag("LUNOCO_GITHUB_UPDATES_ENABLED", false)
            setFeatureFlag(featureDiscover, false)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    splits {
        abi {
            // Disable split abis when building bundles
            isEnable = !isBuildingBundle.get()

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            // Work around because libass-android & wholphin-mpv both (incorrectly) package libc++_shared.so
            pickFirsts += "lib/*/libc++_shared.so"
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories += "$buildDir/generated/seerr_api/src/main/kotlin"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        disable.add("MissingTranslation")
    }
    androidResources {
        generateLocaleConfig = true
    }
}

androidComponents {
    beforeVariants(selector().all()) { builder ->
        builder.enable = builder.name in setOf("defaultPerformance", "defaultDebug")
    }
    onVariants(selector().all()) { variant ->
        variant.outputs
            .map { it as com.android.build.api.variant.impl.VariantOutputImpl }
            .forEach { output ->
                val abi =
                    output
                        .getFilter(FilterConfiguration.FilterType.ABI)
                        .let { if (it != null) "-${it.identifier}" else "" }
                val outputFileName =
                    "Lunoco-${variant.flavorName}-${variant.buildType}-${output.versionName.get()}-${output.versionCode.get()}$abi.apk"
                output.outputFileName = outputFileName
            }

        if (variant.name == "defaultDebug") {
            val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
            val builtArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
            tasks.register("verifyDefaultDebugMpv") {
                group = "verification"
                description = "Checks that every defaultDebug APK contains MPV and its JNI dependencies."
                dependsOn("assembleDefaultDebug")
                inputs.dir(apkDirectory)
                doLast {
                    val builtArtifacts = checkNotNull(builtArtifactsLoader.load(apkDirectory.get())) {
                        "No APK output metadata was produced."
                    }
                    val apks = builtArtifacts.elements.map { file(it.outputFile) }
                    check(apks.isNotEmpty()) { "No defaultDebug APKs were produced." }
                    val requiredLibraries =
                        listOf(
                            "libmpv.so", "libplayer.so", "libavcodec.so", "libavformat.so",
                            "libavfilter.so", "libavdevice.so", "libavutil.so",
                            "libswscale.so", "libswresample.so", "libc++_shared.so",
                        )
                    val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
                    val missing = mutableListOf<String>()
                    apks.sortedBy { it.name }.forEach { apk ->
                        logger.lifecycle("Checking APK: ${apk.absolutePath}")
                        val splitAbi = supportedAbis.firstOrNull { apk.name.endsWith("-$it.apk") }
                        val abis = splitAbi?.let(::listOf) ?: supportedAbis
                        ZipFile(apk).use { zip ->
                            abis.forEach { abi ->
                                requiredLibraries.forEach { library ->
                                    if (zip.getEntry("lib/$abi/$library") == null) {
                                        missing += "${apk.name}: lib/$abi/$library"
                                    }
                                }
                            }
                        }
                    }
                    check(missing.isEmpty()) {
                        "Real MPV packaging check failed. Missing native libraries:\n" +
                            missing.joinToString("\n") +
                            "\nConfigure official extension access or provide verified upstream AARs."
                    }
                    logger.lifecycle("MPV native packaging passed. Device playback still requires a runtime test.")
                }
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.protobuf.kotlin.lite.get().version}"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java") {
                    option("lite")
                }
            }
            it.builtins {
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
aboutLibraries {
    collect {
        configPath = file("config")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/seerr/seerr-api.yml")
    templateDir.set("$projectDir/src/main/seerr/templates")
    outputDir.set("$buildDir/generated/seerr_api")
    apiPackage.set("com.github.damontecres.wholphin.api.seerr")
    modelPackage.set("com.github.damontecres.wholphin.api.seerr.model")
    groupId.set("com.github.damontecres.wholphin.api.seerr")
    id.set("seerr-api")
    packageName.set("com.github.damontecres.wholphin.api.seerr")
    additionalProperties.apply {
        put("serializationLibrary", "kotlinx_serialization")
        put("sortModelPropertiesByRequiredFlag", true)
        put("sortParamsByRequiredFlag", true)
        put("useCoroutines", true)
        put("enumPropertyNaming", "UPPERCASE")
        put("modelMutable", false)

        // Note: this is only for downloading files, so it's not necessary to enable
        put("supportAndroidApiLevel25AndBelow", false)
    }
}

tasks.named("preBuild") {
    dependsOn.add(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.ass.media)
    implementation(libs.exoplayerhdrutils)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.cachecontrol)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.jellyfin.core)
    implementation(libs.jellyfin.api)
    implementation(libs.jellyfin.api.okhttp)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.testing)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.runner)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.timber)
    implementation(libs.slf4j2.timber)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.programguide)
    implementation(libs.acra.http)
    implementation(libs.acra.dialog)
    implementation(libs.acra.limiter)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.kache)
    implementation(libs.kache.file)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    if (ffmpegModuleExists.get()) {
        logger.info("Using local ffmpeg decoder")
        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    } else if (extensionsRepoActive.get()) {
        logger.info("Using prebuilt ffmpeg decoder")
        implementation(libs.wholphin.extensions.ffmpeg)
    } else {
        logger.warn("Media3 ffmpeg decoder was NOT found")
    }
    if (av1ModuleExists.get()) {
        logger.info("Using local av1 decoder")
        implementation(files("libs/lib-decoder-av1-release.aar"))
    } else if (extensionsRepoActive.get()) {
        logger.info("Using prebuilt av1 decoder")
        implementation(libs.wholphin.extensions.av1)
    } else {
        logger.warn("Media3 av1 decoder was NOT found")
    }
    if (mpvModuleExists.get()) {
        logger.info("Using local libMPV build")
        implementation(files("libs/wholphin-mpv-release.aar"))
    } else if (extensionsRepoActive.get()) {
        logger.info("Using prebuilt libMPV")
        implementation(libs.wholphin.extensions.mpv)
    } else {
        logger.warn("libMPV was NOT found, using stub library")
        implementation(project(":wholphin-mpv-stub"))
    }

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
