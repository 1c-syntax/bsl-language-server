open class ToolsNewDiagnostic @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @Option(option = "key", description = "Diagnostic key (required)")
    private var key = "";

    @Option(option = "nameRu", description = "Diagnostic name in Russian (optional)")
    private var nameRu = "<Имя диагностики>";

    @Option(option = "nameEn", description = "Diagnostic name in English (optional)")
    private var nameEn = "<Diagnostic name>";

    fun setKey(key: String) {
        this.key = key;
    }

    fun setNameRu(nameRu: String) {
        this.nameRu = nameRu;
    }

    fun setNameEn(nameEn: String) {
        this.nameEn = nameEn;
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
        val srcPath = File(outputDir.get().asFile.path, "src");
        val packPath = "com/github/_1c_syntax/bsl/languageserver/diagnostics";
        val docPath = File(outputDir.get().asFile.path, "docs");
        createFile("${docPath}/diagnostics/${key}.md",
                "# ${nameRu}\n\n<Описание диагностики>\n\n## Параметры\n\n" +
                        "* `ИмяПараметра` - `ТипПараметра` - Описание параметра\n");
        createFile("${docPath}/en/diagnostics/${key}.md",
                "# ${nameEn}\n\n<Diagnostic description>\n\n## Params\n\n" +
                        "* `ParamName` - `ParamType` - Param description\n");

        createFile("${srcPath}/main/java/${packPath}/${key}Diagnostic.java",
                "package com.github._1c_syntax.bsl.languageserver.diagnostics;\n\n" +
                        "@DiagnosticMetadata(\n\ttype = DiagnosticType.CODE_SMELL," +
                        "\n\tseverity = DiagnosticSeverity.INFO,\n\tminutesToFix = 1\n)\n" +
                        "public class ${key}Diagnostic implements QuickFixProvider, BSLDiagnostic {\n}\n");

        createFile("${srcPath}/test/java/${packPath}/${key}DiagnosticTest.java",
                "package com.github._1c_syntax.bsl.languageserver.diagnostics;\n\n" +
                        "class ${key}DiagnosticTest extends AbstractDiagnosticTest<${key}Diagnostic> {\n" +
                        "\t${key}DiagnosticTest() {\n\t\tsuper(${key}Diagnostic.class);\n\t}\n\n" +
                        "\t@Test\n\tvoid test() {\n\t}\n}\n");

        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_ru.properties",
                "diagnosticMessage=<Сообщение>\ndiagnosticName=${nameRu}\n");
        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_en.properties",
                "diagnosticMessage=<Message>\ndiagnosticName=${nameEn}\n");

        createFile("${srcPath}/test/resources/diagnostics/${key}Diagnostic.bsl", "\n");
    }
}

tasks.register<ToolsNewDiagnostic>("newDiagnostic") {
    description = "Creating new diagnostics files";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};