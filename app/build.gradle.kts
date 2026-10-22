import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// 广告 provider：meta（默认）/ pangle（穿山甲）。
// 只把选中的 provider 挂到 :app，保证单个产物里只有一套广告 SDK。
val adSdk = providers.gradleProperty("foolproof.adSdk").getOrElse("meta").trim().lowercase()
val availableAdSdks = setOf("meta", "pangle")
check(adSdk in availableAdSdks) {
    "foolproof.adSdk=$adSdk 不可用；当前可选：${availableAdSdks.joinToString()}"
}

android {
    namespace = "com.foolproof"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.foolproof"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (!keystoreProperties.isEmpty) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // 只编译当前 provider 的工厂文件，另一个 provider 的源码不参与构建
    sourceSets.getByName("main").kotlin.srcDir("src/ad/$adSdk/kotlin")
    androidResources {
        localeFilters += listOf("zh", "en", "ja", "fr", "de", "km")
    }
    packaging {
        resources {
            // BouncyCastle 的后量子算法参数文件约 4MB，普通 TLS 用不到
            excludes += setOf(
                "org/bouncycastle/pqc/**",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(project(":base"))
    implementation(project(":common"))
    implementation(project(":features:image-compression"))
    implementation(project(":features:duplicate-cleanup"))
    implementation(project(":features:video-compression"))
    implementation(project(":features:file-conversion"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // :app 需要直接从 Koin 取 feature 的 ViewModel（页面组装的一部分）
    implementation(libs.koin.compose.viewmodel)
    // 广告：抽象 + 当前选中的 provider（feature 仍只依赖 :base 的 AdGate）
    implementation(project(":ads:api"))
    when (adSdk) {
        "meta" -> implementation(project(":ads:meta"))
        "pangle" -> implementation(project(":ads:pangle"))
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}