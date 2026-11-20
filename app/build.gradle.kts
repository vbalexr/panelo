// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** Copies the repo-root PRIVACY.md into a generated assets dir, so the file is not duplicated in the repo. */
abstract class BundlePrivacy : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        source.get().asFile.copyTo(target.resolve("PRIVACY.md"), overwrite = true)
    }
}

val bundlePrivacy = tasks.register<BundlePrivacy>("bundlePrivacy") {
    source.set(rootProject.layout.projectDirectory.file("PRIVACY.md"))
    outputDir.set(layout.buildDirectory.dir("generated/privacy/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundlePrivacy) { it.outputDir }
    }
}

android {
    namespace = "com.vbalex.android.panelo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vbalex.android.panelo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PRIVACY_URL", "\"https://vbalexr.github.io/panelo/privacy/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
