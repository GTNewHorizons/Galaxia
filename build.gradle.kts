plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.register<Exec>("run + RenderDoc") {
    val javaExecTask = tasks.withType<JavaExec>().named("runClient25").get()
    val javaHome = javaExecTask.javaLauncher.get().metadata.installationPath.asFile.absolutePath

    commandLine = listOf(
        "C:\\Program Files\\RenderDoc\\renderdoccmd.exe",
        "capture",
        "--opt-hook-children",
        "--wait-for-exit",
        "--working-dir",
        ".",
        "$javaHome/bin/java.exe",
        "-Xmx64m",
        "-Xms64m",
        "-Dorg.gradle.appname=gradlew",
        "-Dorg.gradle.java.home=$javaHome",
        "-Dorg.lwjgl.util.Debug=true",
        "-classpath",
        "gradle/wrapper/gradle-wrapper.jar",
        "org.gradle.wrapper.GradleWrapperMain",
        "runClient"
    )
}
