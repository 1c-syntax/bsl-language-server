# BSL Language Server Configuration

BSL Language Server provides the ability to change the settings using a configuration file in json format.
 The created file must be specified using the key `--configuration ` *(or `-c`)*  when running BSL Language Server as a console application. If you use the editor / IDE with the BSL Language Server client plugin, place it in accordance with the documentation *(this is usually the root of a project or workspace)*.

## Settings

Name | Type | Description
--- | --- | ---
`diagnosticLanguage` | `String` | Set the language for displaying diagnosed comments. Supported languages:<br>* `ru` - for Russian (*default*)<br>* `en` - for English
`showCognitiveComplexityCodeLens` | `Boolean` | In code editors/IDE with support codelens*(for example [Visual Studio Code](https://code.visualstudio.com/))*, enables displaying the value[ of the cognitive complexity](../diagnostics/CognitiveComplexity.md) of the method over its definition. By default is enabled (*is set to `true`*)
`showCyclomaticComplexityCodeLens` | `Boolean` | Similar to `showCognitiveComplexityCodeLens`, enables the display of the [cyclomatic complexity](../diagnostics/CyclomaticComplexity.md) value   of the method. By default enabled (*is set to `true`*)
`computeDiagnosticsTrigger` | `String` | Event that will trigger the code analysis procedure to diagnose comments. Possible values:<br>* `onType` -when editing a file (online) ***on large files can significantly slow down editing ***<br>* `onSave` - when saving a file (*default*)<br>* `never` - analysis will not be performed
`computeDiagnosticsSkipSupport` | `String` | Этим параметром настраивается режим пропуска файлов *(т.е. файлы не анализируются на предмет наличия замечаний)* **конфигурации 1С**, находящихся "на поддержке" конфигурации поставщика. Возможные значения:<br>* `withSupport` - пропускаются все модули, находящиеся "на поддержке" *(все виды "замков")*<br>* `withSupportLocked` - пропускаются только модули, находящиеся "на поддержке" с запретом изменений *("желтый закрытый замок")*<br>* `never` - режим поддержки не анализируется и модули не пропускаются *(установлен по умолчанию)*
`traceLog` | `String` | Для логирования всех запросов *(входящих и исходящих)* между **BSL Language Server** и **Language Client** из используемого редактора/IDE, в этом параметре можно указать путь к файлу лога. Путь можно указывать как абсолютный, так и относительный *(от корня анализируемого проекта)*, по умолчанию значение не заполнено.<br><br>**ВНИМАНИЕ**<br><br>* При запуске **BSL Language Server** перезаписывает указанный файл<br>* Скорость взаимодействия между клиентом и сервером **ЗНАЧИТЕЛЬНО ЗАМЕДЛЯЕТСЯ**
`diagnostics` | `JSON-Object` | Параметр представляет собой коллекцию настроек диагностик. Элементами коллекции являются json-объекты следующей структуры:<br>* *ключ объекта* - строка, являющаяся ключом диагностики<br>* *значение объекта* - может принимать либо булево значение, и тогда интерпретируется как отключение диагностики (`false`) или ее включение с параметрами по умолчанию (`true`), либо значение типа `json-объект`, представляющего собой набор настроек диагностики.<br><br>Ключ, включена ли по умолчанию, а также описание возможных параметров и примеры для конфигурационного файла представлены на странице с описанием каждой диагностики.

You can use the following JSON schema to make it easier to compile and edit a configuration file:

```
https://1c-syntax.github.io/bsl-language-server/configuration/schema.json
```

## Example

The following is an example of a settings:

-  Language of diagnostics messages - English;
- Changes the diagnostic setting for [LineLength - Line Length limit](../diagnostics/LineLength.md), set the limit for the length of a string to 140 characters;
- Disable [MethodSize - Method size restriction{/a0 } diagnostic.](../diagnostics/MethodSize.md)

```json
{
  "$schema": "https://1c-syntax.github.io/bsl-language-server/configuration/schema.json",
  "diagnosticLanguage": "en",
  "diagnostics": {
    "LineLength": {
      "maxLineLength": 140
    },
    "MethodSize": false
  }
}
```
