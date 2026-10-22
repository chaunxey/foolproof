plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foolproof.ads.meta"
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

    // 广告：仅选中 Meta 的构建会把它带进 :app
    implementation(libs.audience.network)

    testImplementation(libs.junit)
}
