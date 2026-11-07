plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "app.overfit.shared"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.jb.navigation.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.ktor.client.core)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.backhandler)
            api(libs.room.runtime)
            implementation(libs.coil.compose.mp)
            implementation(libs.coil.svg.mp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Notifications (NotificationCompat) and Dispatchers.Main for the workout controller.
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.zxing.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // Android nutzt das System-SQLite (Room-Standard), iOS das gebündelte SQLite.
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        // Prüft in den Android-Host-Tests, dass der eigene QR-Generator echte, lesbare QR-Codes erzeugt.
        getByName("androidHostTest").dependencies {
            implementation(libs.zxing.core)
        }
    }
}

dependencies {
    // Room-Code für jedes Target generieren (KSP2).
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
}

room {
    // Exportiertes Schema: Grundlage für Migrationen und Migrationstests (nie destruktiv migrieren).
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.overfit.shared.resources"
}
