plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
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
                "--app-version", "1.0.0",
                "--vendor", "MDD",
                "--icon", layout.projectDirectory.file("mdd.ico").asFile.absolutePath,
                "--input", libDir.absolutePath,
                "--runtime-image", runtimeDir.absolutePath,
                "--main-jar", mainJar.name,
                "--main-class", "org.example.launcher.app.DesktopLauncher",
                "--dest", dest.absolutePath)
        }
    }
}