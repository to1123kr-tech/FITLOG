// connect-client:1.1.0 이 AGP 8.9.1 이상을 요구한다.
// AGP 8.9 는 Gradle 8.11.1 이상이 필요하므로 워크플로의 gradle-version 도 함께 맞출 것.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
