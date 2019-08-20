import org.apache.tools.ant.filters.EscapeUnicode
import java.net.URI
import java.util.*

plugins {
    java
    maven
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.sonarqube") version "2.7.1"
    id("io.franzbecker.gradle-lombok") version "3.1.0"
    id("com.github.gradle-git-version-calculator") version "1.1.0"
    id("com.github.ben-manes.versions") version "0.22.0"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

group = "org.github._1c_syntax"
version = gitVersionCalculator.calculateVersion("v")

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    compile("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.7.2")

    // https://mvnrepository.com/artifact/commons-cli/commons-cli
    compile("commons-cli", "commons-cli", "1.4")
    // https://mvnrepository.com/artifact/commons-io/commons-io
    compile("commons-io", "commons-io", "2.6")
    compile("org.apache.commons", "commons-lang3", "3.9")
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    compile("commons-beanutils", "commons-beanutils", "1.9.4")

    compile("com.fasterxml.jackson.core", "jackson-databind", "2.9.9")
    compile("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.9.9")
    compile("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.9.9")

    // https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
    compile("com.google.code.findbugs", "jsr305", "3.0.2")

    compile("me.tongfei", "progressbar", "0.7.4")

    compile("org.slf4j", "slf4j-api", "1.8.0-beta4")
    compile("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    compile("org.reflections", "reflections", "0.9.10")

    compile("com.github.1c-syntax", "bsl-parser", "0.9.0")

    compileOnly("org.projectlombok", "lombok", lombok.version)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.5.1")
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", "5.5.1")

    testCompile("org.assertj", "assertj-core", "3.13.2")

    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
    from("docs/diagnostics") {
        into("org/github/_1c_syntax/bsl/languageserver/diagnostics")
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
    }
}

lombok {
    version = "1.18.8"
    sha256 = "0396952823579b316a0fe85cbd871bbb3508143c2bcbd985dd7800e806cb24fc"
}
