open class ToolsUpdateJsonSchema @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    private var srcPath = "src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics"
    private var schemaPath = "src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json"
    private var diagnosticSchemaPath = "src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/diagnostics-schema.json"

    private var paramsPattern = Regex("^\\s*?\\@DiagnosticParameter\\(([. \\s\\w\\W]*?)\\)\\s*\$\\s*?private\\s*?(\\w+)\\s+?(\\w+)\\s+?(?:\\=|\\;)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var metadataPattern = Regex("^\\s*?\\@DiagnosticMetadata\\(([. \\s\\w\\W]*?)\\)\\s*\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var paramsDescriptionPattern = Regex("^\\s*?description\\s*?=\\s*?\"([\\s\\w\\W]+)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var paramsDefPattern = Regex("^\\s*?defaultValue\\s*?=\\s*?(?:\\s*?\"\"\\s*\\+\\s*)*([\\w]+?),\$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private var newlinePattern = Regex("\"\\s*\\+\\s*\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private fun getValueFromText(text: String, pattern: Regex): String {
        val match = pattern.find(text)
        var result = ""
        if (match != null && match.groups.isNotEmpty()) {
            result = match.groups[1]?.value.toString()
        }
        return result
    }

    private fun getDiagnosticsMetadata(): HashMap<String, Any> {
        val path = File(outputDir.get().asFile.path, srcPath)
        val result = hashMapOf<String, Any>()
        val diagnostics = hashMapOf<String, Any>()
        val diagnosticsKeys = hashMapOf<String, HashMap<String, String>>()

        File(path.toURI())
                .walkBottomUp()
                .filter {
                    it.name.endsWith(".java") && it.name.indexOf("Diagnostic") > 0
                }.forEach {
                    val key = it.name.substring(0, it.name.indexOf("Diagnostic"))
                    val text = it.readText(charset("UTF-8"))

                    if (getValueFromText(text, metadataPattern).isNotEmpty()) {

                        diagnosticsKeys[key] = hashMapOf("\$ref" to "diagnostics-schema.json#/definitions/${key}")
                        val diagnostic = hashMapOf("\$id" to "#/definitions/${key}",
                                "type" to arrayListOf("boolean", "object"),
                                "title" to "",
                                "default" to null)

                        val matches = paramsPattern.findAll(text)
                        val params = HashMap<String, Any>()

                        matches.forEach {
                            val type = it.groupValues[2]
                                    .toLowerCase()
                                    .replace("int", "integer")
                                    .replace("pattern", "string")
                                    .replace("float", "string")
                            val oneParam = hashMapOf("type" to type)

                            val body = it.groupValues[1]
                            oneParam["title"] = getValueFromText(body, paramsDescriptionPattern)
                                    .replace(newlinePattern, "")
                            var defValue = getValueFromText(body, paramsDefPattern)
                            if (defValue.isNotEmpty()) {
                                val valPattern = Regex("\\s+?${defValue}\\s*=\\s*?([\\w\\W]+?);\$",
                                        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                                defValue = getValueFromText(text, valPattern)
                                        .replace(newlinePattern, "")
                                        .trim()
                            }
                            oneParam["example"] = defValue
                            params[it.groupValues[3]] = oneParam
                        }

                        // параметры диагностики
                        if (params.isNotEmpty()) {
                            diagnostic["properties"] = params
                        }
                        diagnostics[key] = diagnostic
                    }
                }
        result["diagnostics"] = diagnostics
        result["diagnosticsKeys"] = diagnosticsKeys
        return result
    }

    @TaskAction
    fun updateSchema() {
        logger.quiet("Update json schema")
        val diagnosticMeta = getDiagnosticsMetadata()

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
            val schemaProperties = schema["properties"]
            if (schemaProperties != null && schemaProperties is Map<*, *>) {
                val schProp = schemaProperties.toMap().toMutableMap()
                val schemaPropertiesDiagnostics = schProp["diagnostics"]
                if (schemaPropertiesDiagnostics != null && schemaPropertiesDiagnostics is Map<*, *>) {
                    val schPropDiag = schemaPropertiesDiagnostics.toMap().toMutableMap()
                    schPropDiag["properties"] = diagnosticMeta["diagnosticsKeys"]
                    schProp["diagnostics"] = schPropDiag
                }
                schema["properties"] = schProp
            }

            val resultString = groovy.json.JsonBuilder(schema).toPrettyString()
            File(outputDir.get().asFile.path, schemaPath).writeText(resultString, charset("UTF-8"))
        }
    }
}

tasks.register<ToolsUpdateJsonSchema>("updateJsonSchema") {
    description = "Update json schema"
    group = "Developer tools"
    outputDir.set(project.layout.projectDirectory)
}
