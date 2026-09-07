// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 nutzt "built-in Kotlin"; die Compiler-Version stammt vom Kotlin Gradle Plugin auf
        // dem Buildscript-Classpath. AGP 9.3.2 bringt nur 2.2.10 mit, aktuelle AndroidX-/Compose-
        // Artefakte tragen aber Kotlin-2.4-Metadaten.
        // Version synchron zu `kotlin` in gradle/libs.versions.toml halten.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}