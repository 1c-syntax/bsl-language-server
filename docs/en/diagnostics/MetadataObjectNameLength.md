# Metadata object names must not exceed the allowed length (MetadataObjectNameLength)

|  Type   | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:-------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Error` | `BSL` | `Major`  |             `Yes`             |          `10`           | `standard` |

## Parameters 


|             Name              |   Type    |                         Description                          | Default value |
|:-----------------------------:|:---------:|:------------------------------------------------------------:|:-------------:|
| `maxMetadataObjectNameLength` | `Integer` | `Permissible length of the name of the configuration object` |     `80`      |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Metadata object names must not exceed 80 characters.

In addition to problems using these objects, there are problems with uploading the configuration to files.

## Examples

LooooooooooooooooooooooooooooooooooooooooooooooooooooooooongVeryLongDocumentName

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Name, synonym, comment (RU)](https://its.1c.ru/db/v8std#content:474:hdoc:2.3)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MetadataObjectNameLength-off
// BSLLS:MetadataObjectNameLength-on
```

### Parameter for config

```json
"MetadataObjectNameLength": {
    "maxMetadataObjectNameLength": 80
}
```
