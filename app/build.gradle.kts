import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = if (keystorePropertiesFile.isFile) {
    Properties().apply { keystorePropertiesFile.inputStream().use { load(it) } }
} else null

fun gitOutput(vararg arguments: String): String = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", *arguments)
}.standardOutput.asText.get().trim()

val gitCommitCount = gitOutput("rev-list", "HEAD", "--count")
val gitCommitHash = gitOutput("rev-parse", "--short", "HEAD")

android {
    namespace = "io.github.a13e300.notificationmanager"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.a13e300.notificationmanager"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "$gitCommitCount-$gitCommitHash"
        base.archivesName = "NotificationManager-$versionName"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-paging:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.paging:paging-compose:3.5.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
