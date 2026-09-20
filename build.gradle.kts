import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.FileSystemException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
    id("com.gradleup.shadow") version "9.6.1"
}

version = property("mod_version") as String
group = property("maven_group") as String
val expandedModVersion = version.toString()

base {
    archivesName.set(property("archives_base_name") as String)
}

// Private copy: never link against the compressor bundled by another mod.
val privateCompression by configurations.creating
configurations.implementation { extendsFrom(privateCompression) }

dependencies {
    privateCompression("io.airlift:aircompressor:0.27") { isTransitive = false }
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    configurations = listOf(privateCompression)
    archiveClassifier.set("dev-shadow")
    relocate("io.airlift.compress", "fr.tropimon.chatfilter.internal.airlift.compress")
    minimize()
}
tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}

java {
    withSourcesJar()
}

// Build-only privacy guard: no dependency or code is added to the client mod.
val privacy by sourceSets.creating
sourceSets.test {
    compileClasspath += privacy.output
    runtimeClasspath += privacy.output
}
val verifyPrivacySources by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks publishable sources without displaying private matched values."
    dependsOn(privacy.classesTaskName)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.chatfilter.privacy.PrivacyCheck")
    args("sources", layout.projectDirectory.asFile.absolutePath)
}
tasks.compileJava { dependsOn(verifyPrivacySources) }
tasks.processResources { dependsOn(verifyPrivacySources) }

val remappedSources = tasks.named<net.fabricmc.loom.task.RemapSourcesJarTask>("remapSourcesJar")
val verifyPrivacyArtifacts by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks final JAR constants, resources, metadata and nested archives."
    dependsOn(privacy.classesTaskName, tasks.jar, tasks.shadowJar, tasks.remapJar, remappedSources)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.chatfilter.privacy.PrivacyCheck")
    doFirst {
        setArgs(listOf("artifacts", layout.projectDirectory.asFile.absolutePath,
                tasks.remapJar.get().archiveFile.get().asFile.absolutePath,
                remappedSources.get().archiveFile.get().asFile.absolutePath,
                tasks.jar.get().archiveFile.get().asFile.absolutePath,
                tasks.shadowJar.get().archiveFile.get().asFile.absolutePath))
    }
}
tasks.check { dependsOn(verifyPrivacySources, verifyPrivacyArtifacts) }
tasks.remapJar { finalizedBy(verifyPrivacyArtifacts) }
remappedSources { finalizedBy(verifyPrivacyArtifacts) }

val deliveryRoot = layout.buildDirectory.dir("delivery/$expandedModVersion")
val prepareChatFilterDelivery by tasks.registering {
    group = "distribution"
    description = "Prepares identical local/share JARs; never installs or schedules an update."
    dependsOn(tasks.check)
    doLast {
        val jar = tasks.remapJar.get().archiveFile.get().asFile
        val root = deliveryRoot.get().asFile
        copy {
            from(jar)
            into(root.resolve("share"))
        }
        copy {
            from(jar)
            into(root.resolve("local"))
            rename { "tropimon-chat-filter-$expandedModVersion-LOCAL.jar" }
        }
        copy {
            from("tools/install-local-deferred.ps1", "tools/InstallManagedLocalMod.ps1")
            into(root.resolve("local"))
        }
        val localJar = root.resolve("local/tropimon-chat-filter-$expandedModVersion-LOCAL.jar")
        val hash = MessageDigest.getInstance("SHA-256").digest(localJar.readBytes()).joinToString("") { "%02x".format(it) }
        localJar.resolveSibling(localJar.name + ".sha256").writeText(hash + "\n")
    }
}
val verifyChatFilterDelivery by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(prepareChatFilterDelivery)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.chatfilter.privacy.PrivacyCheck")
    doFirst {
        val root = deliveryRoot.get().asFile
        val shared = root.resolve("share/tropimon-chat-filter-$expandedModVersion.jar")
        val local = root.resolve("local/tropimon-chat-filter-$expandedModVersion-LOCAL.jar")
        check(shared.readBytes().contentEquals(local.readBytes())) { "Delivery JARs differ." }
        setArgs(listOf("artifacts", layout.projectDirectory.asFile.absolutePath,
                shared.absolutePath, local.absolutePath,
                root.resolve("local/install-local-deferred.ps1").absolutePath,
                root.resolve("local/InstallManagedLocalMod.ps1").absolutePath))
    }
}
tasks.build { finalizedBy(verifyChatFilterDelivery) }

tasks.withType<AbstractArchiveTask>().configureEach {
    exclude(".git/**", "**/.git/**", ".gradle/**", "config/**", "logs/**", "run/**",
            "releases/**", "screenshots/**", "backups/**", "mod-archive/**",
            ".env*", "**/.env*", "**/*.log", "**/*.bak", "**/*.pending",
            "**/*.keystore", "**/*.jks", "**/*.p12", "**/*.hprof")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", expandedModVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedModVersion)
    }
}

// Test-only client. Its entrypoint is never packaged in the distributable mod.
val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
loom {
    mods {
        create("tropimon_chat_filter") { sourceSet(sourceSets.main.get()) }
        create("chat_filter_smoke") { sourceSet(smoke) }
    }
    runs {
        create("smoke") {
            client()
            source(smoke)
            runDir("build/smoke-run")
            vmArg("-Dchatfilter.smoke=true")
            vmArg("-Dfabric.debug.disableErrorGui=true")
            vmArg("-Xmx3G")
        }
    }
}
tasks.named<JavaExec>("runSmoke") {
    providers.gradleProperty("smokeJava").orNull?.let { setExecutable(it) }
}
// Production-mapped smoke helper, kept outside build/libs to prevent accidental sharing.
val smokeJar by tasks.registering(Jar::class) {
    from(smoke.output)
    archiveClassifier.set("smoke-dev")
    destinationDirectory.set(layout.buildDirectory.dir("smoke-helper"))
}
val remapSmokeJar by tasks.registering(net.fabricmc.loom.task.RemapJarTask::class) {
    inputFile.set(smokeJar.flatMap { it.archiveFile })
    archiveClassifier.set("smoke")
    destinationDirectory.set(layout.buildDirectory.dir("smoke-helper"))
    addNestedDependencies.set(false)
}
dependencies {
    add("productionRuntimeMods", "net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProductionSmoke") {
    mods.from(remapSmokeJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs.addAll("-Dchatfilter.smoke=true", "-Dfabric.debug.disableErrorGui=true", "-Xmx4G")
    programArgs.addAll("--username", "ChatFilterTest", "--accessToken", "0", "--version", "ChatFilterSmoke")
    val coexistDirectory = providers.gradleProperty("coexistModsDir").orNull
    runDir.set(layout.buildDirectory.dir(if (coexistDirectory == null) "production-smoke-isolated" else "production-smoke-coexist"))
    // Read-only inputs to this test task, never compile/runtime dependencies of the mod.
    coexistDirectory?.let { directory ->
        mods.from(fileTree(directory) {
            include("Tropi*.jar", "Cobblemon-fabric-*.jar", "fabric-language-kotlin-*.jar",
                    "cloth-config-*.jar", "architectury-*.jar", "XaerosWorldMap*.jar",
                    "morechathistory-*.jar", "chatanimation-*.jar", "geckolib-*.jar",
                    "mega_showdown-*.jar", "trinkets-*.jar")
            exclude("TropimonChatFilter-*.jar")
        })
    }
}

tasks.register("installTropimonChatFilterLocal") {
    dependsOn(verifyPrivacyArtifacts)
    doLast {
        val launcher = file("${System.getProperty("user.home")}/AppData/Roaming/.tropimon")
        val mods = launcher.resolve("mods").toPath()
        val archive = launcher.resolve("mod-archive/tropimon-chat-filter").toPath()
        Files.createDirectories(mods)
        Files.createDirectories(archive)
        val source = tasks.remapJar.get().archiveFile.get().asFile.toPath()
        val target = mods.resolve(
            "TropimonChatFilter-${expandedModVersion}+1.21.1-LOCAL.jar"
        )
        val pending = mods.resolve(".${target.fileName}.pending")
        Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.list(mods).use { files ->
                files.filter { path ->
                    path.fileName.toString().matches(
                        Regex("TropimonChatFilter-.*\\.jar", RegexOption.IGNORE_CASE)
                    )
                }.forEach { oldJar ->
                    val archived = archive.resolve(
                        oldJar.fileName.toString() + "." + System.currentTimeMillis() + ".bak"
                    )
                    try {
                        Files.move(oldJar, archived)
                    } catch (error: FileSystemException) {
                        throw GradleException(
                            "Le JAR est encore utilisé. Fermez Tropimon puis relancez l'installation.",
                            error
                        )
                    }
                }
            }
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (ignored: AtomicMoveNotSupportedException) {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
        println("Installé dans $target")
    }
}


val prepareReleaseDelivery = tasks.register("prepareReleaseDelivery") {
    group = "distribution"
    description = "Produit les JAR local et partageable vérifiés de la même version."
    dependsOn(tasks.build)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        val deliveryRoot = layout.buildDirectory.dir("release").get().asFile
        val shareDirectory = deliveryRoot.resolve("shareable")
        val localDirectory = deliveryRoot.resolve("local")
        shareDirectory.deleteRecursively()
        localDirectory.deleteRecursively()
        shareDirectory.mkdirs()
        localDirectory.mkdirs()

        fun copyAndHash(target: File) {
            source.copyTo(target, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            target.resolveSibling(target.name + ".sha256").writeText(hash + System.lineSeparator())
        }

        copyAndHash(shareDirectory.resolve("TropimonChatFilter-${project.version}+1.21.1.jar"))
        copyAndHash(localDirectory.resolve("TropimonChatFilter-${project.version}+1.21.1-LOCAL.jar"))
        file("tools/install-local-deferred.ps1")
            .copyTo(localDirectory.resolve("install-local-deferred.ps1"), overwrite = true)
        file("tools/InstallManagedLocalMod.ps1")
            .copyTo(localDirectory.resolve("InstallManagedLocalMod.ps1"), overwrite = true)
    }
}

tasks.register("armReleaseLocal") {
    group = "distribution"
    description = "Arme l'installation locale différée sans arrêter Minecraft ni le launcher."
    dependsOn(prepareReleaseDelivery)
    doLast {
        val script = layout.buildDirectory.file("release/local/install-local-deferred.ps1").get().asFile
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .directory(script.parentFile)
            .start()
    }
}

