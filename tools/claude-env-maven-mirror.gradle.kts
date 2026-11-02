// Local only: Maven Central answers 429 behind this environment's proxy; use Google's mirror first.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

beforeSettings {
    pluginManagement.repositories.maven { url = uri(mirror); name = "CentralMirror" }
    dependencyResolutionManagement.repositories.maven { url = uri(mirror); name = "CentralMirror" }
}

allprojects {
    tasks.withType<Test>().configureEach {
        // Robolectric downloads its android-all jars from Maven Central at test time.
        systemProperty("robolectric.dependency.repo.url", mirror)
    }
}
