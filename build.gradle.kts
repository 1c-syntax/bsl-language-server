import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.CommitVersionDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.VersionDescription
import org.apache.tools.ant.filters.EscapeUnicode
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("net.kyori.indra.license-header") version "1.0.2"
    id("org.sonarqube") version "3.0"
    id("io.franzbecker.gradle-lombok") version "4.0.0"
    id("me.qoomon.git-versioning") version "3.0.0"
    id("com.github.ben-manes.versions") version "0.31.0"
    id("io.freefair.javadoc-links") version "5.2.1"
    id("org.springframework.boot") version "2.3.3.RELEASE"
    id("com.github.1c-syntax.bslls-dev-tools") version "0.3.1"
}

apply(plugin = "io.spring.dependency-management")

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

group = "com.github.1c-syntax"

gitVersioning.apply(closureOf<GitVersioningPluginConfig> {
    preferTags = true
    branch(closureOf<VersionDescription> {
        pattern = "^(?!v[0-9]+).*"
        versionFormat = "\${branch}-\${commit.short}\${dirty}"
    })
    tag(closureOf<VersionDescription> {
        pattern = "v(?<tagVersion>[0-9].*)"
        versionFormat = "\${tagVersion}\${dirty}"
    })
    commit(closureOf<CommitVersionDescription> {
        versionFormat = "\${commit.short}\${dirty}"
    })
})

val jacksonVersion = "2.11.2"
val junitVersion = "5.6.1"
val languageToolVersion = "5.0"

dependencies {

    // RUNTIME

    // spring
    api("org.springframework.boot:spring-boot-starter")
    api("info.picocli:picocli-spring-boot-starter:4.5.1")

    // lsp4j core
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.9.0")

    // 1c-syntax
    api("com.github.1c-syntax", "bsl-parser", "0.16.0") {
        exclude("com.tunnelvisionlabs", "antlr4-annotations")
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.abego.treelayout", "org.abego.treelayout.core")
        exclude("org.antlr", "antlr-runtime")
        exclude("org.glassfish", "javax.json")
    }
    api("com.github.1c-syntax", "utils", "0.3.1")
    api("com.github.1c-syntax", "mdclasses", "0.6.1")

    // JLanguageTool
    implementation("org.languagetool", "languagetool-core", "5.0.2")
    implementation("org.languagetool", "language-en", languageToolVersion)
    implementation("org.languagetool", "language-ru", languageToolVersion)

    // commons utils
    implementation("commons-io", "commons-io", "2.8.0")
    implementation("org.apache.commons", "commons-lang3", "3.11")
    implementation("commons-beanutils", "commons-beanutils", "1.9.4")
    implementation("org.apache.commons", "commons-collections4", "4.4")

    // progress bar
    implementation("me.tongfei", "progressbar", "0.8.1")

    // (de)serialization
    implementation("com.fasterxml.jackson.core", "jackson-databind", jacksonVersion)
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", jacksonVersion)
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", jacksonVersion)

    // stat analysis
    implementation("com.google.code.findbugs", "jsr305", "3.0.2")

    // COMPILE

    compileOnly("org.projectlombok", "lombok", lombok.version)

    // TEST

    // junit
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    // spring
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // test utils
    testImplementation("org.assertj", "assertj-core", "3.17.2")
    testImplementation("org.mockito", "mockito-core", "3.5.10")
    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
    testImplementation("org.awaitility", "awaitility", "4.0.3")
}

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
        html.isEnabled = true
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        xml.destination = File("$buildDir/reports/jacoco/test/jacoco.xml")
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

jacoco {
    toolVersion = "0.8.6"
}

license {
    header = rootProject.file("license/HEADER.txt")
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>"
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

lombok {
    version = "1.18.12"
    sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406"
}

tasks {
    val delombok by registering(JavaExec::class) {
        dependsOn(compileJava)

        main = project.extensions.findByType(io.franzbecker.gradle.lombok.LombokPluginExtension::class)!!.main
        args = listOf("delombok")
        classpath = project.configurations.getByName("compileClasspath")

        jvmArgs = listOf("-Dfile.encoding=UTF-8")
        val outputDir by extra { file("$buildDir/delombok") }
        outputs.dir(outputDir)
        sourceSets["main"].java.srcDirs.forEach {
            inputs.dir(it)
            args(it, "-d", outputDir)
        }
        doFirst {
            outputDir.delete()
        }
    }

    javadoc {
        dependsOn(delombok)
        val outputDir: File by delombok.get().extra
        source = fileTree(outputDir)
        isFailOnError = false
        options.encoding = "UTF-8"
    }
}

artifacts {
    archives(tasks["sourcesJar"])
    archives(tasks["bootJar"])
    archives(tasks["javadocJar"])
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks["sourcesJar"])
            artifact(tasks["bootJar"])
            artifact(tasks["javadocJar"])
            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")

                configurations.implementation.get().dependencies.forEach { dependency ->
                    if (dependency !is SelfResolvingDependency) {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dependency.group)
                        dependencyNode.appendNode("artifactId", dependency.name)
                        dependencyNode.appendNode("version", dependency.version)
                        dependencyNode.appendNode("scope", "runtime")
                    }
                }
            }
        }
    }
}
