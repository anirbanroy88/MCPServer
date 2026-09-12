plugins {
    java
    alias(libs.plugins.spring.boot)
}
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    implementation(libs.spring.ai.mcp.webmvc)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.oauth.resource.server)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.jar { enabled = false }
tasks.bootJar { archiveFileName.set("openfigi-mcp.jar") }
tasks.test {
    dependsOn(tasks.bootJar)
    systemProperty("openfigi.test.jar", tasks.bootJar.get().archiveFile.get().asFile.absolutePath)
}
