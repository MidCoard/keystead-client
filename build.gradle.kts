import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

group = "top.focess"
version = "0.4.1-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("top.focess:keystead-core:0.4.0")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("net.java.dev.jna:jna-platform:5.19.0")
    implementation("de.swiesend:secret-service:2.0.1-alpha")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // keystead-core runs on the classpath here; its fail-closed native locked memory
    // requires native access to be granted to the unnamed module.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "top.focess.keystead.client.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Keystead"
            // Installer version mirrors the project release version (0.4.0).
            // macOS DMG is intentionally NOT built: jpackage's Dmg packager
            // requires MAJOR > 0, so it cannot match a 0.x release version. macOS
            // publishing is deferred until the project reaches a 1.0 standard
            // release where all platforms can share one version. Re-add Dmg (and
            // the macos matrix leg in release.yml) at that point. Bump per release.
            packageVersion = "0.4.0"
            // keystead-core's fail-closed native locked memory requires native access
            // to be granted to the unnamed module. Without this the packaged launcher
            // (Msi/Dmg/Deb) crashes with NativeMemoryUnavailableException on the first
            // secret operation, exactly as the test JVM did before tasks.test set it.
            jvmArgs += "--enable-native-access=ALL-UNNAMED"
        }
    }
}
