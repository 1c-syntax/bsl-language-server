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

    private var tagPattern = Regex("([\\w]+?)\\(\"([\\W\\w]+?)\"\\)[,\\s]*",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

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
        val fileP = File(outputDir.get().asFile.path,
                "src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics/metadata/DiagnosticTag.java");

        if(fileP.exists()) {
            val match = tagPattern.findAll(fileP.readText(charset("UTF-8")));
            println("Diagnostic tags: \n" + match.map {
                it.groupValues[1]
            }.joinToString("\n")
            );
        }

        println("Enter diagnostic tags separated by space (max 3).")
        val inputStr = readLine();
        if(inputStr.isNullOrBlank()) {
            throw Throwable("Empty diagnostic tags")
        }
        var diagnosticTags = inputStr.split(' ').joinToString(",\n    DiagnosticTag.");
        diagnosticTags = "tags = {\n    DiagnosticTag.$diagnosticTags\n  }\n";

        logger.quiet("Creating new diagnostics files with the key '{}'", key);
        val srcPath = File(outputDir.get().asFile.path, "src");
        val packPath = "com/github/_1c_syntax/bsl/languageserver/diagnostics";
        val docPath = File(outputDir.get().asFile.path, "docs");
        val templatePath = File(outputDir.get().asFile.path, "gradle/NewDiagnosticTemplates");
        createFile("${docPath}/diagnostics/${key}.md",
                (File(templatePath, "Template_ru.md"))
                        .readText(charset("UTF-8")).replace("<Имя диагностики>", nameRu));

        createFile("${docPath}/en/diagnostics/${key}.md",
                (File(templatePath, "Template_en.md"))
                        .readText(charset("UTF-8")).replace("<Diagnostic name>", nameEn));

        createFile("${srcPath}/main/java/${packPath}/${key}Diagnostic.java",
                (File(templatePath, "TemplateDiagnostic.java"))
                        .readText(charset("UTF-8"))
                        .replace("Template", key)
                        .replace("\$diagnosticTags", diagnosticTags));

        createFile("${srcPath}/test/java/${packPath}/${key}DiagnosticTest.java",
                (File(templatePath, "TemplateDiagnosticTest.java"))
                        .readText(charset("UTF-8")).replace("Template", key));

        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_ru.properties",
                (File(templatePath, "TemplateDiagnostic_ru.properties"))
                        .readText(charset("UTF-8")).replace("<Имя диагностики>", nameRu));

        createFile("${srcPath}/main/resources/${packPath}/${key}Diagnostic_en.properties",
                (File(templatePath, "TemplateDiagnostic_en.properties"))
                        .readText(charset("UTF-8")).replace("<Diagnostic name>", nameEn));

        createFile("${srcPath}/test/resources/diagnostics/${key}Diagnostic.bsl", "\n");
    }
}

tasks.register<ToolsNewDiagnostic>("newDiagnostic") {
    description = "Creating new diagnostics files";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};
