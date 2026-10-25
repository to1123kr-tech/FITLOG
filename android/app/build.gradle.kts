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

    // ★ 저장소에 넣어둔 디버그 키로 서명한다.
    //
    // 기본값(각 빌드 머신의 ~/.android/debug.keystore)을 쓰면 **CI 가 빌드할 때마다
    // 키를 새로 만들어서 서명이 달라진다.** 그러면 덮어쓰기 설치가 거부되고
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE) 지웠다 깔아야 하는데, 그때
    // **삼성헬스 권한이 날아가서 복구가 안 된다.** 실제로 그렇게 잃었다.
    //
    // 키를 고정하면 `adb install -r` 로 덮어쓰기가 되고 권한이 유지된다.
    // 스토어에 올리지 않는 개인 사이드로딩 전용이라 디버그 키로 충분하다.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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

    // ⚠️ fileTree 로 넣은 aar 는 POM 이 없어서 자기 의존성을 못 끌고 온다.
    //    없으면 컴파일은 되고 실행 중에 NoClassDefFoundError 로 앱이 죽는다.
    //    아래 둘은 aar 안의 클래스가 실제로 참조하는 것을 확인하고 넣은 것.
    //    (coroutines / androidx.annotation 은 아래 것들이 이미 끌고 온다)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.1.20")

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
