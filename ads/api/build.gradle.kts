plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foolproof.ads.api"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // AdGate / FeatureId 在 :base；provider 模块需要直接看到这些类型，因此用 api 暴露。
    api(project(":base"))

    testImplementation(libs.junit)
}
