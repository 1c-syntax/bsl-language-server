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
    private var metadataActivPattern = Regex("^\\s*?activatedByDefault\\s*?=\\s*?(true|false)\\s*?.*",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataScopePattern = Regex("^\\s*?scope\\s*?=\\s*?DiagnosticScope\\.(\\w+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataTagsBodyPattern = Regex("DiagnosticTag\\.(\\w+?)(?:[\\s\\W]|\$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var namePattern = Regex("^diagnosticName\\s*=\\s*(.*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE));
    private var paramsPattern = Regex("^\\s*?\\@DiagnosticParameter\\(([. \\s\\w\\W]*?)\\)\\s*\$\\s*?private\\s*?(\\w+)\\s+?(\\w+)\\s+?(?:\\=|\\;)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var paramsDescriptionPattern = Regex("^\\s*?description\\s*?=\\s*?\"([\\s\\w\\W]+)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var paramsDefPattern = Regex("^\\s*?defaultValue\\s*?=\\s*?(?:\\s*?\"\"\\s*\\+\\s*)*([\\w]+?),\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private var newlinePattern = Regex("\"\\s*\\+\\s*\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private var srcPath = "src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics"
    private var defaultValues = hashMapOf<String, String>()
    private var templateDocHeader = "# <Description>\n\n<Metadata>\n<Params><!-- Блоки выше заполняются автоматически, не трогать -->\n"
    private var templateDocMetadata = "| <TypeHeader> | <ScopeHeader> | <SeverityHeader> | <ActivatedHeader> | <MinutesHeader> | <TagsHeader> |\n" +
            "| :-: | :-: | :-: | :-: | :-: | :-: |\n" +
            "| `<Type>` | `<Scope>` | `<Severity>` | `<Activated>` | `<Minutes>` | <Tags> |\n"

    private var templateDocHeaderParams = "| <NameHeader> | <TypeHeader> | <DescriptionHeader> | <DefHeader> |\n" +
            "| :-: | :-: | :-- | :-: |\n"
    private var templateDocLineParams = "| `<Name>` | `<Type>` | ```<Description>``` | ```<Def>``` |\n"

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
            "SECURITY_HOTSPOT" to "Потенциальная уязвимость",
            "CODE_SMELL" to "Дефект кода");

    private var typeEnMap = hashMapOf("ERROR" to "Error",
            "VULNERABILITY" to "Vulnerability",
            "SECURITY_HOTSPOT" to "Security Hotspot",
            "CODE_SMELL" to "Code smell");

    private var typeParamRuMap = hashMapOf("int" to "Число",
            "boolean" to "Булево",
            "String" to "Строка",
            "Boolean" to "Число",
            "Pattern" to "Регулярное выражение");

    private fun getMetadataFromText(text: String, metadata: HashMap<String, Any>) {
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
            if (tagsString.isEmpty()) {
                tagsString = defaultValues.getOrDefault("tags", "")
            }
            metadata["tags"] = tagsString

            val matches = paramsPattern.findAll(text)
            val params = arrayListOf<HashMap<String, String>>()

            matches.forEach {
                val oneParam = hashMapOf("type" to it.groupValues[2], "name" to it.groupValues[3])
                val body = it.groupValues[1]
                oneParam["description"] = getValueFromText(body, paramsDescriptionPattern, "")
                        .replace(newlinePattern, "")
                var defValue = getValueFromText(body, paramsDefPattern, "")
                if(defValue.isNotEmpty()) {
                    val valPattern = Regex("\\s+?${defValue}\\s*=\\s*?([\\w\\W]+?);\$",
                            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                    defValue = getValueFromText(text, valPattern, "")
                            .replace(newlinePattern, "")
                            .trim()
                }
                oneParam["defaultValue"] = defValue
                params.add(oneParam)
            }
            metadata["params"] = params
        }
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

    private fun getDiagnosticsMetadata(): HashMap<String, HashMap<String, Any>> {
        val path = File(outputDir.get().asFile.path, srcPath)
        val result = hashMapOf<String, HashMap<String, Any>>()

        File(path.toURI())
                .walkBottomUp()
                .filter {
                    it.name.endsWith(".java") && it.name.indexOf("Diagnostic") > 0
                }.forEach {
                    val key = it.name.substring(0, it.name.indexOf("Diagnostic"))
                    val text = it.readText(charset("UTF-8"))
                    val metadata = hashMapOf<String, Any>()
                    getMetadataFromText(text, metadata)
                    if (metadata.isNotEmpty()) {
                        result[key] = metadata
                    }
                }

        return result
    }

    private fun updateDocFile(lang: String, key: String, metadata: HashMap<String, Any>) {
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
        if (indexHeader < 0) {
            docPath.writeText(addText + header + "\n\n" + text, charset("UTF-8"))
        } else {
            docPath.writeText(addText + text.substring(indexHeader),
                    charset("UTF-8"))
        }
    }

    private fun getDiagnosticDescription(key: String, lang: String): String {
        var langNew = if (lang == "") "ru" else "en"
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

    private fun makeDiagnosticMetadata(lang: String, metadata: HashMap<String, Any>): String {
        var metadataBody = templateDocMetadata
                .replace("<Minutes>", metadata.getOrDefault("minutesToFix", "").toString())
                .replace("<Tags>", metadata.getOrDefault("tags", "").toString())
                .replace("<Scope>", if (metadata.getOrDefault("scope", "").toString() == "ALL") "BSL`<br/>`OS" else metadata.getOrDefault("scope", "").toString())

        if (lang == "") {
            metadataBody = metadataBody
                    .replace("<TypeHeader>", "Тип")
                    .replace("<ScopeHeader>", "Поддерживаются<br/>языки")
                    .replace("<SeverityHeader>", "Важность")
                    .replace("<ActivatedHeader>", "Включена<br/>по умолчанию")
                    .replace("<MinutesHeader>", "Время на<br/>исправление (мин)")
                    .replace("<TagsHeader>", "Тэги")
                    .replace("<Type>", typeRuMap.getOrDefault(metadata.getOrDefault("type", ""), ""))
                    .replace("<Severity>", severityRuMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                    .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() == "true") "Да" else "Нет")
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
                    .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() == "true") "Yes" else "No")

        }

        return metadataBody;
    }

    private fun makeDiagnosticParams(key: String, lang: String, metadata: HashMap<String, Any>): String {
        val params = metadata.getOrDefault("params", arrayListOf<HashMap<String, String>>())
        if(params is ArrayList<*>) {

            if (params.isEmpty()) {
                return ""
            }

            var paramsBody = templateDocHeaderParams

            if (lang == "") {
                paramsBody = "## Параметры \n\n" + paramsBody
                        .replace("<NameHeader>", "Имя")
                        .replace("<TypeHeader>", "Тип")
                        .replace("<DescriptionHeader>", "Описание")
                        .replace("<DefHeader>", "Значение по умолчанию")

                params.forEach {
                    if (it is HashMap<*, *>) {
                        var typeValue = it.getOrDefault("type", "").toString()
                        typeValue = typeParamRuMap.getOrDefault(typeValue, typeValue)
                        paramsBody += templateDocLineParams
                                .replace("<Name>", it.getOrDefault("name", "").toString())
                                .replace("<Type>", typeValue)
                                .replace("<Description>", it.getOrDefault("description", "").toString())
                                .replace("<Def>", it.getOrDefault("defaultValue", "").toString())
                    }
                }

            } else {
                paramsBody = "## Parameters \n\n" + paramsBody
                        .replace("<NameHeader>", "Name")
                        .replace("<TypeHeader>", "Type")
                        .replace("<DescriptionHeader>", "Description")
                        .replace("<DefHeader>", "Default value")

                params.forEach {
                    if (it is HashMap<*, *>) {
                        paramsBody += templateDocLineParams
                                .replace("<Name>", it.getOrDefault("name", "").toString())
                                .replace("<Type>", it.getOrDefault("type", "").toString())
                                .replace("<Description>", it.getOrDefault("description", "").toString())
                                .replace("<Def>", it.getOrDefault("defaultValue", "").toString())
                    }
                }
            }
            return paramsBody + "\n"
        }

        return ""
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
        defaultValues["activatedByDefault"] = getValueFromText(text, Regex("boolean\\s+?activatedByDefault\\(\\)\\s+?default\\s+?(true|false)\\s*?.*",
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

tasks.register<ToolsUpdateDiagnosticDocs>("updateDiagnosticDocs") {
    description = "Updates diagnostic docs after changes"
    group = "Developer tools"
    outputDir.set(project.layout.projectDirectory)
}