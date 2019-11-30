import org.apache.tools.ant.filters.EscapeUnicode
import java.net.URI
import java.util.*

plugins {
    java
    maven
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.sonarqube") version "2.8"
    id("io.franzbecker.gradle-lombok") version "3.2.0"
    id("com.github.gradle-git-version-calculator") version "1.1.0"
    id("com.github.ben-manes.versions") version "0.25.0"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

group = "com.github.1c-syntax"
version = gitVersionCalculator.calculateVersion("v")

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    compile("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.8.1")

    // https://mvnrepository.com/artifact/commons-cli/commons-cli
    compile("commons-cli", "commons-cli", "1.4")
    // https://mvnrepository.com/artifact/commons-io/commons-io
    compile("commons-io", "commons-io", "2.6")
    compile("org.apache.commons", "commons-lang3", "3.9")
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    compile("commons-beanutils", "commons-beanutils", "1.9.4")

    compile("com.fasterxml.jackson.core", "jackson-databind", "2.10.0")
    compile("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.10.0")
    compile("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.10.0")

    // https://mvnrepository.com/artifact/javax.xml.bind/jaxb-api
    compile("javax.xml.bind", "jaxb-api", "2.3.1")


    // https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
    compile("com.google.code.findbugs", "jsr305", "3.0.2")

    // https://github.com/1c-syntax/bsl-language-server/issues/369
    // Excude jline and use fixed one.
    compile("me.tongfei", "progressbar", "0.7.4") { exclude(group = "org.jline") }
    compile("org.jline", "jline", "3.13.1")

    compile("org.slf4j", "slf4j-api", "1.8.0-beta4")
    compile("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    compile("org.reflections", "reflections", "0.9.10")

    compile("com.github.1c-syntax", "bsl-parser", "0.11.0") {
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

    compile("com.github.1c-syntax:mdclasses:9df30a46ff35f99d8478e0634198e203badfbcfd")

    compileOnly("org.projectlombok", "lombok", lombok.version)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.5.2")
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", "5.5.2")

    testCompile("org.assertj", "assertj-core", "3.13.2")

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

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher"
        attributes["Implementation-Version"] = archiveVersion.get()
    }
    configurations["compile"].forEach {
        from(zipTree(it.absoluteFile)) {
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
    }
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
