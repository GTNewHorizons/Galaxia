import com.gtnewhorizons.gtnhgradle.tasks.RunHotswappableMinecraftTask
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.466")
    testRuntimeOnly("org.joml:joml:1.10.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

providers.gradleProperty("horizonQaJar").orNull?.let { horizonQaJar ->
    sourceSets.main {
        java.srcDir("src/gameTest/java")
        resources.srcDir("src/gameTest/resources")
    }
    dependencies {
        add("devOnlyNonPublishable", files(horizonQaJar))
    }
}

// Opt-in native-window lane. The normal client and dedicated server do not load Offscreen.
if (providers.gradleProperty("offscreenRuntime").map(String::toBoolean).getOrElse(false)) {
    dependencies {
        add("runtimeOnlyNonPublishable", files("libs/offscreen-0.3-alpha-dev.jar"))
    }
    configurations.configureEach {
        resolutionStrategy.force("com.github.GTNewHorizons:lwjgl3ify:3.0.25")
    }
    tasks.named<RunHotswappableMinecraftTask>("runClient17") {
        targetJvmVersion.set(25)
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
        enableHotswap.set(false)
    }
}

tasks.test {
    useJUnitPlatform()

    jvmArgs.plus( "--add-opens")
    jvmArgs.plus("java.base/jdk.internal.loader=ALL-UNNAMED")
    jvmArgs.plus( "--add-opens")
    jvmArgs.plus("java.base/java.lang=ALL-UNNAMED")
    jvmArgs.plus( "--add-opens")
    jvmArgs.plus("java.base/java.lang.reflect=ALL-UNNAMED")
}

tasks.register("printRuntimeClasspath") {
    val runtimeClasspath = configurations.named("runtimeClasspath")
    doLast {
        runtimeClasspath.get().files.forEach { println(it.absolutePath) }
    }
}

// Applied after Horizon-QA assigns the isolated game directory.
tasks.named<JavaExec>("runClient") {
    val manualGameplayClient = providers.gradleProperty("horizonQaManual").map { it.toBoolean() }.orElse(false)
    doFirst {
        if (manualGameplayClient.get()) {
            val options = workingDir.resolve("options.txt")
            val defaults = mapOf("guiScale" to "2", "fov" to "1.0", "clouds" to "false")
            val retained = if (options.isFile) {
                options.readLines().filter { it.substringBefore(':') !in defaults }
            } else {
                emptyList()
            }
            options.parentFile.mkdirs()
            options.writeText((retained + defaults.map { (key, value) -> "$key:$value" }).joinToString("\n", postfix = "\n"))
            logger.lifecycle("Manual gameplay settings: GUI Scale 2, Quake Pro FOV, clouds disabled")
        }
    }
}
