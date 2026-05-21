plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.466")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
