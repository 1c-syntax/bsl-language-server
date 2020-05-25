# Metadata object names must not exceed 80 characters (ObjectNameLength)

<Metadata>

## <Params>

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Metadata object names must not exceed 80 characters.

In addition to problems using these objects, there are problems with uploading the configuration to files.

## Examples

ОченьДлинноеИмяСправочникиКотороеВызываетПроблемыВРаботеАТакжеОшибкиВыгрузкиКонфигурации, LooooooooooooooooooooooooooooooooooooooooooooooooooooooooongVeryLongDocumentName

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Name, synonym, comment (RU)](https://its.1c.ru/db/v8std#content:474:hdoc:2.3)

## Snippets
<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:ObjectNameLength-off
// BSLLS:ObjectNameLength-on
```

### Parameter for config

```json
"ObjectNameLength": {
    "maxObjectNameLength": 80
}
```
