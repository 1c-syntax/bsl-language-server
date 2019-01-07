import java.net.URI
import java.util.Calendar
import org.apache.tools.ant.filters.EscapeUnicode

plugins {
    java
    maven
    jacoco
    id("com.github.hierynomus.license") version "0.14.0"
    id("org.sonarqube") version "2.6.2"
}

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

group = "org.github._1c_syntax"
version = "1.0"

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j
    compile("org.eclipse.lsp4j:org.eclipse.lsp4j:0.4.1")

    // https://mvnrepository.com/artifact/commons-cli/commons-cli
    compile("commons-cli:commons-cli:1.4")

    compile("commons-io", "commons-io", "2.6")

    compile("com.fasterxml.jackson.core", "jackson-databind", "2.9.4")

    compile("com.github.1c-syntax", "bsl-parser", "0.1.0")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    testImplementation("commons-io:commons-io:2.6")

    testImplementation("org.hamcrest:hamcrest-library:1.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.2.0")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.2.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.github._1c_syntax.intellij.bsl.lsp.server.BSLLSPLauncher"
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

// native2ascii gradle replacement
tasks.withType<ProcessResources>().forEach { task ->
    task.from(task.getSource()) {
        include("**/*.properties")
        filter<EscapeUnicode>()
    }
}

license {
    header = rootProject.file("license/HEADER.txt")
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>"
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
