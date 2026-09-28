plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.yoolife.stepsync"
    // connect-client:1.1.0 이 36 이상을 요구한다
    compileSdk = 36

    defaultConfig {
        applicationId = "kr.yoolife.stepsync"
        // 삼성헬스 Data SDK 가 Android 10(API 29) 이상을 요구한다
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // 디버그 키로 서명된 APK를 그대로 사이드로딩한다 (스토어 배포 안 함)
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 삼성헬스 Data SDK (app/libs/*.aar). 과거 채우기에만 쓴다 — SamsungHealth.kt
    // 매일 동기화는 아래 Health Connect 그대로.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
