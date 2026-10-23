plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foolproof.ads.pangle"
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
    implementation(project(":ads:api"))

    // GroMore 融合 SDK：聚合广告位（1 开头）必须用它；仅选中 pangle 的构建会进 :app
    implementation(libs.pangle.mediation)

    testImplementation(libs.junit)
}
