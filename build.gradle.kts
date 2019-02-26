
import org.apache.tools.ant.filters.EscapeUnicode
import java.net.URI
import java.util.*

plugins {
    java
    maven
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.sonarqube") version "2.7"
    id("io.franzbecker.gradle-lombok") version "2.1"
    id("com.github.gradle-git-version-calculator") version "1.1.0"
    id("com.github.ben-manes.versions") version "0.20.0"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

group = "org.github._1c_syntax"
version = gitVersionCalculator.calculateVersion("v")

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    compile("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.6.0")

    // https://mvnrepository.com/artifact/commons-cli/commons-cli
    compile("commons-cli", "commons-cli", "1.4")
    // https://mvnrepository.com/artifact/commons-io/commons-io
    compile("commons-io", "commons-io", "2.6")
    compile("org.apache.commons", "commons-lang3", "3.8.1")

    compile("com.fasterxml.jackson.core", "jackson-databind", "2.9.8")
    compile("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.9.8")
    compile("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.9.8")

    // https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
    compile("com.google.code.findbugs", "jsr305", "3.0.2")

    compile("me.tongfei", "progressbar", "0.7.2")

    compile("org.slf4j", "slf4j-api", "1.8.0-beta4")
    compile("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    compile("com.github.1c-syntax", "bsl-parser", "0.7.1")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.4.0")
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", "5.4.0")

    testCompile("org.assertj", "assertj-core", "3.12.0")

    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.github._1c_syntax.bsl.languageserver.BSLLSPLauncher"
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
        html.setEnabled(true)
    }
}

tasks.jacocoTestReport {
    reports {
        xml.setEnabled(true)
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
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
    }
}

lombok {
    version = "1.18.6"
    sha256 = "6373d9ade79efdc028cd48d40a9af9ac6a090dbcfaec55b438ec49556a4e92fb"
}
