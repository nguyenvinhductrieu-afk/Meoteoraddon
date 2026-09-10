buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Meteor
    modImplementation(libs.meteor.client)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}

// ─────────────────────────────────────────────────────────────
//  ProGuard obfuscation task — runs after remapJar
//  Usage: ./gradlew proguardJar
//  Output: build/libs/<name>-<version>-obf.jar
// ─────────────────────────────────────────────────────────────
tasks.register<proguard.gradle.ProGuardTask>("proguardJar") {
    dependsOn(tasks.named("remapJar"))

    val remapJarTask = tasks.named("remapJar", AbstractArchiveTask::class)
    val outFile = layout.buildDirectory.file(
        "libs/${base.archivesName.get()}-${version}-obf.jar"
    )

    inputs.files(remapJarTask.flatMap { it.archiveFile })
    outputs.file(outFile)

    injars(remapJarTask.flatMap { it.archiveFile })
    outjars(outFile)

    // ── Java runtime libs (Java 9+ jmod files) ───────────────
    val javaHome = File(System.getProperty("java.home"))
    val jmodsDir = javaHome.resolve("jmods")
    if (jmodsDir.exists()) {
        val needed = listOf(
            "java.base.jmod", "java.desktop.jmod", "java.logging.jmod",
            "java.management.jmod", "java.naming.jmod", "java.net.http.jmod",
            "java.sql.jmod", "java.xml.jmod"
        )
        needed.forEach { jmodName ->
            val jmod = jmodsDir.resolve(jmodName)
            if (jmod.exists()) {
                libraryjars(
                    mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                    jmod
                )
            }
        }
    } else {
        // Java 8 fallback
        libraryjars(File(System.getProperty("java.home"), "lib/rt.jar"))
    }

    // ── Compile-time dependencies (Minecraft, Fabric, Meteor) ─
    configurations.compileClasspath.get()
        .filter { it.isFile && it.name.endsWith(".jar") }
        .forEach { libraryjars(it) }

    // ── ProGuard rules ────────────────────────────────────────
    configuration(file("proguard.pro"))

    doLast {
        val jar = outFile.get().asFile
        if (jar.exists()) {
            println("\n╔═══════════════════════════════════════════╗")
            println("║  Obfuscated JAR ready:                    ║")
            println("║  ${jar.name.padEnd(43)}║")
            println("║  Size: ${"%,d bytes".format(jar.length()).padEnd(37)}║")
            println("╚═══════════════════════════════════════════╝")
        } else {
            println("WARNING: ProGuard task finished but output JAR not found!")
        }
    }
}
