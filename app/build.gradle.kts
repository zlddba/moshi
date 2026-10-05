import com.android.build.api.variant.FilterConfiguration
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

val appBaseName = "moshi"
val appVersion = "0.1"
val appId = "dev.zlddba.moshiapp"
val splitAbis = listOf("arm64-v8a")

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.zlddba.moshiapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/*.kotlin_module"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include(*splitAbis.toTypedArray())
            isUniversalApk = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier ?: "universal"
            output.outputFileName.set("$appBaseName-$appVersion-$abi.apk")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("installArchApk") {
    group = "build"
    description = "构建 debug APK 并安装指定架构到指定设备：-PadbDevice=<序列号> -PapkAbi=<abi>"
    val device = providers.gradleProperty("adbDevice").orNull.orEmpty()
    val abi = providers.gradleProperty("apkAbi").orNull ?: "arm64-v8a"
    val projectPath = projectDir.absolutePath
    val buildPath = layout.buildDirectory.get().asFile.absolutePath
    val applicationId = appId
    dependsOn("assembleDebug")
    doLast {
        if (device.isEmpty()) {
            error("未指定设备：先 adb devices 查看序列号，再加 -PadbDevice=<序列号>")
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val adbName = if (os.contains("windows")) "adb.exe" else "adb"
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: run {
                val props = Properties()
                val localFile = File(projectPath, "local.properties")
                if (localFile.isFile) localFile.inputStream().use { props.load(it) }
                props.getProperty("sdk.dir")
            }
        if (sdkDir.isNullOrEmpty()) {
            error("未找到 Android SDK：请设置 ANDROID_HOME 或 local.properties 的 sdk.dir")
        }
        val adb = File(sdkDir, "platform-tools/$adbName")
        if (!adb.isFile) error("未找到 adb：${adb.absolutePath}")
        val apkDir = File(buildPath, "outputs/apk/debug")
        val apks = apkDir.listFiles()?.filter { it.isFile && it.extension == "apk" }.orEmpty()
        val apk = apks.firstOrNull { it.name.endsWith("-$abi.apk") }
            ?: apks.firstOrNull {
                it.name.contains(abi) && !(abi == "x86" && it.name.contains("x86_64"))
            }
            ?: error("未找到 $abi 架构的 APK：${apkDir.absolutePath}")
        println("安装 ${apk.name} 到 $device")
        val exit = ProcessBuilder(
            adb.absolutePath,
            "-s",
            device,
            "install",
            "-r",
            apk.absolutePath
        ).inheritIO().start().waitFor()
        if (exit != 0) error("adb install 失败（exit=$exit）：${apk.name}")
        val launch = ProcessBuilder(
            adb.absolutePath,
            "-s",
            device,
            "shell",
            "am",
            "start",
            "-n",
            "$applicationId/.activities.launchPage.LaunchActivity"
        ).inheritIO().start().waitFor()
        if (launch != 0) error("启动应用失败（exit=$launch）")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    // JUnit 6 单元测试
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Coil
    implementation(libs.coil.compose)

    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.sherpa.onnx)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    implementation(libs.commonmark.autolink)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.pdfbox.android)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)
    implementation(libs.google.localagents.rag)
    implementation(libs.litertlm.android)
    implementation(libs.protobuf.javalite)
    implementation(libs.sqlcipher.android)
}