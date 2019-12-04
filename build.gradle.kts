
import me.qoomon.gradle.gitversioning.GitVersioningPluginExtension.CommitVersionDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginExtension.VersionDescription
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
    id("io.franzbecker.gradle-lombok") version "3.2.0"
    id("me.qoomon.git-versioning") version "1.4.0"
    id("com.github.ben-manes.versions") version "0.25.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/1c-syntax/bsl-language-server")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_DEPLOY_KEY")
            }
        }
    }
    publications {
        register("gpr", MavenPublication::class) {
            from(components["java"])
        }
    }
}

group = "com.github.1c-syntax"

gitVersioning {
    preferTags = true
    branch(closureOf<VersionDescription> {
        pattern = "^(?!v[0-9]+).*"
        versionFormat = "\${branch}-\${commit.short}"
    })
    tag(closureOf<VersionDescription>{
        pattern = "v(?<tagVersion>[0-9].*)"
        versionFormat = "\${tagVersion}"
    })
    commit(closureOf<CommitVersionDescription>{
        versionFormat = "\${commit.short}"
    })
}

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.8.1")

    // https://mvnrepository.com/artifact/commons-cli/commons-cli
    implementation("commons-cli", "commons-cli", "1.4")
    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io", "commons-io", "2.6")
    implementation("org.apache.commons", "commons-lang3", "3.9")
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    implementation("commons-beanutils", "commons-beanutils", "1.9.4")

    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.10.0")
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.10.0")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.10.0")

    // https://mvnrepository.com/artifact/javax.xml.bind/jaxb-api
    implementation("javax.xml.bind", "jaxb-api", "2.3.1")

    // https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
    implementation("com.google.code.findbugs", "jsr305", "3.0.2")

    // https://github.com/1c-syntax/bsl-language-server/issues/369
    // Excude jline and use fixed one.
    implementation("me.tongfei", "progressbar", "0.7.4") { exclude(group = "org.jline") }
    implementation("org.jline", "jline", "3.13.1")

    implementation("org.slf4j", "slf4j-api", "1.8.0-beta4")
    implementation("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    implementation("org.reflections", "reflections", "0.9.10")

    implementation("com.github.1c-syntax", "bsl-parser", "0.11.0") {
        exclude("com.github.nixel2007.antlr4", "antlr4-maven-plugin")
        exclude("com.github.nixel2007.antlr4", "antlr4-runtime-test-annotations")
        exclude("com.github.nixel2007.antlr4", "antlr4-runtime-test-annotation-processors")
        exclude("com.github.nixel2007.antlr4", "antlr4-runtime-testsuite")
        exclude("com.github.nixel2007.antlr4", "antlr4-tool-testsuite")
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.abego.treelayout", "org.abego.treelayout.core")
        exclude("org.antlr", "antlr-runtime")
        exclude("org.glassfish", "javax.json")
    }

    implementation("com.github.1c-syntax:mdclasses:2ba0a98a43cc11d7775c5169ce0e921c0d921386")

    compileOnly("org.projectlombok", "lombok", lombok.version)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.5.2")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.5.2")

    testImplementation("org.assertj", "assertj-core", "3.13.2")

    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher"
//        attributes["Implementation-Version"] = archiveVersion.get()
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
}

// native2ascii gradle replacement
tasks.withType<ProcessResources>().forEach { task ->
    task.from(task.source) {
        include("**/*.properties")
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
    version = "1.18.10"
    sha256 = "2836e954823bfcbad45e78c18896e3d01058e6f643749810c608b7005ee7b2fa"
}

// custom developers tools
apply(from = "gradle/tools-new-diagnostic.gradle.kts")
apply(from = "gradle/tools-update-diagnostics-index.gradle.kts")
apply(from = "gradle/tools-update-diagnostics-doc.gradle.kts")
apply(from = "gradle/tools-update-json-schema.gradle.kts")

tasks.register("precommit") {
    description = "Run all precommit tasks"
    group = "Developer tools"
    dependsOn(":test")
    dependsOn(":licenseFormat")
    dependsOn(":updateDiagnosticDocs")
    dependsOn(":updateDiagnosticsIndex")
    dependsOn(":updateJsonSchema")
}
