plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun readFujiNetRuntimeVersion(): String {
    val versionHeader = rootProject.file("tools/fujinet/work/fujinet-firmware/include/version.h")
    if (!versionHeader.isFile) {
        return "fujinet-runtime-v1"
    }
    val match = Regex("""#define\s+FN_VERSION_FULL\s+"([^"]+)"""")
        .find(versionHeader.readText())
    return match?.groupValues?.get(1) ?: "fujinet-runtime-v1"
}

val fujiNetRuntimeVersion = readFujiNetRuntimeVersion()

// AppleWin core version is recorded by tools/applewin/build-applewin-core.sh in a
// .source-info stamp once the core has been staged (Phase 2). Until then, report
// the configured source branch from the staging script, or "Unknown".
fun readAppleWinVersion(): String {
    val stamp = rootProject.file("app/src/main/cpp-generated/applewin/.source-info")
    if (stamp.isFile) {
        val text = stamp.readText()
        val branch = Regex("""^source_branch=(.+)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
        val commit = Regex("""^source_commit=(.+)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()?.take(8)
        return when {
            !branch.isNullOrBlank() && !commit.isNullOrBlank() -> "$branch ($commit)"
            !commit.isNullOrBlank() -> commit
            !branch.isNullOrBlank() -> branch
            else -> "Unknown"
        }
    }
    return "Unknown"
}

val appleWinVersion = readAppleWinVersion()

// Stages the AppleWin libretro core source tree from the local checkout into
// app/src/main/cpp-generated/applewin (see tools/applewin/build-applewin-core.sh).
val prepareAppleWinCore by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Stages the AppleWin libretro core source tree from the local checkout."
    workingDir = rootProject.projectDir
    commandLine("bash", rootProject.file("tools/applewin/build-applewin-core.sh").absolutePath)
    inputs.file(rootProject.file("tools/applewin/build-applewin-core.sh"))
    outputs.dir(project.file("src/main/cpp-generated/applewin"))
}

tasks.named("preBuild").configure {
    dependsOn(prepareAppleWinCore)
}

tasks.matching { task ->
    task.name.startsWith("configureCMake") || task.name.startsWith("buildCMake")
}.configureEach {
    dependsOn(prepareAppleWinCore)
}

// Optional dev override: -Papple2Abi=arm64-v8a builds a single ABI for fast
// iteration. Unset => all four packaged ABIs (release/default).
val apple2Abi: String? = (project.findProperty("apple2Abi") as String?)?.takeIf { it.isNotBlank() }
val fujinetAbiArgs: List<String> =
    if (apple2Abi != null) listOf("--abi", apple2Abi) else listOf("--all-abis")

// Builds the FujiNet APPLE Android runtime (libfujinet.so per ABI + runtime
// assets) from the local fujinet-pc-apple2 checkout. Up-to-date checked on the
// script/support inputs and the generated output dirs, so it only re-runs when
// the build inputs change.
val prepareFujiNetRuntime by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Builds the FujiNet APPLE Android runtime for the packaged ABIs."
    workingDir = rootProject.projectDir
    commandLine(listOf("bash", rootProject.file("tools/fujinet/build-fujinet.sh").absolutePath) + fujinetAbiArgs)
    inputs.file(rootProject.file("tools/fujinet/build-fujinet.sh"))
    inputs.dir(rootProject.file("tools/fujinet/support"))
    outputs.dir(project.file("src/main/assets-generated/fujinet"))
    outputs.dir(project.file("src/main/jniLibs-generated"))
}

tasks.configureEach {
    if (name.contains("Release") || name == "preBuild") {
        dependsOn(prepareFujiNetRuntime)
    }
}

tasks.matching { task ->
    task.name.startsWith("merge") && (
        task.name.endsWith("Assets")
            || task.name.endsWith("JniLibFolders")
            || task.name.endsWith("NativeLibs")
    )
}.configureEach {
    dependsOn(prepareFujiNetRuntime)
}

android {
    namespace = "online.fujinet.go.apple2"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "online.fujinet.go.apple2"
        minSdk = 26
        targetSdk = 35
        versionCode = 3 
        versionName = "0.5.0"
        buildConfigField("String", "APPLEWIN_VERSION", "\"${appleWinVersion}\"")
        buildConfigField("String", "FUJINET_RUNTIME_VERSION", "\"${fujiNetRuntimeVersion}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                // Header-only Boost include dir can be overridden for CI/other hosts.
                (project.findProperty("apple2BoostInclude") as String?)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { arguments += "-DAPPLE2_BOOST_INCLUDE_DIR=$it" }
            }
        }
        ndk {
            if (apple2Abi != null) {
                abiFilters += apple2Abi
            } else {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets {
        getByName("main") {
            // assets-generated/fujinet and jniLibs-generated/<abi>/libfujinet.so
            // are produced by tools/fujinet/build-fujinet.sh.
            assets.srcDir("src/main/assets-generated")
            jniLibs.srcDir("src/main/jniLibs-generated")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation(libs.junit)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.androidx.lifecycle.viewmodel.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
