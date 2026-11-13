import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "graffiti"
version = "1.0.1"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "graffiti.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "GraffitiPad"
            packageVersion = "1.0.1"
            description = "Write Palm Graffiti strokes, get keystrokes"
            // java.desktop is needed for java.awt.Robot (keystrokes)
            modules("java.desktop")
            windows {
                menuGroup = "Graffiti Pad"
                shortcut = true
                dirChooser = true
                upgradeUuid = "6F3C2A7E-4B1D-4E8A-9C55-2D7B1A0E9F31"
            }
        }
    }
}

// Portable build: a folder with GraffitiPad.exe and its own Java runtime, zipped up.
// Unzip anywhere on a Windows PC and run GraffitiPad.exe; nothing is installed.
//   gradlew.bat packagePortable  ->  build\portable\GraffitiPad-<version>-portable.zip
tasks.register<Zip>("packagePortable") {
    group = "compose desktop"
    description = "Zips the self-contained app folder into a portable, no-install build."
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("GraffitiPad-${project.version}-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
}

// Single-file build: one GraffitiPad.exe with the portable app embedded in it. On first run it
// unpacks to %LOCALAPPDATA%\GraffitiPad\ and starts from there (see src/launcher/Launcher.cs).
// It is compiled with the C# compiler that ships with Windows (.NET Framework 4).
//   gradlew.bat packageSingleExe  ->  build\single\GraffitiPad-<version>.exe
tasks.register<Exec>("packageSingleExe") {
    group = "compose desktop"
    description = "Builds one self-unpacking GraffitiPad.exe that needs no install."
    dependsOn("packagePortable")
    val zip = layout.buildDirectory.file("portable/GraffitiPad-${project.version}-portable.zip").get().asFile
    val work = layout.buildDirectory.dir("single").get().asFile
    val source = file("src/launcher/Launcher.cs")
    inputs.file(source)
    inputs.file(zip)
    val exe = File(work, "GraffitiPad-${project.version}.exe")
    outputs.file(exe)
    val csc = File(System.getenv("WINDIR") ?: "C:/Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe")
    executable = csc.path
    args(
        "/nologo", "/target:winexe", "/optimize+",
        "/out:" + exe.path,
        "/reference:System.IO.Compression.dll",
        "/reference:System.IO.Compression.FileSystem.dll",
        "/reference:System.Windows.Forms.dll",
        "/resource:" + zip.path + ",app.zip",
        source.path,
        File(work, "BuildInfo.cs").path,
    )
    doFirst {
        if (!csc.exists()) throw GradleException("C# compiler not found at $csc (needs .NET Framework 4, part of Windows 10/11)")
        work.mkdirs()
        // Build id = version + hash of the embedded app, so every new build unpacks fresh.
        val hash = MessageDigest.getInstance("SHA-256").digest(zip.readBytes())
            .joinToString("") { b -> "%02x".format(b) }.take(12)
        File(work, "BuildInfo.cs").writeText(
            "static class BuildInfo { public const string Id = \"${project.version}-$hash\"; }\n"
        )
    }
}
