import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/buildConfig/commonMain/kotlin"))
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization)
            implementation(libs.ktor.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.core)
        }
    }
}

val generateBuildConfig by tasks.registering {
    val version = project.version.toString()
    val outputDir = layout.buildDirectory.dir("generated/buildConfig/commonMain/kotlin")
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val buildConfigFile = outputDir.get().file("de/quati/shome/BuildInfo.kt").asFile
        buildConfigFile.parentFile.mkdirs()
        buildConfigFile.writeText(
            """
            package de.quati.shome

            object BuildInfo {
                const val VERSION = "$version"
            }
            """.trimIndent()
        )
    }
}

tasks.configureEach {
    if (name.startsWith("compileKotlin") || name.startsWith("compileCommonMainKotlin")) {
        dependsOn(generateBuildConfig)
    }
}