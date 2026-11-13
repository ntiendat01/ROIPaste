plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// AGP 9 enables built-in Kotlin and defaults to an older KGP.
// Pin a current stable Kotlin Gradle plugin for the Compose compiler plugin.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
