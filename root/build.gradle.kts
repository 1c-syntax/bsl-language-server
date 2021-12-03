plugins {
    `java-library`
    id("org.cadixdev.licenser")
    id("org.sonarqube")
    id("io.freefair.lombok")
    id("io.freefair.aspectj.post-compile-weaving")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.github.1c-syntax.bslls-dev-tools")
    id("ru.vyarus.pom")
    id("de.jjohannes.extra-java-module-info")
}

extraJavaModuleInfo {
    failOnMissingModuleInfo.set(false)
    automaticModule("org.eclipse.lsp4j-0.12.0.jar", "org.eclipse.lsp4j")
    automaticModule("bsl-parser-b0858f1e02.jar", "v8.bsl.parser")
    automaticModule("mdclasses-0.9.2.jar", "v8.bsl.mdclasses")
    automaticModule("antlr4-runtime-4.9.0.jar", "antlr4.runtime")
    automaticModule("org.eclipse.lsp4j.jsonrpc-0.12.0.jar", "org.eclipse.lsp4j.jsonrpc")
    automaticModule("utils-0.3.4.jar", "v8.bsl.utils")
    automaticModule("commons-beanutils-1.9.4.jar", "commons.beanutils")
    automaticModule("progressbar-0.9.2.jar", "progressbar")
    automaticModule("java-sarif-2.0.jar", "java.sarif")
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("--add-reads", "bsl.language.server=ALL-UNNAMED"))
}

group = "io.github.1c-syntax"
version = "jigsaw-10c10c2-DIRTY"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

val languageToolVersion = "5.5"

dependencies {
    // RUNTIME

    // spring
    api("org.springframework.boot:spring-boot-starter")
    api("info.picocli:picocli-spring-boot-starter:4.6.2")

    // lsp4j core
    api("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.12.0") {
        exclude("com.google.code.findbugs", "jsr305")
    }

    // 1c-syntax
    api("com.github.1c-syntax", "bsl-parser", "b0858f1e02") {
        exclude("com.tunnelvisionlabs", "antlr4-annotations")
        exclude("com.ibm.icu", "*")
        exclude("org.antlr", "ST4")
        exclude("org.abego.treelayout", "org.abego.treelayout.core")
        exclude("org.antlr", "antlr-runtime")
        exclude("org.glassfish", "javax.json")
    }
    api("com.github.1c-syntax", "utils", "0.3.4")
    api("com.github.1c-syntax", "mdclasses", "0.9.2")

    // JLanguageTool
    implementation("org.languagetool", "languagetool-core", languageToolVersion)
    implementation("org.languagetool", "language-en", languageToolVersion)
    implementation("org.languagetool", "language-ru", languageToolVersion)

    // AOP
    implementation("org.aspectj", "aspectjrt", "1.9.7")

    // commons utils
    implementation("commons-io", "commons-io", "2.11.0")
    implementation("org.apache.commons", "commons-lang3", "3.12.0")
    implementation("commons-beanutils", "commons-beanutils", "1.9.4")
    implementation("org.apache.commons", "commons-collections4", "4.4")

    // progress bar
    implementation("me.tongfei", "progressbar", "0.9.2")

    // (de)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    // graphs
    implementation("org.jgrapht", "jgrapht-core", "1.5.1")

    // SARIF serialization
    implementation("com.contrastsecurity", "java-sarif", "2.0")

    // COMPILE

    // stat analysis
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.5.0") {
        exclude("com.google.code.findbugs", "jsr305")
    }


    // TEST

    // spring
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }

    // test utils
    testImplementation("com.ginsberg", "junit5-system-exit", "1.1.1")
    testImplementation("org.awaitility", "awaitility", "4.1.1")
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
        filter<org.apache.tools.ant.filters.EscapeUnicode>()
    }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}