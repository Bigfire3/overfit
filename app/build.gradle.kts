plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// versionCode kommt in CI aus der Run-Nummer, damit jedes APK als Update installierbar ist.
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.overfit"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.overfit"
        minSdk = 26
        targetSdk = 37
        versionCode = ciRunNumber
        versionName = "0.1.$ciRunNumber"
        // credit/invite backend; empty = not configured (the app then shows "coming soon" and the demo). CI variable OVERFIT_BACKEND_URL.
        buildConfigField("String", "OVERFIT_BACKEND_URL", "\"${System.getenv("OVERFIT_BACKEND_URL").orEmpty().trim()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signatur nur, wenn Keystore und Passwort von außen kommen (CI: GitHub-Secrets, siehe README).
    // Ohne diese Angaben entsteht lokal ein unsigniertes Release-Build.
    val keystoreFile = System.getenv("OVERFIT_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    val keystorePassword = System.getenv("OVERFIT_KEYSTORE_PASSWORD")
    val releaseSigning = if (keystoreFile != null && !keystorePassword.isNullOrEmpty()) {
        signingConfigs.create("release") {
            storeFile = keystoreFile
            storePassword = keystorePassword
            keyAlias = "overfit"
            keyPassword = keystorePassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric braucht auf JDK 21 Zugriff auf JDK-Interna.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }

    sourceSets {
        // Exportierte Room-Schemas für MigrationTestHelper (Asset-Ordner = Klassenname der Datenbank).
        // Robolectric-Unit-Tests sehen nur die Assets der App-Variante, daher debug (nicht im Release-APK).
        getByName("debug").assets.srcDir("../shared/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.room.testing)
    testImplementation(libs.sqlite.framework)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Component tests (Column, Row) build small hosts around design-system parts.
    testImplementation(libs.compose.foundation)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
