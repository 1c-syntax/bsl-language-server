import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.CommitVersionDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.VersionDescription
import org.apache.tools.ant.filters.EscapeUnicode
import java.net.URI
import java.util.*

plugins {
    java
    maven
    `maven-publish`
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.sonarqube") version "2.8"
    id("io.franzbecker.gradle-lombok") version "3.3.0"
    id("me.qoomon.git-versioning") version "3.0.0"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
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

val jacksonVersion = "2.10.3"
val junitVersion = "5.6.1"

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.9.0")

    implementation("org.languagetool", "languagetool-core", "4.2")

    // https://mvnrepository.com/artifact/org.languagetool/language-en
    implementation("org.languagetool", "language-en", "4.2")

    // https://mvnrepository.com/artifact/org.languagetool/language-ru
    implementation("org.languagetool", "language-ru", "4.2")

    implementation("info.picocli", "picocli", "4.2.0")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io", "commons-io", "2.6")
    implementation("org.apache.commons", "commons-lang3", "3.10")
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    implementation("commons-beanutils", "commons-beanutils", "1.9.4")

    implementation("com.fasterxml.jackson.core", "jackson-databind", jacksonVersion)
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", jacksonVersion)
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", jacksonVersion)

    // https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
    implementation("com.google.code.findbugs", "jsr305", "3.0.2")

    implementation("me.tongfei", "progressbar", "0.8.1")

    implementation("org.slf4j", "slf4j-api", "1.8.0-beta4")
    implementation("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    implementation("org.reflections", "reflections", "0.9.10")

    implementation("com.github.1c-syntax", "bsl-parser", "01cc1512e10b667a0ec3fdd2307ca0221bf52684") {
        exclude("com.tunnelvisionlabs", "antlr4-annotations")
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.abego.treelayout", "org.abego.treelayout.core")
        exclude("org.antlr", "antlr-runtime")
        exclude("org.glassfish", "javax.json")
    }

    implementation("com.github.1c-syntax", "utils", "4034e83681b")
    implementation("com.github.1c-syntax", "mdclasses", "86be1579c4")

    compileOnly("org.projectlombok", "lombok", lombok.version)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    testImplementation("org.assertj", "assertj-core", "3.15.0")
    testImplementation("org.mockito", "mockito-core", "3.3.3")

    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
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

    enabled = false
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    project.configurations.implementation.get().isCanBeResolved = true
    configurations = listOf(project.configurations["implementation"])
    archiveClassifier.set("")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
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

license {
    header = rootProject.file("license/HEADER.txt")
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>"
    ext["project"] = "BSL Language Server"
    strictCheck = true
    mapping("java", "SLASHSTAR_STYLE")
    exclude("**/*.properties")
    exclude("**/*.xml")
    exclude("**/*.json")
    exclude("**/*.bsl")
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

// custom developers tools
apply(from = "gradle/tools-new-diagnostic.gradle.kts")
apply(from = "gradle/developer-tools.gradle.kts")

tasks.register("precommit") {
    description = "Run all precommit tasks"
    group = "Developer tools"
    dependsOn(":test")
    dependsOn(":licenseFormat")
    dependsOn(":updateDiagnosticDocs")
    dependsOn(":updateDiagnosticsIndex")
    dependsOn(":updateJsonSchema")
}

tasks {
    val delombok by registering(io.franzbecker.gradle.lombok.task.DelombokTask::class) {
        dependsOn(compileJava)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks["sourcesJar"])
            artifact(tasks["shadowJar"])
            artifact(tasks["javadocJar"])
//            artifact(tasks.shadowJar.get())
//            artifact(tasks.javadoc.get())
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
