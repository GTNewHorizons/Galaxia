plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

repositories {
    mavenLocal()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.github.GTNewHorizons:GT5-Unofficial:5.99.99-mu6-environments.1")
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
