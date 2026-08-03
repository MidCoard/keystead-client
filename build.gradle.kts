import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "top.focess"
version = "0.4.3-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

dependencies {
    implementation("top.focess:keystead-core:0.4.4-SNAPSHOT")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("net.java.dev.jna:jna-platform:5.19.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // keystead-core runs on the classpath here; its fail-closed native locked memory
    // requires native access to be granted to the unnamed module.
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        // Tink 1.22 currently brings Protobuf, which still uses the JDK's deprecated
        // sun.misc.Unsafe memory helpers. JDK 25 supports explicitly allowing the
        // current behavior; remove this once Protobuf no longer makes that call.
        "--sun-misc-unsafe-memory-access=allow",
    )
}

tasks.register<Test>("liveServerVaultSmoke") {
    group = "verification"
    description =
        "Runs the real two-device encrypted-vault round trip against a live Keystead Server."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/LiveTwoClientVaultFlowTest.class")
    outputs.upToDateWhen { false }
    environment(
        "KEYSTEAD_LIVE_TEST_URL",
        providers.gradleProperty("keysteadSmokeServerUrl")
            .orElse(providers.environmentVariable("KEYSTEAD_LIVE_TEST_URL"))
            .orElse("http://127.0.0.1:8080")
            .get(),
    )
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
    )
    shouldRunAfter(tasks.test)
}

compose.desktop {
    application {
        mainClass = "top.focess.keystead.client.MainKt"
        jvmArgs += "--sun-misc-unsafe-memory-access=allow"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Keystead"
            // The packaged image is produced with jlink. HttpClient is loaded by
            // the server clients at runtime, so static module discovery does not
            // reliably include it in the minimized JRE.
            modules("java.net.http")
            // Installer version mirrors the project release version (0.4.1).
            // macOS DMG is intentionally NOT built: jpackage's Dmg packager
            // requires MAJOR > 0, so it cannot match a 0.x release version. macOS
            // publishing is deferred until the project reaches a 1.0 standard
            // release where all platforms can share one version. Re-add Dmg (and
            // the macos matrix leg in release.yml) at that point. Bump per release.
            packageVersion = "0.4.2"
            // keystead-core's fail-closed native locked memory requires native access
            // to be granted to the unnamed module. Without this the packaged launcher
            // (Msi/Dmg/Deb) crashes with NativeMemoryUnavailableException on the first
            // secret operation, exactly as the test JVM did before tasks.test set it.
            jvmArgs +=
                listOf(
                    "--enable-native-access=ALL-UNNAMED",
                    "--sun-misc-unsafe-memory-access=allow",
                )
            windows {
                iconFile.set(project.file("src/main/resources/keystead-icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/keystead-icon.png"))
            }
        }
    }
}
