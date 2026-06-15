import org.apache.tools.ant.filters.EscapeUnicode
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.jreleaser.model.Active.*
import java.text.SimpleDateFormat
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("com.diffplug.spotless") version "7.0.4"
    id("me.qoomon.git-versioning") version "6.4.4"
    id("io.freefair.lombok") version "9.5.0"
    id("io.freefair.javadoc-links") version "9.5.0"
    id("io.freefair.javadoc-utf-8") version "9.5.0"
    id("io.freefair.aspectj.post-compile-weaving") version "9.5.0"
     id("io.freefair.maven-central.validate-poms") version "9.5.0"
    id("com.github.ben-manes.versions") version "0.54.0"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.sentry.jvm.gradle") version "6.11.0"
    id("io.github.1c-syntax.bslls-dev-tools") version "0.8.1"
    id("ru.vyarus.pom") version "3.0.0"
    id("org.jreleaser") version "1.24.0"
    id("org.sonarqube") version "7.3.1.8318"
    id("me.champeau.jmh") version "0.7.3"
    id("com.gorylenko.gradle-git-properties") version "4.0.1"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://projectlombok.org/edge-releases")
    maven("https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://repo.spring.io/milestone")
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

val languageToolVersion = "6.8"

dependencies {

    // RUNTIME

    // spring
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-websocket")
    api("org.springframework.boot:spring-boot-starter-cache")

    api("io.micrometer:context-propagation")

    api("info.picocli:picocli-spring-boot-starter:4.7.7")

    // кэширование
    api("com.github.ben-manes.caffeine:caffeine:3.2.4")
    api("org.ehcache:ehcache:3.12.0")

    // lsp4j core
    api("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    api("org.eclipse.lsp4j:org.eclipse.lsp4j.websocket.jakarta:1.0.0")

    // Spring AI MCP (Model Context Protocol) server starters.
    // Spring AI 2.0 is the first line compatible with Spring Boot 4 (milestone at the time of writing).
    // - core starter: STDIO transport (`mcp` subcommand);
    // - webmvc starter: Streamable HTTP transport, served on the same servlet container as LSP-over-WS.
    api(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    api("org.springframework.ai:spring-ai-starter-mcp-server")
    api("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // 1c-syntax
    api("io.github.1c-syntax:bsl-parser:0.35.0")
    api("io.github.1c-syntax:utils:0.7.2")
    api("io.github.1c-syntax:mdclasses:0.19.1")
    api("io.github.1c-syntax:bsl-common-library:0.11.0")
    api("io.github.1c-syntax:supportconf:0.16.0")
    api("io.github.1c-syntax:bsl-context:0.7.0")

    // nullability annotations
    api("org.jspecify:jspecify:1.0.0")

    // JLanguageTool
    implementation("org.languagetool:languagetool-core:$languageToolVersion") {
        exclude("commons-logging", "commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }
    implementation("org.languagetool:language-en:$languageToolVersion") {
        exclude("commons-logging:commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }
    implementation("org.languagetool:language-ru:$languageToolVersion") {
        exclude("commons-logging", "commons-logging")
        exclude("com.sun.xml.bind", "jaxb-core")
        exclude("com.sun.xml.bind", "jaxb-impl")
    }

    // AOP
    implementation("org.aspectj:aspectjrt:1.9.25.1")

    // commons utils
    implementation("commons-io:commons-io:2.22.0")
    implementation("commons-beanutils:commons-beanutils:1.11.0") {
        exclude("commons-logging", "commons-logging")
    }
    implementation("commons-codec:commons-codec:1.22.0")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.apache.commons:commons-exec:1.6.0")

    // JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r")

    // progress bar
    implementation("me.tongfei:progressbar:0.10.2")

    // (de)serialization
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml")
    implementation("io.leangen.geantyref:geantyref:2.0.1")

    // graphs
    implementation("org.jgrapht:jgrapht-core:1.5.3")

    // SARIF serialization
    implementation("com.contrastsecurity:java-sarif:2.0")

    // CONSTRAINTS
    implementation("com.google.guava:guava") {
        version {
            strictly("33.6.0-jre")
        }
    }

    // COMPILE

    // TEST

    // spring
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }

    testImplementation("org.junit.jupiter:junit-jupiter")

    // test utils
    testImplementation("com.github.hazendaz.jmockit:jmockit:2.2.0")
    testImplementation("org.awaitility:awaitility:4.3.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("docker.io/1csyntax/bsl-language-server:${project.version}")
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

    tasks.named<Jar>("sourcesJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(tasks.generateGitProperties)
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

    // Increase heap size to prevent OOM during test execution.
    // With CleanupContextBeforeClassAndAfterClass tests causing frequent Spring context reloads,
    // multiple contexts can be in memory simultaneously (old being GC'd while new is created).
    // 3g gives enough headroom on GitHub Actions ubuntu-latest runners with 1 fork.
    maxHeapSize = "3g"

    // Параллельное выполнение тестов JUnit на уровне процессов (форков JVM).
    // Использование форков, а не потоков, обусловлено тем, что многие тесты
    // изменяют общее состояние Spring-контекста (@DirtiesContext,
    // @CleanupContextBeforeClassAndAfterClass) и статические кэши, поэтому
    // потоковая параллельность внутри одной JVM небезопасна. Каждый форк —
    // изолированная JVM со своим Spring-контекстом.
    //
    // Уровень параллелизма можно переопределить Gradle-свойством
    // `maxParallelForks` (например, `-PmaxParallelForks=4`). По умолчанию
    // на CI (env `CI=true` / `GITHUB_ACTIONS=true`) используется один форк,
    // чтобы не упереться в OOM при `maxHeapSize=3g` на каждый форк.
    // Локально — половина доступных процессоров, ограниченная диапазоном
    // от 1 до 4.
    val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    maxParallelForks = (project.findProperty("maxParallelForks") as String?)?.toIntOrNull()
        ?: if (isCi) 1 else (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    val jmockitPath = classpath.find { it.name.contains("jmockit") }!!.absolutePath
    val mockitoAgentPath = classpath.find { it.name.contains("mockito-core") }!!.absolutePath
    jvmArgs("-javaagent:${jmockitPath}", "-javaagent:${mockitoAgentPath}")

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
    // Версия antlr4 приходит транзитивно (через bsl-parser), поэтому берём её
    // из разрешённого runtimeClasspath, чтобы ссылка на javadoc.io указывала
    // ровно на используемую версию (иначе .../latest даёт redirect-warning).
    val antlr4Version = configurations.runtimeClasspath.get()
        .resolvedConfiguration.resolvedArtifacts
        .map { it.moduleVersion.id }
        .first { it.group == "io.github.1c-syntax" && it.name == "antlr4" }
        .version
    options {
        this as StandardJavadocDocletOptions
        links(
            "https://1c-syntax.github.io/bsl-parser/dev/javadoc",
            "https://1c-syntax.github.io/mdclasses/dev/javadoc",
            "https://javadoc.io/doc/io.github.1c-syntax/antlr4/$antlr4Version"
        )
        // Проверяем корректность javadoc (битые ссылки, синтаксис, html),
        // но не требуем наличия комментариев у каждого элемента (группа missing).
        addBooleanOption("Xdoclint:all,-missing", true)
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
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacoco.xml"
        )
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
