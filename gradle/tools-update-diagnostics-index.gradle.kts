import java.io.File

open class ToolsUpdateDiagnosticsIndex @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    private var pathPack = "com/github/_1c_syntax/bsl/languageserver/diagnostics";
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

tasks.register<ToolsUpdateDiagnosticsIndex>("updateDiagnosticsIndex") {
    description = "Updates diagnostics index after changes";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};