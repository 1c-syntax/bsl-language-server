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

    private var typeMapRu = hashMapOf<String, Int>()
    private var typeMapEn = hashMapOf<String, Int>()

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

    private var defaultValues = hashMapOf<String, String>()

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty();

    private fun loadDefaultValues() {
        defaultValues.clear()
        val fileP = File(outputDir.get().asFile.path,
                "src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics/metadata/DiagnosticMetadata.java");
        val text = getValueFromText(fileP.readText(charset("UTF-8")), Regex("DiagnosticMetadata\\s*?\\{([\\w\\s\\W]+)\\}",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["type"] = getValueFromText(text, Regex("DiagnosticType\\s+?type\\(\\)\\s+?default\\s+?DiagnosticType\\.(\\w+)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["severity"] = getValueFromText(text, Regex("DiagnosticSeverity\\s+?severity\\(\\)\\s+?default\\s+?DiagnosticSeverity\\.(\\w+)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["scope"] = getValueFromText(text, Regex("DiagnosticScope\\s+?scope\\(\\)\\s+?default\\s+?DiagnosticScope\\.(\\w+)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["minutesToFix"] = getValueFromText(text, Regex("int\\s+?minutesToFix\\(\\)\\s+?default\\s+?(\\w+)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["activatedByDefault"] = getValueFromText(text, Regex("boolean\\s+?activatedByDefault\\(\\)\\s+?default\\s+?(true|false)\\s*?.*",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["tags"] = "`ERROR`"
    }

    private fun getValueFromText(text: String, pattern: Regex, key: String): String {
        val match = pattern.find(text)
        var result = ""
        if (match != null && match.groups.isNotEmpty()) {
            result = match.groups[1]?.value.toString()
        }
        if (result.isEmpty() && key.isNotEmpty()) {
            return defaultValues.getOrDefault(key, "")
        }
        return result
    }

    private fun getName(key: String, lang: String): String {
        val fileP = File(outputDir.get().asFile.path,
                "src/main/resources/${pathPack}/${key}Diagnostic_${lang}.properties");
        if (fileP.exists()) {
            val match = namePattern.find(fileP.readText(charset("UTF-8")));
            if (match != null && match.groups.isNotEmpty()) {
                return match.groups[1]?.value.toString();
            }
        }
        logger.quiet("File '{}' not exist", fileP.path);
        return "";
    }

    private fun getReadme(key: String, lang: String): String {
        val docPath = File(outputDir.get().asFile.path, "docs/${lang}diagnostics");
        val readme = File(docPath.path, "${key}.md");
        if (readme.exists()) {
            return "${readme.name}";
        }
        logger.quiet("File '{}' not exist", readme.path);
        return "";
    }

    private fun getTags(text: String): String {
        val matchBody = tagsBodyPattern.find(text);
        if (matchBody != null && matchBody.groups.isNotEmpty()) {
            val match = tagsListPattern.findAll(matchBody.groups[1]?.value ?: "");
            return "`" + match.map {
                it.groupValues[1]
            }.joinToString("`<br/>`").toLowerCase() + "`";
        }
        return "";
    }

    private fun writeIndex(indexText: String, lang: String, count: Int, typeMap: HashMap<String, Int>) {
        val indexPath = File(outputDir.get().asFile.path, "docs/${lang}diagnostics/index.md");
        val text = indexPath.readText(charset("UTF-8"));

        var header = "## Список реализованных диагностик";
        var total = "Общее количество:";
        var table = "| Ключ | Название | Включена по умолчанию | Важность | Тип | Тэги |";
        if (lang != "") {
            header = "## Implemented diagnostics";
            total = "Total:";
            table = "| Key | Name| Enabled by default | Severity | Type | Tags |";
        }
        table += "\n| --- | --- | :-: | --- | --- | --- |"
        total += " **$count**\n\n* ${typeMap.toString()
                .replace("{", "")
                .replace("}", "")
                .replace("=", ": **")
                .replace(",", "**\n*")}**";
        val indexHeader = text.indexOf(header);
        indexPath.writeText(text.substring(0, indexHeader - 1) + "\n${header}\n\n${total}\n\n${table}${indexText}",
                charset("UTF-8"));

    }

    @TaskAction
    fun updateIndex() {
        logger.quiet("Update diagnostics index")
        loadDefaultValues()
        var indexRu = "";
        var indexEn = "";
        var countDiagnostic = 0;
        File(outputDir.get().asFile.path, "src/main/java/${pathPack}")
                .walkBottomUp()
                .filter {
                    it.name.endsWith(".java")
                }.forEach {
                    val text = it.readText(charset("UTF-8"));
                    if (text.indexOf("@DiagnosticMetadata") >= 0) {
                        countDiagnostic++;
                        val key = it.name.substring(0, it.name.indexOf("Diagnostic"));
                        val nameRu = getName(key, "ru");
                        val nameEn = getName(key, "en");
                        var enabledStr = getValueFromText(text, enabledPattern, "activatedByDefault");

                        val enabledRu = if (enabledStr == "true") "Да" else "Нет";
                        val enabledEn = if (enabledStr == "true") "Yes" else "No";
                        val readmeRu = getReadme(key, "");
                        val readmeEn = getReadme(key, "en/");
                        val tags = getTags(text);
                        var typeRu = "";
                        var typeEn = "";
                        var severityRu = "";
                        var severityEn = "";
                        val typeStr = getValueFromText(text, typePattern, "type");
                        typeRu = typeRuMap.getOrDefault(typeStr, "Ошибка");
                        typeEn = typeEnMap.getOrDefault(typeStr, "Error");
                        typeMapRu[typeRu] = typeMapRu.getOrDefault(typeRu, 0) + 1;
                        typeMapEn[typeEn] = typeMapEn.getOrDefault(typeEn, 0) + 1;
                        val severityStr = getValueFromText(text, severityPattern, "severity");
                        severityRu = severityRuMap.getOrDefault(severityStr, "Блокирующий");
                        severityEn = severityEnMap.getOrDefault(severityStr, "Blocker");

                        indexRu += "\n| [${key}](${readmeRu}) | $nameRu | $enabledRu | $severityRu | $typeRu | $tags |";
                        indexEn += "\n| [${key}](${readmeEn}) | $nameEn | $enabledEn | $severityEn | $typeEn | $tags |";
                    }
                }
        writeIndex(indexRu, "", countDiagnostic, typeMapRu);
        writeIndex(indexEn, "en/", countDiagnostic, typeMapEn);
    }
}

tasks.register<ToolsUpdateDiagnosticsIndex>("updateDiagnosticsIndex") {
    description = "Updates diagnostics index after changes";
    group = "Developer tools";
    outputDir.set(project.layout.projectDirectory);
};