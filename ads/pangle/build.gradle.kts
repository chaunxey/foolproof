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

    // 穿山甲官方 Maven：com.pangle.cn:ads-sdk-pro（仅选中 pangle 的构建会进 :app）
    implementation(libs.pangle.ads)

    testImplementation(libs.junit)
}
