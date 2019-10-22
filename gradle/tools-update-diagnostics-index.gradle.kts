import java.io.File

open class ToolsUpdateDiagnosticsIndex @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    private var pathPack = "com/github/_1c_syntax/bsl/languageserver/diagnostics";
    private var namePattern = Regex("^diagnosticName\\s*=\\s*(.*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var enabledPattern = Regex("^\\s*@DiagnosticMetadata\\([.\\s\\w\\W]*?\\s+activatedByDefault\\s*?=" +
            "\\s*?(true|false)\\s*?[.\\s\\w\\W]*?\\)\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));
    private var tagsBodyPattern = Regex("^\\s*@DiagnosticMetadata\\([.\\s\\w\\W]*?\\s+tags\\s*=" +
            "\\s*?\\{([.\\s\\w\\W]*?)\\s*\\}\\s*?[.\\s\\w\\W]*?\\)\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var tagsListPattern = Regex("DiagnosticTag\\.\\s*?([\\w]*)[,\\s]*",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var typePattern = Regex("^\\s*@DiagnosticMetadata\\([.\\s\\w\\W]*?\\s+type\\s*?=\\s" +
            "*?DiagnosticType\\.([\\w]*?)[\\s,\\)]\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));
    private var severityPattern = Regex("^\\s*@DiagnosticMetadata\\([.\\s\\w\\W]*?\\s+severity\\s*?=\\s" +
            "*?DiagnosticSeverity\\.([\\w]*?)[\\s,\\)]\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var typeRuMap = hashMapOf("ERROR" to "Ошибка",
            "VULNERABILITY" to "Уязвимость",
            "CODE_SMELL" to "Дефект кода");

    private var typeEnMap = hashMapOf("ERROR" to "Error",
            "VULNERABILITY" to "Vulnerability",
            "CODE_SMELL" to "Code smell");

    private var severityRuMap = hashMapOf("BLOCKER" to "Блокирующий",
            "CRITICAL" to "Критичный",
            "MAJOR" to "Важный",
            "MINOR" to "Незначительный",
            "INFO" to "Информационный");

    private var severityEnMap = hashMapOf("BLOCKER" to "Blocker",
            "CRITICAL" to "Critical",
            "MAJOR" to "Major",
            "MINOR" to "Minor",
            "INFO" to "Info");

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
            return "${readme.name}";
        }
        logger.quiet("File '{}' not exist", readme.path);
        return "";
    }

    private fun getTags(text: String): String {
        val matchBody = tagsBodyPattern.find(text);
        if(matchBody != null && matchBody.groups.isNotEmpty()) {
            val match = tagsListPattern.findAll(matchBody.groups[1]?.value?: "");
            return "`" + match.map {
                it.groupValues[1]
            }.joinToString("`<br/>`").toLowerCase() + "`";
        }
        return "";
    }

    private fun writeIndex(indexText: String, lang: String) {
        val indexPath = File(outputDir.get().asFile.path, "docs/${lang}diagnostics/index.md");
        val text = indexPath.readText(charset("UTF-8"));

        var header = "## Список реализованных диагностик";
        var table = "| Ключ | Название | Включена по умолчанию | Важность | Тип | Тэги |\n| --- | --- | :-: | --- | --- | --- |";
        if(lang != "") {
            header = "## Implemented diagnostics";
            table = "| Key | Name| Enabled by default | Severity | Type | Tags |\n| --- | --- | :-: | --- | --- | --- |";
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
                        var match = enabledPattern.find(text);
                        if(match != null && match.groups.isNotEmpty()) {
                            enabled = match.groups[1]?.value?.toBoolean() ?: true;
                        }
                        val enabledRu = if(enabled) "Да" else "Нет";
                        val enabledEn = if(enabled) "Yes" else "No";
                        val readmeRu = getReadme(key, "");
                        val readmeEn = getReadme(key, "en/");
                        val tags = getTags(text);
                        var typeRu= "";
                        var typeEn= "";
                        var severityRu = "";
                        var severityEn = "";
                        match = typePattern.find(text);
                        if(match != null && match.groups.isNotEmpty()) {
                            val type = match.groups[1]?.value.toString();
                            typeRu = typeRuMap.getOrDefault(type, "Ошибка");
                            typeEn = typeEnMap.getOrDefault(type, "Error");
                        }
                        match = severityPattern.find(text);
                        if(match != null && match.groups.isNotEmpty()) {
                            val severity = match.groups[1]?.value.toString();
                            severityRu = severityRuMap.getOrDefault(severity, "Блокирующий");
                            severityEn = severityEnMap.getOrDefault(severity, "Blocker");
                        }

                        indexRu += "\n| [${key}](${readmeRu}) | $nameRu | $enabledRu | $severityRu | $typeRu | $tags |";
                        indexEn += "\n| [${key}](${readmeEn}) | $nameEn | $enabledEn | $severityEn | $typeEn | $tags |";
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