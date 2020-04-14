import java.net.URL
import java.net.URLClassLoader

open class DeveloperTools @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {
    lateinit var taskName: String // таска для исполнения

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    private var schemaPath = "src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json"
    private var diagnosticSchemaPath = "src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/parameters-schema.json"

    private var templateDocHeader = "# <Description> (<DiagnosticKey>)\n\n<Metadata>\n<Params>" +
            "<!-- Блоки выше заполняются автоматически, не трогать -->\n"
    private var templateDocFooter =
            "## <Helpers>\n\n" +
                    "<!-- Блоки ниже заполняются автоматически, не трогать -->\n" +
                    "### <DiagnosticIgnorance>\n\n```bsl\n// BSLLS:<DiagnosticKey>-off\n// BSLLS:<DiagnosticKey>-on\n```\n\n" +
                    "### <ParameterConfig>\n\n```json\n\"<DiagnosticKey>\": <DiagnosticConfig>\n```\n"
    private var templateDocMetadata = "| <TypeHeader> | <ScopeHeader> | <SeverityHeader> | <ActivatedHeader> | <MinutesHeader> | <TagsHeader> |\n" +
            "| :-: | :-: | :-: | :-: | :-: | :-: |\n" +
            "| `<Type>` | `<Scope>` | `<Severity>` | `<Activated>` | `<Minutes>` | <Tags> |\n"
    private var templateDocHeaderParams = "| <NameHeader> | <TypeHeader> | <DescriptionHeader> | <DefHeader> |\n" +
            "| :-: | :-: | :-- | :-: |\n"
    private var templateDocLineParams = "| `<Name>` | `<Type>` | ```<Description>``` | ```<Def>``` |\n"
    private var templateIndexLine = "\n| [<Name>](<Name>.md) | <Description> | <Activated> | <Severity> | <Type> | <Tags> |"

    private var typeRuMap = hashMapOf("ERROR" to "Ошибка",
            "VULNERABILITY" to "Уязвимость",
            "SECURITY_HOTSPOT" to "Потенциальная уязвимость",
            "CODE_SMELL" to "Дефект кода")

    private var typeEnMap = hashMapOf("ERROR" to "Error",
            "VULNERABILITY" to "Vulnerability",
            "SECURITY_HOTSPOT" to "Security Hotspot",
            "CODE_SMELL" to "Code smell")

    private var severityRuMap = hashMapOf("BLOCKER" to "Блокирующий",
            "CRITICAL" to "Критичный",
            "MAJOR" to "Важный",
            "MINOR" to "Незначительный",
            "INFO" to "Информационный")

    private var severityEnMap = hashMapOf("BLOCKER" to "Blocker",
            "CRITICAL" to "Critical",
            "MAJOR" to "Major",
            "MINOR" to "Minor",
            "INFO" to "Info")

    private var typeParamRuMap = hashMapOf(
            "Integer" to "Целое",
            "Boolean" to "Булево",
            "String" to "Строка",
            "Float" to "Число с плавающей точкой")

    private fun createDiagnosticSupplier(lang: String, classLoader: ClassLoader): Any {
        val languageServerConfigurationClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration")
        val languageClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.configuration.Language")

        val lsConfiguration = languageServerConfigurationClass
                .getDeclaredMethod("create")
                .invoke(languageServerConfigurationClass)
        val language = languageClass.getMethod("valueOf", classLoader.loadClass("java.lang.String"))
                .invoke(languageClass, lang.toUpperCase())

        languageServerConfigurationClass.getDeclaredMethod("setLanguage", languageClass)
                .invoke(lsConfiguration, language)

        return classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier").declaredConstructors[0].newInstance(lsConfiguration)
    }

    private fun createClassLoader(): ClassLoader {
        val urls: ArrayList<URL> = ArrayList()
        File(project.buildDir, "classes")
                .walkTopDown()
                .forEach { urls.add(it.toURI().toURL()) }

        File(project.buildDir, "resources")
                .walkTopDown()
                .forEach { urls.add(it.toURI().toURL()) }

        val urlsParent: ArrayList<URL> = ArrayList()
        project.configurations.getByName("runtimeClasspath").files.forEach {
            urlsParent.add(it.toURI().toURL())
        }
        val parentCL = URLClassLoader(urlsParent.toTypedArray())
        return URLClassLoader(urls.toTypedArray(), parentCL)
    }

    private fun getDiagnosticsMetadata(): HashMap<String, HashMap<String, Any>> {

        val classLoader = createClassLoader()
        val diagnosticSupplierClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier")

        val diagnosticSupplierRU = createDiagnosticSupplier("ru", classLoader)
        val diagnosticSupplierEN = createDiagnosticSupplier("en", classLoader)

        val result = hashMapOf<String, HashMap<String, Any>>()
        File(project.buildDir, "classes/java/main/com/github/_1c_syntax/bsl/languageserver/diagnostics")
                .walkTopDown()
                .filter {
                    it.name.endsWith("Diagnostic.class")
                            && !it.name.startsWith("Abstract")
                }
                .forEach {
                    val diagnosticClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.${it.nameWithoutExtension}")
                    if (!diagnosticClass.toString().startsWith("interface")) {
                        val diagnosticInstanceRU = diagnosticSupplierClass.getDeclaredMethod("getDiagnosticInstance", diagnosticClass.javaClass).invoke(diagnosticSupplierRU, diagnosticClass)
                        val diagnosticInstanceEN = diagnosticSupplierClass.getDeclaredMethod("getDiagnosticInstance", diagnosticClass.javaClass).invoke(diagnosticSupplierEN, diagnosticClass)

                        val metadata = getDiagnosticMetadata(diagnosticClass, diagnosticInstanceRU, diagnosticInstanceEN, classLoader)
                        if (metadata.isNotEmpty() && metadata["key"] is String) {
                            result[metadata["key"] as String] = metadata
                        }
                    }
                }

        return result
    }

    private fun getDiagnosticMetadata(diagnosticClass: Class<*>, diagnosticInstanceRU: Any, diagnosticInstanceEN: Any, classLoader: ClassLoader): HashMap<String, Any> {
        val diagnosticInfoClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo")
        val diagnosticParameterInfoClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameterInfo")
        val diagnosticeCodeClass = classLoader.loadClass("com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode")

        val infoRU = diagnosticClass.getMethod("getInfo").invoke(diagnosticInstanceRU)
        val infoEN = diagnosticClass.getMethod("getInfo").invoke(diagnosticInstanceEN)

        val diagnosticCode = diagnosticInfoClass.getMethod("getCode").invoke(infoRU)
        val code = diagnosticeCodeClass.getMethod("getStringValue").invoke(diagnosticCode)

        val metadata = hashMapOf<String, Any>()
        metadata["key"] = code
        metadata["type"] = diagnosticInfoClass.getMethod("getType").invoke(infoRU).toString()
        metadata["severity"] = diagnosticInfoClass.getMethod("getSeverity").invoke(infoRU).toString()
        metadata["scope"] = diagnosticInfoClass.getMethod("getScope").invoke(infoRU).toString()
        metadata["minutesToFix"] = diagnosticInfoClass.getMethod("getMinutesToFix").invoke(infoRU).toString()
        metadata["activatedByDefault"] = diagnosticInfoClass.getMethod("isActivatedByDefault").invoke(infoRU)
        metadata["tags"] = diagnosticInfoClass.getMethod("getTags").invoke(infoRU).toString()
        metadata["description_ru"] = diagnosticInfoClass.getMethod("getName").invoke(infoRU).toString()
        metadata["description_en"] = diagnosticInfoClass.getMethod("getName").invoke(infoEN).toString()

        val params = arrayListOf<HashMap<String, String>>()
        val parameters = diagnosticInfoClass.getMethod("getParameters").invoke(infoRU)
        if (parameters is ArrayList<*>) {
            for (parameter in parameters) {
                val oneParameter = hashMapOf<String, String>()
                val parameterName = diagnosticParameterInfoClass.getMethod("getName").invoke(parameter).toString()
                oneParameter["name"] = parameterName
                val typeArr = diagnosticParameterInfoClass.getMethod("getType").invoke(parameter).toString().split(".")
                oneParameter["type"] = typeArr[typeArr.size - 1]
                oneParameter["defaultValue"] = diagnosticParameterInfoClass.getMethod("getDefaultValue").invoke(parameter).toString()
                oneParameter["description_ru"] = diagnosticParameterInfoClass.getMethod("getDescription").invoke(parameter).toString()
                oneParameter["description_en"] = diagnosticInfoClass.getMethod("getResourceString", classLoader.loadClass("java.lang.String"))
                        .invoke(infoEN, parameterName).toString()

                params.add(oneParameter)
            }
        }

        metadata["parameters"] = params
        return metadata
    }

    @TaskAction
    fun execTask() {
        val diagnosticsMetadata = getDiagnosticsMetadata()
        if (taskName == "Update diagnostic docs") {
            logger.quiet("Update diagnostic docs")
            diagnosticsMetadata.forEach {
                updateDocFile("ru",
                        it.key,
                        it.value)

                updateDocFile("en",
                        it.key,
                        it.value)
            }
        } else if (taskName == "Update diagnostics index") {
            logger.quiet("Update diagnostics index")
            val diagnosticsMetadataSort = diagnosticsMetadata.toSortedMap()
            updateDiagnosticIndex("ru", diagnosticsMetadataSort)
            updateDiagnosticIndex("en", diagnosticsMetadataSort)
        } else if (taskName == "Update json schema") {
            val diagnosticMeta = transformMetadata(diagnosticsMetadata)
            var schemaJson = groovy.json.JsonSlurper().parseText(File(outputDir.get().asFile.path, diagnosticSchemaPath).readText(charset("UTF-8")))
            if (schemaJson is Map<*, *>) {
                val schema = schemaJson.toMap().toMutableMap()
                schema["definitions"] = diagnosticMeta["diagnostics"]
                val resultString = groovy.json.JsonBuilder(schema).toPrettyString()
                File(outputDir.get().asFile.path, diagnosticSchemaPath).writeText(resultString, charset("UTF-8"))
            }

            schemaJson = groovy.json.JsonSlurper().parseText(File(outputDir.get().asFile.path, schemaPath).readText(charset("UTF-8")))
            if (schemaJson is Map<*, *>) {
                val schema = schemaJson.toMap().toMutableMap()
                val schemaDefinitions = schema["definitions"]
                if (schemaDefinitions != null && schemaDefinitions is Map<*, *>) {
                    val schemaDefinitionInner = schemaDefinitions.toMap().toMutableMap()
                    val schemaParameters = schemaDefinitionInner["parameters"]
                    if (schemaParameters != null && schemaParameters is Map<*, *>) {
                        val schemaParametersInner = schemaParameters.toMap().toMutableMap()
                        schemaParametersInner["properties"] = diagnosticMeta["diagnosticsKeys"]
                        schemaDefinitionInner["parameters"] = schemaParametersInner
                    }
                    schema["definitions"] = schemaDefinitionInner
                }

                val resultString = groovy.json.JsonBuilder(schema).toPrettyString()
                File(outputDir.get().asFile.path, schemaPath).writeText(resultString, charset("UTF-8"))
            }
        }
    }

    private fun transformMetadata(diagnosticsMetadata: HashMap<String, HashMap<String, Any>>): HashMap<String, Any> {
        val result = hashMapOf<String, Any>()
        val diagnostics = sortedMapOf<String, Any>()
        val diagnosticsKeys = sortedMapOf<String, HashMap<String, String>>()

        diagnosticsMetadata.forEach { itd ->
            diagnosticsKeys[itd.key] = hashMapOf("\$ref" to "parameters-schema.json#/definitions/${itd.key}")
            val diagnostic = hashMapOf("\$id" to "#/definitions/${itd.key}",
                    "type" to arrayListOf("boolean", "object"),
                    "title" to itd.value.getOrDefault("description_en", "").toString(),
                    "description" to itd.value.getOrDefault("description_en", "").toString(),
                    "default" to null)
            val params = HashMap<String, Any>()

            val parameters = itd.value.getOrDefault("parameters", arrayListOf<HashMap<String, String>>()) as ArrayList<*>
            if (parameters.isNotEmpty()) {
                parameters.forEach {
                    if (it is HashMap<*, *>) {
                        val typeString = it.getOrDefault("type", "").toString().toLowerCase()
                                .replace("pattern", "string")
                                .replace("float", "number")
                        val value = when (typeString) {
                            "boolean" -> {
                                it.getOrDefault("defaultValue", "false").toString().toBoolean()
                            }
                            "integer" -> {
                                it.getOrDefault("defaultValue", "0").toString().toInt()
                            }
                            "number" -> {
                                it.getOrDefault("defaultValue", "0").toString().toFloat()
                            }
                            else -> {
                                "${it.getOrDefault("defaultValue", "")}"
                            }
                        }
                        val oneParam = hashMapOf(
                                "type" to typeString,
                                "title" to it.getOrDefault("description_en", "").toString(),
                                "description" to it.getOrDefault("description_en", "").toString(),
                                "default" to value)

                        params[it.getOrDefault("name", "").toString()] = oneParam
                    }
                }
            }

            if (params.isNotEmpty()) {
                diagnostic["properties"] = params
            }
            diagnostics[itd.key] = diagnostic
        }

        result["diagnostics"] = diagnostics
        result["diagnosticsKeys"] = diagnosticsKeys
        return result
    }

    private fun updateDiagnosticIndex(lang: String, diagnosticsMetadata: Map<String, HashMap<String, Any>>) {
        var indexText = ""
        val typeCount = hashMapOf<String, Int>()
        diagnosticsMetadata.forEach {
            val metadata = it.value
            val typeString: String
            val tags = metadata.getOrDefault("tags", "").toString().toLowerCase()
                    .replace("[", "`")
                    .replace("]", "`")
                    .replace(", ", "`<br/>`")
            if (lang == "ru") {
                typeString = typeRuMap.getOrDefault(metadata.getOrDefault("type", ""), "")
                indexText += templateIndexLine
                        .replace("<Name>", it.key)
                        .replace("<Description>", metadata["description_ru"].toString())
                        .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() != "false") "Да" else "Нет")
                        .replace("<Severity>", severityRuMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                        .replace("<Type>", typeString)
                        .replace("<Tags>", tags)
            } else {
                typeString = typeEnMap.getOrDefault(metadata.getOrDefault("type", ""), "")
                indexText += templateIndexLine
                        .replace("<Name>", it.key)
                        .replace("<Description>", metadata["description_en"].toString())
                        .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() != "false") "Yes" else "No")
                        .replace("<Severity>", severityEnMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                        .replace("<Type>", typeString)
                        .replace("<Tags>", tags)
            }

            typeCount[typeString] = typeCount.getOrDefault(typeString, 0) + 1
        }

        val indexPath = if (lang == "ru") {
            File(outputDir.get().asFile.path, "docs/diagnostics/index.md")
        } else {
            File(outputDir.get().asFile.path, "docs/en/diagnostics/index.md")
        }

        val text = indexPath.readText(charset("UTF-8"))

        var header = "## Список реализованных диагностик"
        var total = "Общее количество:"
        var table = "| Ключ | Название | Включена по умолчанию | Важность | Тип | Тэги |"
        if (lang == "en") {
            header = "## Implemented diagnostics"
            total = "Total:"
            table = "| Key | Name| Enabled by default | Severity | Type | Tags |"
        }
        table += "\n| --- | --- | :-: | --- | --- | --- |"
        total += " **${diagnosticsMetadata.size}**\n\n* ${typeCount.toString()
                .replace("{", "")
                .replace("}", "")
                .replace("=", ": **")
                .replace(",", "**\n*")}**"
        val indexHeader = text.indexOf(header)
        indexPath.writeText(text.substring(0, indexHeader - 1) + "\n${header}\n\n${total}\n\n${table}${indexText}",
                charset("UTF-8"))
    }

    private fun updateDocFile(lang: String, key: String, metadata: HashMap<String, Any>) {
        val docPath = if (lang == "ru") {
            File(outputDir.get().asFile.path, "docs/diagnostics/${key}.md")
        } else {
            File(outputDir.get().asFile.path, "docs/en/diagnostics/${key}.md")
        }

        val text = docPath.readText(charset("UTF-8"))

        var header = "## Описание диагностики"
        var footer = "## Сниппеты"
        val headerText = templateDocHeader
                .replace("<Description>", metadata["description_$lang"].toString())
                .replace("<Metadata>", makeDiagnosticMetadata(lang, metadata))
                .replace("<Params>", makeDiagnosticParams(lang, metadata))
                .replace("<DiagnosticKey>", key)

        var footerText = templateDocFooter
                .replace("<DiagnosticKey>", key)
                .replace("<DiagnosticConfig>", makeDiagnosticConfigExample(metadata))

        if (lang == "ru") {
            footerText = footerText
                    .replace("<Helpers>", "Сниппеты")
                    .replace("<DiagnosticIgnorance>", "Экранирование кода")
                    .replace("<ParameterConfig>", "Параметр конфигурационного файла")
        } else {
            footerText = footerText
                    .replace("<Helpers>", "Snippets")
                    .replace("<DiagnosticIgnorance>", "Diagnostic ignorance in code")
                    .replace("<ParameterConfig>", "Parameter for config")
            header = "## Description"
            footer = "## Snippets"
        }

        var index = text.indexOf(header)
        var newText = if (index < 0) {
            "$headerText$header\n\n$text"
        } else {
            "$headerText${text.substring(index)}"
        }

        index = newText.indexOf(footer)
        newText = if (index < 1) {
            "${newText.trimEnd()}\n\n$footerText"
        } else {
            "${newText.substring(0, index - 1).trimEnd()}\n\n$footerText"
        }

        docPath.writeText(newText, charset("UTF-8"))
    }

    private fun makeDiagnosticMetadata(lang: String, metadata: HashMap<String, Any>): String {
        var metadataBody = templateDocMetadata
                .replace("<Minutes>", metadata.getOrDefault("minutesToFix", "").toString())
                .replace("<Tags>", metadata.getOrDefault("tags", "").toString().toLowerCase()
                        .replace("[", "`")
                        .replace("]", "`")
                        .replace(", ", "`<br/>`"))
                .replace("<Scope>", if (metadata.getOrDefault("scope", "").toString() == "ALL") "BSL`<br/>`OS" else metadata.getOrDefault("scope", "").toString())

        if (lang == "ru") {
            metadataBody = metadataBody
                    .replace("<TypeHeader>", "Тип")
                    .replace("<ScopeHeader>", "Поддерживаются<br/>языки")
                    .replace("<SeverityHeader>", "Важность")
                    .replace("<ActivatedHeader>", "Включена<br/>по умолчанию")
                    .replace("<MinutesHeader>", "Время на<br/>исправление (мин)")
                    .replace("<TagsHeader>", "Тэги")
                    .replace("<Type>", typeRuMap.getOrDefault(metadata.getOrDefault("type", ""), ""))
                    .replace("<Severity>", severityRuMap.getOrDefault(metadata.getOrDefault("severity", ""), ""))
                    .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() != "false") "Да" else "Нет")
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
                    .replace("<Activated>", if (metadata.getOrDefault("activatedByDefault", "").toString().toLowerCase() != "false") "Yes" else "No")
        }

        return metadataBody
    }

    private fun makeDiagnosticParams(lang: String, metadata: HashMap<String, Any>): String {
        val params = metadata.getOrDefault("parameters", arrayListOf<HashMap<String, String>>()) as ArrayList<*>
        if (params.isEmpty()) {
            return ""
        }

        var paramsBody = templateDocHeaderParams

        if (lang == "ru") {
            paramsBody = "## Параметры \n\n" + paramsBody
                    .replace("<NameHeader>", "Имя")
                    .replace("<TypeHeader>", "Тип")
                    .replace("<DescriptionHeader>", "Описание")
                    .replace("<DefHeader>", "Значение по умолчанию")

            params.forEach {
                if (it is HashMap<*, *>) {
                    var typeValue = it.getOrDefault("type", "").toString()
                    typeValue = typeParamRuMap.getOrDefault(typeValue, typeValue)
                    val paramName = it.getOrDefault("name", "").toString()
                    paramsBody += templateDocLineParams
                            .replace("<Name>", paramName)
                            .replace("<Type>", typeValue)
                            .replace("<Description>", it.getOrDefault("description_ru", "").toString())
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
                    val paramName = it.getOrDefault("name", "").toString()
                    paramsBody += templateDocLineParams
                            .replace("<Name>", paramName)
                            .replace("<Type>", it.getOrDefault("type", "").toString())
                            .replace("<Description>", it.getOrDefault("description_en", "").toString())
                            .replace("<Def>", it.getOrDefault("defaultValue", "").toString())
                }
            }
        }
        return paramsBody + "\n"
    }

    private fun makeDiagnosticConfigExample(metadata: HashMap<String, Any>): String {
        val params = metadata.getOrDefault("parameters", arrayListOf<HashMap<String, String>>())
        if (params is ArrayList<*>) {

            if (params.isEmpty()) {
                return "false"
            }

            var configBody = ""
            var configDelimiter = ""
            params.forEach {
                if (it is HashMap<*, *>) {
                    val qoutes = if (it.getOrDefault("type", "") == "Boolean"
                            || it.getOrDefault("type", "") == "Integer"
                            || it.getOrDefault("type", "") == "Float") "" else "\""
                    configBody += "$configDelimiter    \"${it.getOrDefault("name", "")}\": " +
                            "${qoutes}${it.getOrDefault("defaultValue", "").toString().replace("\\", "\\\\")}${qoutes}"
                    configDelimiter = ",\n"
                }
            }
            configBody = "{\n${configBody}\n}"
            return configBody
        }

        return ""
    }

}

tasks.register<DeveloperTools>("updateDiagnosticDocs") {
    dependsOn(":jar")
    group = "Developer tools"
    description = "Updates diagnostic docs after changes"
    outputDir.set(project.layout.projectDirectory)
    taskName = "Update diagnostic docs"
    outputs.upToDateWhen { false }
}

tasks.register<DeveloperTools>("updateDiagnosticsIndex") {
    dependsOn(":jar")
    group = "Developer tools"
    description = "Update diagnostics index after changes"
    outputDir.set(project.layout.projectDirectory)
    taskName = "Update diagnostics index"
    outputs.upToDateWhen { false }
}

tasks.register<DeveloperTools>("updateJsonSchema") {
    dependsOn(":jar")
    group = "Developer tools"
    description = "Update json schema"
    outputDir.set(project.layout.projectDirectory)
    taskName = "Update json schema"
    outputs.upToDateWhen { false }
}
