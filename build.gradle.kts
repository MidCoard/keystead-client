import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

group = "top.focess"
version = "0.3.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("top.focess:keystead-core:0.2.0-SNAPSHOT")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.10.0")
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
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Keystead"
            // The installer version is intentionally decoupled from the project's
            // 0.x semver: the macOS Dmg/PkgBuild packagers reject any version whose
            // MAJOR component is 0 (it must be an integer > 0), so a 0.2.0 project
            // version cannot be used as the packageVersion. Keep a stable MAJOR>=1
            // installer version here until the project reaches a real 1.0; bump the
            // minor/patch manually per release if desired.
            packageVersion = "1.0.0"
            // keystead-core's fail-closed native locked memory requires native access
            // to be granted to the unnamed module. Without this the packaged launcher
            // (Msi/Dmg/Deb) crashes with NativeMemoryUnavailableException on the first
            // secret operation, exactly as the test JVM did before tasks.test set it.
            jvmArgs += "--enable-native-access=ALL-UNNAMED"
        }
    }
}
