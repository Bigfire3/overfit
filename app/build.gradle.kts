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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Bewusst im Repo (privates Repo, rein private App), siehe docs/DECISIONS.md.
        create("release") {
            storeFile = rootProject.file("signing/overfit-release.jks")
            storePassword = "overfit-release-2026"
            keyAlias = "overfit"
            keyPassword = "overfit-release-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
