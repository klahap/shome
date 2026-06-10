import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.app.shared)

            implementation(libs.compose.ui)
        }
    }
}

val serverDst = rootProject.layout.projectDirectory.dir("server/src/main/resources/static")
val cleanWasmDistribution by tasks.registering(Delete::class) {
    description = "clean wasm distribution in server resources"
    delete(serverDst)
}
val copyWasmDistributionProd by tasks.registering(Copy::class) {
    description = "copy wasm prod distribution to server resources"
    dependsOn(
        cleanWasmDistribution,
        tasks.named("wasmJsBrowserDistribution"),
    )
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(serverDst)
}
val copyWasmDistributionDev by tasks.registering(Copy::class) {
    description = "copy wasm dev distribution to server resources"
    dependsOn(
        cleanWasmDistribution,
        tasks.named("wasmJsBrowserDevelopmentExecutableDistribution"),
    )
    from(layout.buildDirectory.dir("dist/wasmJs/developmentExecutable"))
    into(serverDst)
}
