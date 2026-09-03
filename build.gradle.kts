import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "top.focess"
version = "1.1.1"

val isMacHost = System.getProperty("os.name").lowercase().contains("mac")
val macTouchIdHelper = layout.buildDirectory.file("app-resources/macos/keystead-mac-secure-store")
val unsignedMacTouchIdHelper = layout.buildDirectory.file("tmp/mac-touch-id/keystead-mac-secure-store")
val macDmgFile = layout.buildDirectory.file("compose/binaries/main/dmg/Keystead-1.1.1.dmg")
val compileMacTouchIdHelperBinary =
    tasks.register<Exec>("compileMacTouchIdHelperBinary") {
        onlyIf { isMacHost }
        val source = layout.projectDirectory.file("src/main/swift/KeysteadMacSecureStore.swift")
        val infoPlist = layout.projectDirectory.file("src/main/swift/KeysteadMacSecureStore-Info.plist")
        inputs.file(source)
        inputs.file(infoPlist)
        outputs.file(unsignedMacTouchIdHelper)
        doFirst { unsignedMacTouchIdHelper.get().asFile.parentFile.mkdirs() }
        commandLine(
            "xcrun",
            "swiftc",
            "-O",
            "-framework",
            "LocalAuthentication",
            "-framework",
            "Security",
            "-framework",
            "CryptoKit",
            "-Xlinker",
            "-sectcreate",
            "-Xlinker",
            "__TEXT",
            "-Xlinker",
            "__info_plist",
            "-Xlinker",
            infoPlist.asFile.absolutePath,
            source.asFile.absolutePath,
            "-o",
            unsignedMacTouchIdHelper.get().asFile.absolutePath,
        )
    }
val compileMacTouchIdHelper =
    tasks.register<Exec>("compileMacTouchIdHelper") {
        onlyIf { isMacHost }
        dependsOn(compileMacTouchIdHelperBinary)
        inputs.file(unsignedMacTouchIdHelper)
        outputs.file(macTouchIdHelper)
        doFirst {
            val target = macTouchIdHelper.get().asFile
            target.parentFile.mkdirs()
            unsignedMacTouchIdHelper.get().asFile.copyTo(target, overwrite = true)
            check(target.setExecutable(true, false)) { "Could not make the macOS Touch ID helper executable" }
        }
        commandLine(
            "codesign",
            "--force",
            "--sign",
            "-",
            "--identifier",
            "top.focess.keystead.touch-id-helper",
            macTouchIdHelper.get().asFile.absolutePath,
        )
    }

val fixMacDmgVolumeIcon =
    tasks.register<Exec>("fixMacDmgVolumeIcon") {
        onlyIf { isMacHost }
        val brandIcon = layout.projectDirectory.file("src/main/resources/keystead-icon.icns")
        val iconScript = layout.projectDirectory.file("scripts/set-macos-dmg-icon.sh")
        inputs.file(brandIcon)
        inputs.file(iconScript)
        outputs.upToDateWhen { false }
        commandLine(
            "bash",
            iconScript.asFile.absolutePath,
            macDmgFile.get().asFile.absolutePath,
            brandIcon.asFile.absolutePath,
        )
    }

tasks.configureEach {
    if (name == "prepareAppResources") dependsOn(compileMacTouchIdHelper)
    if (name == "packageDmg") finalizedBy(fixMacDmgVolumeIcon)
    if (name == "createDistributable" && isMacHost) {
        inputs.file(macTouchIdHelper)
        doLast {
            val packagedHelper =
                layout.buildDirectory
                    .file("compose/binaries/main/app/Keystead.app/Contents/app/resources/keystead-mac-secure-store")
                    .get()
                    .asFile
            check(packagedHelper.isFile && packagedHelper.setExecutable(true, false) && packagedHelper.canExecute()) {
                "Packaged macOS Touch ID helper is missing or not executable"
            }
        }
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

dependencies {
    implementation("top.focess:keystead-core:0.5.2")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-resources:1.10.0")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("net.java.dev.jna:jna-platform:5.19.0")
    testImplementation(kotlin("test"))
}

compose.resources {
    packageOfResClass = "top.focess.keystead.client.generated.resources"
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
    if (isMacHost) {
        dependsOn(compileMacTouchIdHelper)
        systemProperty("keystead.mac.touch-id.helper", macTouchIdHelper.get().asFile.absolutePath)
    }
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
            .orElse("http://127.0.0.1:22144")
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
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Keystead"
            appResourcesRootDir.set(layout.buildDirectory.dir("app-resources"))
            // The packaged image is produced with jlink. HttpClient is loaded by
            // the server clients at runtime, so static module discovery does not
            // reliably include it in the minimized JRE.
            modules("java.net.http")
            // Installer version mirrors the project release version. Bump per release.
            // macOS DMG requires MAJOR > 0; the project is now 1.x so Dmg is built.
            packageVersion = "1.1.1"
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
            macOS {
                iconFile.set(project.file("src/main/resources/keystead-icon.icns"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/keystead-icon.png"))
            }
        }
    }
}
