plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 落ち物パズル(基本設計書5節)のゲームロジック。Android・UI に依存しない純粋な Kotlin モジュール。
// 後続のミニゲームを追加しても影響が出ないよう、ゲームごとに独立したモジュールにする(ロードマップ Phase 4)。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
