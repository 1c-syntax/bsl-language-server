import gradlegitproperties.org.ajoberstar.grgit.Grgit
import me.qoomon.gitversioning.commons.GitRefType
import org.apache.tools.ant.filters.EscapeUnicode
import java.util.*
import java.text.SimpleDateFormat

plugins {
    `java-library`
    `maven-publish`
    jacoco
    signing
    id("org.cadixdev.licenser") version "0.6.1"
    id("org.sonarqube") version "5.1.0.4882"
    id("io.freefair.lombok") version "8.7.1"
    id("io.freefair.javadoc-links") version "8.7.1"
    id("io.freefair.javadoc-utf-8") version "8.7.1"
    id("io.freefair.aspectj.post-compile-weaving") version "8.7.1"
    id("io.freefair.maven-central.validate-poms") version "8.7.1"
    id("me.qoomon.git-versioning") version "6.4.3"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.github.1c-syntax.bslls-dev-tools") version "0.8.1"
    id("ru.vyarus.pom") version "3.0.0"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    id("io.codearte.nexus-staging") version "0.30.0"
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven(url = "https://projectlombok.org/edge-releases")
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots")
}

group = "io.github.1c-syntax"

gitVersioning.apply {
    refs {
        considerTagsOnBranches = true
        tag("v(?<tagVersion>[0-9].*)") {
            version = "\${ref.tagVersion}\${dirty}"
        }
        branch(".+") {
            version = "\${ref}-\${commit.short}\${dirty}"
        }
    }

    rev {
        version = "\${commit.short}\${dirty}"
    }
}

gitProperties {
    customProperty("git.build.time", buildTime())
}

val isSnapshot = gitVersioning.gitVersionDetails.refType != GitRefType.TAG

val languageToolVersion = "6.4"

dependencyManagement {
    imports {
        mavenBom("io.sentry:sentry-bom:7.12.1")
    }
}

dependencies {

    // RUNTIME

    // spring
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-websocket")
    api("info.picocli:picocli-spring-boot-starter:4.7.6")

    // lsp4j core
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.23.1")
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j.websocket.jakarta", "0.23.1")

    // 1c-syntax
    api("io.github.1c-syntax", "bsl-parser", "0.24.0") {
        exclude("com.tunnelvisionlabs", "antlr4-annotations")
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.abego.treelayout", "org.abego.treelayout.core")
        exclude("org.antlr", "antlr-runtime")
    }
    api("io.github.1c-syntax", "utils", "0.6.1")
    api("io.github.1c-syntax", "mdclasses", "0.14.0")
    api("io.github.1c-syntax", "bsl-common-library", "0.7.0")
    api("io.github.1c-syntax", "supportconf", "0.14.0") {
        exclude("io.github.1c-syntax", "bsl-common-library")
    }
    api("io.github.1c-syntax", "bsl-parser-core", "0.1.0")

    // JLanguageTool
    implementation("org.languagetool", "languagetool-core", languageToolVersion){
        exclude("commons-logging", "commons-logging")
    }
    implementation("org.languagetool", "language-en", languageToolVersion)
    implementation("org.languagetool", "language-ru", languageToolVersion)

    // AOP
    implementation("org.aspectj", "aspectjrt", "1.9.22.1")

    // commons utils
    implementation("commons-io", "commons-io", "2.16.1")
    implementation("org.apache.commons", "commons-lang3", "3.14.0")
    implementation("commons-beanutils", "commons-beanutils", "1.9.4"){
        exclude("commons-logging", "commons-logging")
    }
    implementation("org.apache.commons", "commons-collections4", "4.4")
    implementation("org.apache.commons", "commons-exec", "1.4.0")

    // progress bar
    implementation("me.tongfei", "progressbar", "0.10.1")

    // (de)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    // graphs
    implementation("org.jgrapht", "jgrapht-core", "1.5.2")

    // SARIF serialization
    implementation("com.contrastsecurity", "java-sarif", "2.0")

    // Sentry
    implementation("io.sentry:sentry-spring-boot-starter-jakarta")
    implementation("io.sentry:sentry-logback")

    // CONSTRAINTS
    implementation("com.google.guava:guava") {
        version {
            strictly("33.2.1-jre")
       }
    }
    
    // COMPILE
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")

    // TEST

    // spring
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }

    // test utils
    testImplementation("org.jmockit", "jmockit", "1.49")
    testImplementation("org.awaitility", "awaitility", "4.2.1")
}

lombok {
    version.set("1.18.32")
}

jacoco {
    toolVersion = "0.8.10"
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

    val jmockitPath = classpath.find { it.name.contains("jmockit") }!!.absolutePath
    jvmArgs("-javaagent:${jmockitPath}")
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
    mustRunAfter(tasks.generateDiagnosticDocs)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(File("$buildDir/reports/jacoco/test/jacoco.xml"))
    }
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
            from("$buildDir/docs/diagnostics")
            into("$resourcePath/com/github/_1c_syntax/bsl/languageserver/diagnostics/ru")
        }

        copy {
            from("$buildDir/docs/en/diagnostics")
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

license {
    header(rootProject.file("license/HEADER.txt"))
    newLine(false)
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com>"
    ext["project"] = "BSL Language Server"
    exclude("**/*.properties")
    exclude("**/*.xml")
    exclude("**/*.json")
    exclude("**/*.bsl")
    exclude("**/*.os")
    exclude("**/*.txt")
    exclude("**/*.java.orig")
    exclude("**/*.impl")
    exclude("**/*.mockito.plugins.MockMaker")
}

sonarqube {
    properties {
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "1c-syntax")
        property("sonar.projectKey", "1c-syntax_bsl-language-server")
        property("sonar.projectName", "BSL Language Server")
        property("sonar.exclusions", "**/gen/**/*.*")
        property("sonar.coverage.jacoco.xmlReportPaths", "$buildDir/reports/jacoco/test/jacoco.xml")
    }
}

artifacts {
    archives(tasks["jar"])
    archives(tasks["sourcesJar"])
    archives(tasks["bootJar"])
    archives(tasks["javadocJar"])
}

signing {
    val signingInMemoryKey: String? by project      // env.ORG_GRADLE_PROJECT_signingInMemoryKey
    val signingInMemoryPassword: String? by project // env.ORG_GRADLE_PROJECT_signingInMemoryPassword
    if (signingInMemoryKey != null) {
        useInMemoryPgpKeys(signingInMemoryKey, signingInMemoryPassword)
        sign(publishing.publications)
    }
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = if (isSnapshot)
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

            val sonatypeUsername: String? by project
            val sonatypePassword: String? by project

            credentials {
                username = sonatypeUsername // ORG_GRADLE_PROJECT_sonatypeUsername
                password = sonatypePassword // ORG_GRADLE_PROJECT_sonatypePassword
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks["bootJar"])

            if (isSnapshot && project.hasProperty("simplifyVersion")) {
                version = findProperty("git.ref.slug") as String + "-SNAPSHOT"
            }

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
            }
        }
    }
}

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    stagingProfileId = "15bd88b4d17915" // ./gradlew getStagingProfile
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

fun buildTime(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}
