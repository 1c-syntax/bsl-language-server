import me.qoomon.gitversioning.commons.GitRefType
import org.apache.tools.ant.filters.EscapeUnicode
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    signing
    id("org.cadixdev.licenser") version "0.6.1"
    id("org.sonarqube") version "3.3"
    id("io.freefair.lombok") version "6.3.0"
    id("io.freefair.javadoc-links") version "6.3.0"
    id("io.freefair.javadoc-utf-8") version "6.3.0"
    id("io.freefair.aspectj.post-compile-weaving") version "6.3.0"
    id("io.freefair.maven-central.validate-poms") version "6.3.0"
    id("me.qoomon.git-versioning") version "5.1.1"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("org.springframework.boot") version "2.6.1"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("com.github.1c-syntax.bslls-dev-tools") version "0.5.0"
    id("ru.vyarus.pom") version "2.2.1"
    id("io.codearte.nexus-staging") version "0.30.0"
    id("de.jjohannes.extra-java-module-info") version "0.9"

}

extraJavaModuleInfo {
    failOnMissingModuleInfo.set(false)
    automaticModule("jsr305-3.0.2.jar", "jsr305")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
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

val isSnapshot = gitVersioning.gitVersionDetails.refType != GitRefType.TAG

val languageToolVersion = "5.5"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
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
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(File("$buildDir/reports/jacoco/test/jacoco.xml"))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    from("docs/diagnostics") {
        into("com/github/_1c_syntax/bsl/languageserver/diagnostics/ru")
    }

    from("docs/en/diagnostics") {
        into("com/github/_1c_syntax/bsl/languageserver/diagnostics/en")
    }

    // native2ascii gradle replacement
    filesMatching("**/*.properties") {
        filter<EscapeUnicode>()
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
