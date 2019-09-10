

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

    compile("com.github.1c-syntax", "bsl-parser", "0.9.1")

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

open class ToolsNewDiagnostic @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @Option(option = "key", description = "Diagnostic key")
    private var key = "";

    @Option(option = "nameRu", description = "Diagnostic name in Russian")
    private var nameRu = "<Имя диагностики>";

    @Option(option = "nameEn", description = "Diagnostic name in English")
    private var nameEn = "<Diagnostic name>";

    fun setKey(key: String) {
        this.key = key;
    }
    fun getKey(): String {
        return key;
    }

    fun setNameRu(nameRu: String) {
        this.nameRu = nameRu;
    }
    fun getNameRu(): String {
        return nameRu;
    }

    fun setNameEn(nameEn: String) {
        this.nameEn = nameEn;
    }
    fun getNameEn(): String {
        return nameEn;
    }

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty();

    private fun createFile(path: String, text: String) {
        val f = File(path);
        f.writeText(text, charset("UTF-8"));
        logger.quiet("  Created file '{}'", f.absoluteFile);
    }

    @TaskAction
    fun createDiagnostic() {
        if(key.isEmpty()){
            throw Throwable("Empty diagnostic key")
        }
        logger.quiet("Creating new diagnostics files with the key '{}'", key);
        var srcPath = File(outputDir.get().asFile.path, "src");
        var packPath = "org/github/_1c_syntax/bsl/languageserver/diagnostics";
        var docPath = File(outputDir.get().asFile.path, "docs");
        createFile("${docPath}/diagnostics/${key}.md",
                "# ${nameRu}\n\n<Описание диагностики>\n\n## Параметры\n\n" +
                        "* `ИмяПараметра` - `ТипПараметра` - Описание параметра\n");
        createFile("${docPath}/en/diagnostics/${key}.md",
                "# ${nameEn}\n\n<Diagnostic description>\n\n## Params\n\n" +
                        "* `ParamName` - `ParamType` - Param description\n");

        createFile("${srcPath}/main/java/${packPath}/${key}Diagnostic.java",
                "package org.github._1c_syntax.bsl.languageserver.diagnostics;\n\n" +
                        "@DiagnosticMetadata(\n\ttype = DiagnosticType.CODE_SMELL," +
                        "\n\tseverity = DiagnosticSeverity.INFO,\n\tminutesToFix = 1\n)\n" +
                        "public class ${key}Diagnostic implements QuickFixProvider, BSLDiagnostic {\n}\n");

        createFile("${srcPath}/test/java/${packPath}/${key}DiagnosticTest.java",
                "package org.github._1c_syntax.bsl.languageserver.diagnostics;\n\n" +
                        "public class ${key}DiagnosticTest extends AbstractDiagnosticTest<${key}Diagnostic> {\n" +
                        "\t${key}DiagnosticTest() {\n\t\tsuper(${key}Diagnostic.class);\n\t}\n\n" +
                        "\t@Test\n\tvoid test() {\n\t}\n}\n");

        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_ru.properties",
                "diagnosticMessage=<Сообщение>\ndiagnosticName=${nameRu}\n");
        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_en.properties",
                "diagnosticMessage=<Message>\ndiagnosticName=${nameEn}\n");

        createFile("${srcPath}/test/resources/diagnostics/${key}Diagnostic.bsl", "\n");
    }
}

open class ToolsUpdateDiagnosticsIndex @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    private var pathPack = "org/github/_1c_syntax/bsl/languageserver/diagnostics";
    private var namePattern = Regex("^diagnosticName\\s*=\\s*(.*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var enabledPattern = Regex("^\\s*@DiagnosticMetadata\\([.\\s\\w\\W]*?\\s+activatedByDefault\\s*?=" +
            "\\s*?(true|false)\\s*?[.\\s\\w\\W]*?\\)\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty();

    private fun getName(key: String, lang: String): String {
        val fileP = File(outputDir.get().asFile.path,
                "src/main/resources/${pathPack}/${key}Diagnostic_${lang}.properties");
        if(fileP.exists()) {
            val match = namePattern.find(fileP.readText(charset("UTF-8")));
            if(match != null && match.groups.isNotEmpty()) {
                return match.groups[1]?.value.toString();
            }
        }
        logger.quiet("File '{}' not exist", fileP.path);
        return "";
    }

    private fun getReadme(key: String, lang: String): String {
        val docPath = File(outputDir.get().asFile.path, "docs/${lang}diagnostics");
        val readme = File(docPath.path, "${key}.md");
        if(readme.exists()) {
            return "${docPath.name}/${readme.name}";
        }
        logger.quiet("File '{}' not exist", readme.path);
        return "";
    }

    private fun writeIndex(indexText: String, lang: String) {
        val indexPath = File(outputDir.get().asFile.path, "docs/${lang}index.md");
        val text = indexPath.readText(charset("UTF-8"));

        var header = "### Список реализованных диагностик";
        var table = "| Ключ | Название | Включена по умолчанию |\n| --- | --- | :-: |";
        if(lang != "") {
            header = "### Implemented diagnostics";
            table = "| Key | Name| Enabled by default |\n| --- | --- | :-: |";
        }
        val indexHeader = text.indexOf(header);
        indexPath.writeText(text.substring(0, indexHeader - 1) + "\n${header}\n\n${table}${indexText}",
                charset("UTF-8"));
    }

    @TaskAction
    fun updateIndex() {
        logger.quiet("Update diagnostics index");
        var indexRu = "";
        var indexEn = "";
        File(outputDir.get().asFile.path, "src/main/java/${pathPack}")
                .walkBottomUp()
                .filter {
                    it.name.endsWith(".java")
                }.forEach {
                    val text = it.readText(charset("UTF-8"));
                    if(text.indexOf("@DiagnosticMetadata") == -1) {
                        logger.quiet("File skipped {}", it);
                    } else {
                        val key = it.name.substring(0, it.name.indexOf("Diagnostic"));
                        val nameRu = getName(key, "ru");
                        val nameEn = getName(key, "en");
                        var enabled = true;
                        val match = enabledPattern.find(text);
                        if(match != null && match.groups.isNotEmpty()) {
                            enabled = match.groups[1]?.value?.toBoolean() ?: true;
                        }
                        val enabledRu = if(enabled) "Да" else "Нет";
                        val enabledEn = if(enabled) "Yes" else "No";
                        val readmeRu = getReadme(key, "");
                        val readmeEn = getReadme(key, "en/");
                        indexRu += "\n| [${key}](${readmeRu}) | $nameRu | $enabledRu |";
                        indexEn += "\n| [${key}](${readmeEn}) | $nameEn | $enabledEn |";
                    }
                }
        writeIndex(indexRu, "");
        writeIndex(indexEn, "en/");
    }
}

tasks.register<ToolsNewDiagnostic>("newDiagnostic") {
    description = "Creating new diagnostics files";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};

tasks.register<ToolsUpdateDiagnosticsIndex>("updateDiagnosticsIndex") {
    description = "Updates diagnostics index after changes";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};