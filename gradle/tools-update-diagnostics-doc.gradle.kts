open class ToolsUpdateDiagnosticDocs @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    private var metadataPattern = Regex("^\\s*?\\@DiagnosticMetadata\\(([. \\s\\w\\W]*?)\\)\\s*\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataTypePattern = Regex("^\\s*?type\\s*?=\\s*?DiagnosticType\\.(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataSeverityPattern = Regex("^\\s*?severity\\s*?=\\s*?DiagnosticSeverity\\.(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataMinPattern = Regex("^\\s*?minutesToFix\\s*?=\\s*?(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataActivPattern = Regex("^\\s*?activatedByDefault\\s*?=\\s*?(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataScopePattern = Regex("^\\s*?scope\\s*?=\\s*?DiagnosticScope\\.(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataTagsBodyPattern = Regex("DiagnosticTag\\.(\\w+?)(?:[\\s\\W]|\$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private var namePattern = Regex("^diagnosticName\\s*=\\s*(.*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));

    private var srcPath = "src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics"
    private var defaultValues = hashMapOf("t" to "t")
    private var templateDocHeader = "# <Description>\n\n<Metadata>\n\n## <Params>\n\n"
    private var templateDocMetadata = "| <TypeHeader> | <ScopeHeader> | <SeverityHeader> | <ActivatedHeader> | <MinutesHeader> | <TagsHeader> |\n" +
            "| :-: | :-: | :-: | :-: | :-: | :-: |\n" +
            "| `<Type>` | `<Scope>` | `<Severity>` | `<Activated>` | `<Minutes>` | <Tags> |\n"

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

    private var typeRuMap = hashMapOf("ERROR" to "Ошибка",
            "VULNERABILITY" to "Уязвимость",
            "CODE_SMELL" to "Дефект кода");

    private var typeEnMap = hashMapOf("ERROR" to "Error",
            "VULNERABILITY" to "Vulnerability",
            "CODE_SMELL" to "Code smell");

    private fun getMetadataFromText(text: String, metadata: HashMap<String, String>) {
        val match = metadataPattern.find(text)
        if (match != null && match.groups.isNotEmpty()) {
            val metadataText = match.groups[1]?.value.toString()
            metadata["type"] = getValueFromText(metadataText, metadataTypePattern, "type")
            metadata["severity"] = getValueFromText(metadataText, metadataSeverityPattern, "severity")
            metadata["minutesToFix"] = getValueFromText(metadataText, metadataMinPattern, "minutesToFix")
            metadata["activatedByDefault"] = getValueFromText(metadataText, metadataActivPattern, "activatedByDefault")
            metadata["scope"] = getValueFromText(metadataText, metadataScopePattern, "scope")
            var tagsString = "`" + metadataTagsBodyPattern.findAll(metadataText).map {
                it.groupValues[1]
            }.joinToString("`<br/>`").toLowerCase() + "`"
            if(tagsString.isEmpty()) {
                tagsString = defaultValues.getOrDefault("tags", "")
            }
            metadata["tags"] = tagsString
        }
    }

    private fun getValueFromText(text: String, pattern: Regex, key: String): String {
        val match = pattern.find(text)
        var result = ""
        if (match != null && match.groups.isNotEmpty()) {
            result = match.groups[1]?.value.toString()
        }
        if (result.isBlank() && key.isNotEmpty()) {
            return defaultValues.getOrDefault(key, "")
        }
        return result
    }

    private fun getDiagnosticsMetadata(): HashMap<String, HashMap<String, String>> {
        val path = File(outputDir.get().asFile.path, srcPath)
        var paramsPattern = Regex("^\\s*?\\@DiagnosticParameter\\(([. \\s\\w\\W]*?)\\)\\s*\$\\s*?private\\s*?(\\w+)\\s+?(\\w+)\\s+?(?:\\=|\\;)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val result = hashMapOf("t" to hashMapOf("t" to "t"))
        result.clear()

        File(path.toURI())
                .walkBottomUp()
                .filter {
                    it.name.endsWith(".java") && it.name.indexOf("Diagnostic") > 0
                }.forEach {
                    val key = it.name.substring(0, it.name.indexOf("Diagnostic"))
                    val text = it.readText(charset("UTF-8"))
                    val metadata = hashMapOf("t" to "t")
                    metadata.clear()
                    getMetadataFromText(text, metadata)
                    if(metadata.isNotEmpty()) {
                        result[key] = metadata
                    }
                }

        return result
    }

    private fun updateDocFile(lang: String, key: String, metadata: HashMap<String, String>) {
        val docPath = File(outputDir.get().asFile.path, "docs/${lang}diagnostics/${key}.md")
        val text = docPath.readText(charset("UTF-8"))

        var header = "## Описание диагностики"
        if (lang != "") {
            header = "## Description"
        }
        val addText = templateDocHeader
                .replace("<Description>", getDiagnosticDescription(key, lang))
                .replace("<Metadata>", makeDiagnosticMetadata(lang, metadata))
                .replace("<Params>", makeDiagnosticParams(key, lang, metadata))

        val indexHeader = text.indexOf(header)
        if(indexHeader < 0) {
            docPath.writeText(addText + header + "\n\n" + text, charset("UTF-8"))
        } else {
            docPath.writeText(addText + text.substring(indexHeader + header.length + 1),
                    charset("UTF-8"))
        }
        logger.quiet("File {} updated", docPath)
    }

    private fun getDiagnosticDescription(key: String, lang: String): String {
        var langNew = if(lang == "") "ru" else "en"
        val fileP = File(outputDir.get().asFile.path,
                "src/main/resources/com/github/_1c_syntax/bsl/languageserver/diagnostics/${key}Diagnostic_${langNew}.properties");
        if (fileP.exists()) {
            val match = namePattern.find(fileP.readText(charset("UTF-8")));
            if (match != null && match.groups.isNotEmpty()) {
                return match.groups[1]?.value.toString();
            }
        }
        logger.quiet("File '{}' not exist", fileP.path);
        return "";
    }

    private fun makeDiagnosticMetadata(lang: String, metadata: HashMap<String, String>): String {
        var metadataBody = templateDocMetadata
                .replace("<Minutes>", metadata.getOrDefault("minutesToFix", ""))
                .replace("<Tags>", metadata.getOrDefault("tags", ""))
                .replace("<Scope>", if(metadata.getOrDefault("scope", "") == "ALL") "BSL`<br/>`OS" else metadata.getOrDefault("scope", ""))

        if(lang == "") {
            metadataBody = metadataBody
                    .replace("<TypeHeader>", "Тип")
                    .replace("<ScopeHeader>", "Поддерживаются<br/>языки")
                    .replace("<SeverityHeader>", "Важность")
                    .replace("<ActivatedHeader>", "Включена<br/>по умолчанию")
                    .replace("<MinutesHeader>", "Время на<br/>исправление (мин)")
                    .replace("<TagsHeader>", "Тэги")
                    .replace("<Type>", typeRuMap.getOrDefault(metadata.getOrDefault("type", ""), ""))
                    .replace("<Severity>", severityRuMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                    .replace("<Activated>", if(metadata.getOrDefault("activatedByDefault", "").toUpperCase() == "YES") "Да" else "Нет")
        } else {
            metadataBody = metadataBody
                    .replace("<TypeHeader>", "Type")
                    .replace("<ScopeHeader>", "Scope")
                    .replace("<SeverityHeader>", "Severity")
                    .replace("<ActivatedHeader>", "Activated<br/>by default")
                    .replace("<MinutesHeader>", "Minutes<br/>to fix")
                    .replace("<TagsHeader>", "Tags")
                    .replace("<Type>", typeEnMap.getOrDefault(metadata.getOrDefault("type", ""), ""))
                    .replace("<Severity>", severityEnMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                    .replace("<Activated>", if(metadata.getOrDefault("activatedByDefault", "").toUpperCase() == "YES") "Да" else "Нет")

        }

        return metadataBody;
    }

    private fun makeDiagnosticParams(key: String, lang: String, metadata: HashMap<String, String>): String {
        return "TODO PARAMS"
    }

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
        defaultValues["activatedByDefault"] = getValueFromText(text, Regex("boolean\\s+?activatedByDefault\\(\\)\\s+?default\\s+?(\\w+)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)), "")
        defaultValues["tags"] = "`ERROR`"
    }
    
    @TaskAction
    fun updateDocs() {
        logger.quiet("Update diagnostic docs")

        loadDefaultValues();
        val result = getDiagnosticsMetadata()
        result.forEach {
            updateDocFile("",
                    it.key,
                    it.value)
            updateDocFile("en/",
                    it.key,
                    it.value)
        }
    }
}

tasks.register<ToolsUpdateDiagnosticDocs> ("updateDiagnosticDocs") {
    description = "Updates diagnostic docs after changes"
    group = "Developer tools"
    outputDir.set(project.layout.projectDirectory)
}