import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// keystore.properties 是機器本地的（已被 .gitignore 蓋住）。缺檔時整個簽章設定不套，
// 換機器或乾淨 clone 仍能建置 release，只是產出未簽章的 AAB。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.wenhsiaoyi.jackcompass"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hywen.app.jackcompass"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.02"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("upload") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        // 設定頁底部的版本資訊直接讀 BuildConfig.VERSION_NAME／VERSION_CODE
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ads.mobile.sdk)
    implementation(libs.user.messaging.platform)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
