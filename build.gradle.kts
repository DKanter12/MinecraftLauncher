import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

/** Версия лаунчера из манифеста (используется для ZIP и version.txt). */
fun readLauncherVersion(manifest: File): String {
    if (!manifest.isFile) return "dev"
    return Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
        .find(manifest.readText())?.groupValues?.get(1) ?: "dev"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "org.example.launcher.app.DesktopLauncher"
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

// ZIP для самообновления: содержимое app-image MDD кладётся в корень
// архива, чтобы апдейтер скопировал его поверх домашнего каталога.
// Запуск: .\gradlew.bat launcherUpdatePackage
tasks.register("launcherUpdatePackage") {
    group = "distribution"
    description = "Собирает ZIP обновления и печатает SHA-256 и размер"
    dependsOn(tasks.named("desktopPackage"))
    doLast {
        val dist = layout.buildDirectory.dir("dist/MDD").get().asFile
        require(dist.isDirectory) { "App image not found: $dist" }
        val manifest = layout.projectDirectory.file("launcher-version.json").asFile
        val version = readLauncherVersion(manifest)
        val outDir = layout.buildDirectory.dir("updates").get().asFile
        outDir.mkdirs()
        val zipFile = File(outDir, "launcher-$version.zip")
        zipFile.delete()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            dist.walkTopDown()
                .filter { it.isFile }
                .filter { file ->
                    val name = file.relativeTo(dist).invariantSeparatorsPath
                    // Только файлы лаунчера: код в app/ и маркер версии.
                    // runtime/ и MDD.exe остаются от установленной сборки.
                    name == "version.txt" || name.startsWith("app/")
                }
                .sortedBy { it.path }
                .forEach { file ->
                    val name = file.relativeTo(dist).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        zipFile.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha = digest.digest().joinToString("") { String.format("%02x", it.toInt() and 0xff) }
        println("Update package: ${zipFile.absolutePath}")
        println("Size: ${zipFile.length()}")
        println("SHA-256: $sha")
    }
}

// Готовая к запуску с рабочего стола сборка: installDist + jpackage
// app-image (MDD.exe с иконкой, без консоли). WiX не нужен.
tasks.register("desktopPackage") {
    group = "distribution"
    description = "Собирает приложение MDD.exe (app-image) с иконкой"
    dependsOn(tasks.named("installDist"))
    doLast {
        val install = layout.buildDirectory.dir("install/MinecraftLauncher")
            .get().asFile
        val libDir = File(install, "lib")
        val mainJar = libDir.listFiles { f -> f.isFile }!!.first {
            it.name.startsWith("MinecraftLauncher-") && it.extension == "jar"
        } ?: error("Main jar not found in $libDir")
        val jpackage = sequenceOf(
            File(System.getenv("JAVA_HOME") ?: "", "bin/jpackage.exe"),
            File("C:/Program Files/Java/jdk-17/bin/jpackage.exe"),
            File("C:/Program Files/Common Files/Oracle/Java/javapath/jpackage.exe"),
        ).firstOrNull { it.isFile } ?: error("jpackage.exe not found")
        val jlink = File(jpackage.parentFile, "jlink.exe")
        require(jlink.isFile) { "jlink.exe not found next to jpackage" }
        val dest = layout.buildDirectory.dir("dist").get().asFile

        // Runtime собираем через jlink вручную: у jpackage's встроенного
        // jlink на этой системе из runtime/bin пропадают .exe (java.exe
        // и launcher), из-за чего app-image не запускался.
        val runtimeDir = layout.buildDirectory.dir("jlink-runtime").get().asFile
        runtimeDir.deleteRecursively()
        exec {
            commandLine(jlink.absolutePath,
                "--add-modules",
                "java.base,java.compiler,java.datatransfer,java.xml," +
                    "java.prefs,java.desktop,java.instrument,java.logging," +
                    "java.management,java.security.sasl,java.naming,java.rmi," +
                    "java.management.rmi,java.net.http,java.scripting," +
                    "java.security.jgss,java.smartcardio,java.transaction.xa," +
                    "java.sql,java.sql.rowset,java.xml.crypto,jdk.accessibility," +
                    "jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi," +
                    "jdk.dynalink,jdk.internal.le,jdk.httpserver," +
                    "jdk.incubator.vector,jdk.internal.opt,jdk.jartool," +
                    "jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom," +
                    "jdk.zipfs",
                "--output", runtimeDir.absolutePath)
        }

        // jpackage отказывается перезаписывать существующий образ
        File(dest, "MDD").deleteRecursively()
        exec {
            commandLine(jpackage.absolutePath,
                "--type", "app-image",
                "--name", "MDD",
                "--app-version", "1.1.0",
                "--vendor", "MDD",
                "--icon", layout.projectDirectory.file("mdd.ico").asFile.absolutePath,
                "--input", libDir.absolutePath,
                "--runtime-image", runtimeDir.absolutePath,
                "--main-jar", mainJar.name,
                "--main-class", "org.example.launcher.app.DesktopLauncher",
                "--dest", dest.absolutePath)
        }

        // Установленная версия читается из version.txt рядом с MDD.exe.
        // Для dev-запусков (IDE/Gradle) остаётся AppVersion.BUILT_IN.
        val version = readLauncherVersion(
            layout.projectDirectory.file("launcher-version.json").asFile)
        File(dest, "MDD/version.txt").writeText(version)
    }
}