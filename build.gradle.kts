import org.apache.tools.ant.filters.EscapeUnicode
import java.util.*
import java.text.SimpleDateFormat
import org.jreleaser.model.Active.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("com.diffplug.spotless") version "8.1.0"
    id("me.qoomon.git-versioning") version "6.4.4"
    id("io.freefair.lombok") version "9.1.0"
    id("io.freefair.javadoc-links") version "9.1.0"
    id("io.freefair.javadoc-utf-8") version "9.1.0"
    id("io.freefair.aspectj.post-compile-weaving") version "9.1.0"
    // id("io.freefair.maven-central.validate-poms") version "9.0.0" // TODO: Re-enable when compatible with Gradle 9
    id("com.github.ben-manes.versions") version "0.53.0"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.sentry.jvm.gradle") version "6.0.0-rc.1"
    id("io.github.1c-syntax.bslls-dev-tools") version "0.8.1"
    id("ru.vyarus.pom") version "3.0.0"
    id("org.jreleaser") version "1.22.0"
    id("org.sonarqube") version "7.2.2.6593"
    id("me.champeau.jmh") version "0.7.3"
    id("com.gorylenko.gradle-git-properties") version "2.5.4"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://projectlombok.org/edge-releases")
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

group = "io.github.1c-syntax"
gitVersioning.apply {
    refs {
        describeTagFirstParent = false
        tag("v(?<tagVersion>[0-9].*)") {
            version = $$"${ref.tagVersion}${dirty}"
        }

        branch("develop") {
            version = $$"${describe.tag.version}." +
                    $$"${describe.distance}-SNAPSHOT${dirty}"
        }

        branch(".+") {
            version = $$"${ref}-${commit.short}${dirty}"
        }
    }

    rev {
        version = $$"${commit.short}${dirty}"
    }
}

gitProperties {
    customProperty("git.build.time", buildTime())
}

val languageToolVersion = "6.7"

dependencies {

    // RUNTIME

    // spring
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-websocket")
    api("org.springframework.boot:spring-boot-starter-cache")

    api("info.picocli:picocli-spring-boot-starter:4.7.7")

    // кэширование
    api("com.github.ben-manes.caffeine", "caffeine", "3.2.3")
    api("org.ehcache:ehcache:3.11.1")

    // lsp4j core
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.24.0")
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j.websocket.jakarta", "0.24.0")

    // 1c-syntax
    api("io.github.1c-syntax", "bsl-parser", "0.29.0") {
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.antlr", "antlr-runtime")
    }
    api("io.github.1c-syntax", "utils", "0.6.8")
    api("io.github.1c-syntax", "mdclasses", "0.17.2")
    api("io.github.1c-syntax", "bsl-common-library", "0.9.1")
    api("io.github.1c-syntax", "supportconf", "0.15.0")

    // nullability annotations
    api("org.jspecify", "jspecify", "1.0.0")

    // JLanguageTool
    implementation("org.languagetool", "languagetool-core", languageToolVersion){
        exclude("commons-logging", "commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }
    implementation("org.languagetool", "language-en", languageToolVersion){
        exclude("commons-logging", "commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }
    implementation("org.languagetool", "language-ru", languageToolVersion){
        exclude("commons-logging", "commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }

    // AOP
    implementation("org.aspectj", "aspectjrt", "1.9.25.1")

    // commons utils
    implementation("commons-io", "commons-io", "2.21.0")
    implementation("commons-beanutils", "commons-beanutils", "1.11.0"){
        exclude("commons-logging", "commons-logging")
    }
    implementation("commons-codec", "commons-codec", "1.20.0")
    implementation("org.apache.commons", "commons-lang3", "3.20.0")
    implementation("org.apache.commons", "commons-collections4", "4.5.0")
    implementation("org.apache.commons", "commons-exec", "1.6.0")

    // progress bar
    implementation("me.tongfei", "progressbar", "0.10.1")

    // (de)serialization
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml")
    implementation("io.leangen.geantyref:geantyref:2.0.1")

    // graphs
    implementation("org.jgrapht", "jgrapht-core", "1.5.2")

    // SARIF serialization
    implementation("com.contrastsecurity", "java-sarif", "2.0")

    // CONSTRAINTS
    implementation("com.google.guava:guava") {
        version {
            strictly("33.5.0-jre")
       }
    }
    
    // COMPILE

    // TEST

    // spring
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }

    testImplementation("org.junit.jupiter:junit-jupiter")

    // test utils
    testImplementation("org.jmockit", "jmockit", "1.50")
    testImplementation("org.awaitility", "awaitility", "4.3.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher"
        attributes["Implementation-Version"] = archiveVersion.get()
    }
    enabled = true
    archiveClassifier.set("")
}

tasks.bootJar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion.get()
    }
    archiveClassifier.set("exec")
}

afterEvaluate {
    tasks.named("spotlessJavaCheck") {
        dependsOn(tasks.generateSentryDebugMetaPropertiesjava)
        dependsOn(tasks.collectExternalDependenciesForSentry)
    }

    tasks.named("spotlessJavaApply") {
        dependsOn(tasks.generateSentryDebugMetaPropertiesjava)
        dependsOn(tasks.collectExternalDependenciesForSentry)
    }

    tasks.named("sourcesJar") {
        dependsOn(tasks.generateSentryDebugMetaPropertiesjava)
        dependsOn(tasks.collectExternalDependenciesForSentry)
    }
}

tasks.build {
    dependsOn(tasks.bootJar)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standard_error")
    }

    reports {
        html.required.set(true)
    }

    // Increase heap size to prevent OOM during test execution with EhCache
    maxHeapSize = "2g"

    val jmockitPath = classpath.find { it.name.contains("jmockit") }!!.absolutePath
    jvmArgs("-javaagent:${jmockitPath}")

    // Cleanup test cache directories after tests complete
    doLast {
        try {
            val tmpDir = File(System.getProperty("java.io.tmpdir"))
            // Use walkTopDown with maxDepth to avoid loading all temp files into memory
            tmpDir.walkTopDown()
                .maxDepth(1)  // Only look at direct children, not subdirectories
                .drop(1)  // Skip the root temp directory itself (first element in the sequence)
                .filter { it.isDirectory && it.name.startsWith("bsl-ls-cache-") }
                .forEach { cacheDir ->
                    try {
                        cacheDir.deleteRecursively()
                        logger.info("Deleted test cache directory: ${cacheDir.name}")
                    } catch (e: Exception) {
                        logger.warn("Failed to delete test cache directory ${cacheDir.name}: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            // Don't fail the build if cleanup fails
            logger.warn("Failed to cleanup test cache directories: ${e.message}")
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
    mustRunAfter(tasks.generateDiagnosticDocs)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(File("${layout.buildDirectory.get()}/reports/jacoco/test/jacoco.xml"))
    }
}

jmh {
    jmhVersion = "1.37"
}

sentry {
    org.set("1c-syntax")
    projectName.set("bsl-language-server")
    
    // Включить source context только при наличии токена аутентификации
    includeSourceContext = System.getenv("SENTRY_AUTH_TOKEN") != null
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // native2ascii gradle replacement
    filesMatching("**/*.properties") {
        filter<EscapeUnicode>()
    }
}

tasks.classes {
    finalizedBy(tasks.generateDiagnosticDocs)
}

tasks.generateDiagnosticDocs {
    doLast {
        val resourcePath = tasks["processResources"].outputs.files.singleFile
        copy {
            from("${layout.buildDirectory.get()}/docs/diagnostics")
            into("$resourcePath/com/github/_1c_syntax/bsl/languageserver/diagnostics/ru")
        }

        copy {
            from("${layout.buildDirectory.get()}/docs/en/diagnostics")
            into("$resourcePath/com/github/_1c_syntax/bsl/languageserver/diagnostics/en")
        }
    }
}

tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        links(
            "https://1c-syntax.github.io/bsl-parser/dev/javadoc",
            "https://1c-syntax.github.io/mdclasses/dev/javadoc",
            "https://javadoc.io/doc/org.antlr/antlr4-runtime/latest"
        )
    }
}

spotless {
    java {
        targetExclude("**/AbstractObjectPool.java")
        licenseHeaderFile(rootProject.file("license/HEADER.txt"), "package ").updateYearWithLatest(true)
    }
}

sonarqube {
    properties {
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "1c-syntax")
        property("sonar.projectKey", "1c-syntax_bsl-language-server")
        property("sonar.projectName", "BSL Language Server")
        property("sonar.exclusions", "**/gen/**/*.*")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacoco.xml")
    }
}

artifacts {
    archives(tasks["jar"])
    archives(tasks["sourcesJar"])
    archives(tasks["bootJar"])
    archives(tasks["javadocJar"])
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks["bootJar"])

            pom {
                description.set("Language Server Protocol implementation for 1C (BSL) - 1C:Enterprise 8 and OneScript languages.")
                url.set("https://1c-syntax.github.io/bsl-language-server")
                licenses {
                    license {
                        name.set("GNU LGPL 3")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("asosnoviy")
                        name.set("Alexey Sosnoviy")
                        email.set("labotamy@gmail.com")
                        url.set("https://github.com/asosnoviy")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("nixel2007")
                        name.set("Nikita Fedkin")
                        email.set("nixel2007@gmail.com")
                        url.set("https://github.com/nixel2007")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("theshadowco")
                        name.set("Valery Maximov")
                        email.set("maximovvalery@gmail.com")
                        url.set("https://github.com/theshadowco")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("otymko")
                        name.set("Oleg Tymko")
                        email.set("olegtymko@yandex.ru")
                        url.set("https://github.com/otymko")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/1c-syntax/bsl-language-server.git")
                    developerConnection.set("scm:git:git@github.com:1c-syntax/bsl-language-server.git")
                    url.set("https://github.com/1c-syntax/bsl-language-server")
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/1c-syntax/bsl-language-server/issues")
                }
                ciManagement {
                    system.set("GitHub Actions")
                    url.set("https://github.com/1c-syntax/bsl-language-server/actions")
                }
            }
        }
    }
}

jreleaser {
    signing {
        active = ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("release-deploy") {
                    active = RELEASE
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
            nexus2 {
                create("snapshot-deploy") {
                    active = SNAPSHOT
                    snapshotUrl = "https://central.sonatype.com/repository/maven-snapshots/"
                    applyMavenCentralRules = true
                    snapshotSupported = true
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.register("updateLicenses") {
    description = "Wrapper for spotlessApply"
    group = "license"
    dependsOn(tasks.spotlessApply)
}

fun buildTime(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}
