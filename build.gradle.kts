plugins {
    base
    alias(libs.plugins.spring.boot) apply false
}
subprojects {
    group = "io.github.anirbanroy88.mcp"
    version = "0.1.0"
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
            options.encoding = "UTF-8"
            options.compilerArgs.add("-parameters")
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}
tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }
