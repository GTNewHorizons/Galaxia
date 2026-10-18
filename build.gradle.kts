import java.security.MessageDigest
import java.util.HexFormat
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.gradle.process.ExecOperations
import javax.inject.Inject

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

if (providers.gradleProperty("horizonQaClientReportDir").isPresent || providers.gradleProperty("horizonQaJar").isPresent) {
    tasks.named<JavaExec>("runClient") {
        doFirst("Apply Galaxia QA display defaults") {
            val options = File((this as JavaExec).workingDir, "options.txt")
            val defaults = mapOf("guiScale" to "2", "fov" to "1.0", "clouds" to "false")
            val retained = if (options.isFile) options.readLines().filter { it.substringBefore(':') !in defaults } else emptyList()
            options.parentFile.mkdirs()
            options.writeText((retained + defaults.map { (key, value) -> "$key:$value" }).joinToString("\n", postfix = "\n"))
        }
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


val forecastInputFile = layout.buildDirectory.file("rocket-forecasts/inputs.txt")
tasks.register<JavaExec>("exportRocketForecastInputs") {
    group = "verification"
    description = "Exports shared numerical definitions without bootstrapping game content"
    dependsOn(tasks.compileJava)
    classpath = files(sourceSets.main.get().output.classesDirs, sourceSets.main.get().compileClasspath, configurations.runtimeClasspath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    mainClass.set("com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner")
    args("inputs", forecastInputFile.get().asFile.absolutePath, "defaults")
    outputs.file(forecastInputFile)
}


abstract class PrepareRocketForecasts @Inject constructor(private val processes: ExecOperations) : DefaultTask() {
    @get:InputFile abstract val canonicalInputs: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val algorithmSources: ConfigurableFileCollection
    @get:Classpath abstract val numericalClasspath: ConfigurableFileCollection
    @get:Input abstract val javaExecutable: Property<String>
    @get:Input abstract val regenerationAnswer: Property<String>
    @get:Input abstract val releaseBuild: Property<Boolean>
    @get:OutputFile abstract val bakedData: RegularFileProperty
    @get:OutputDirectory abstract val packagedResources: DirectoryProperty

    @TaskAction fun prepare() {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(canonicalInputs.get().asFile.readBytes())
        algorithmSources.files.sortedBy { it.name }.forEach {
            digest.update(it.name.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(it.readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        }
        val fingerprint = HexFormat.of().formatHex(digest.digest())
        val baked = bakedData.get().asFile
        val current = if (baked.isFile) try {
            DataInputStream(GZIPInputStream(baked.inputStream())).use {
                it.readUTF() == "galaxia_circular_forecasts_v1" && it.readUTF() == fingerprint
            }
        } catch (_: Exception) { false } else false
        var answer = regenerationAnswer.get()
        if (answer !in listOf("yes", "no", "ask")) {
            throw GradleException("Use -ProcketForecastRegenerate=yes or -ProcketForecastRegenerate=no")
        }
        if (!current) {
            logger.warn("Rocket forecasts are missing or stale. Choose -ProcketForecastRegenerate=yes or -ProcketForecastRegenerate=no")
        }
        if (releaseBuild.get()) answer = "yes"
        else if (!current && answer == "ask") {
            val console = System.console() ?: throw GradleException(
                "Noninteractive stale forecast build requires -ProcketForecastRegenerate=yes or -ProcketForecastRegenerate=no"
            )
            answer = console.readLine("Regenerate rocket forecasts? [yes/no] ").trim().lowercase()
            if (answer !in listOf("yes", "no")) throw GradleException("A yes or no regeneration choice is required")
        }
        processes.javaexec {
            executable(javaExecutable.get())
            classpath(numericalClasspath)
            mainClass.set("com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner")
            args("prepare", baked.absolutePath, packagedResources.get().asFile.absolutePath,
                fingerprint, answer, releaseBuild.get().toString())
        }.assertNormalExitValue()
    }
}

val preparedForecastResources = layout.buildDirectory.dir("generated/rocketForecastResources")
val prepareRocketForecasts = tasks.register<PrepareRocketForecasts>("prepareRocketForecasts") {
    group = "build"
    description = "Checks packaged forecast freshness and honors the explicit regeneration choice"
    dependsOn("exportRocketForecastInputs")
    canonicalInputs.set(forecastInputFile)
    algorithmSources.from(fileTree("src/main/java/com/gtnewhorizons/galaxia/registry/orbital") { include("*.java") })
    algorithmSources.from(
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/CelestialRegistry.java",
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/CelestialBodyProperties.java",
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/CelestialObject.java",
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/CelestialObjectKey.java",
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/asteroid/AsteroidFieldOrbitResolver.java",
        "src/main/java/com/gtnewhorizons/galaxia/registry/celestial/asteroid/content/GeneratedAsteroids.java"
    )
    numericalClasspath.from(sourceSets.main.get().output.classesDirs, sourceSets.main.get().compileClasspath, configurations.runtimeClasspath)
    javaExecutable.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
        .map { it.executablePath.asFile.absolutePath })
    regenerationAnswer.set(providers.gradleProperty("rocketForecastRegenerate").orElse("ask"))
    releaseBuild.set(providers.environmentVariable("GITHUB_REF").map { it.startsWith("refs/tags/") }.orElse(false))
    bakedData.set(layout.projectDirectory.file("src/rocketForecasts/forecasts.bin.gz"))
    packagedResources.set(preparedForecastResources)
    // Freshness is cheap and must still be checked when a developer explicitly declines regeneration.
    outputs.upToDateWhen { false }
}
sourceSets.main { resources.srcDir(preparedForecastResources) }
tasks.processResources { mustRunAfter(prepareRocketForecasts) }
tasks.assemble { dependsOn(prepareRocketForecasts) }
tasks.jar { dependsOn(prepareRocketForecasts) }
tasks.named("sourcesJar") { dependsOn(prepareRocketForecasts) }
tasks.matching { it.name.startsWith("publish") }.configureEach { dependsOn(prepareRocketForecasts) }
