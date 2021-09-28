# Metadata borders (MetadataBorders)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |   Tags   |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------:|
| `Code smell` |    `BSL`<br>`OS`    |  `Info`  |             `No`              |           `1`           | `design` |

## Parameters


|            Name             |   Type   |                                 Description                                 | Default value |
|:---------------------------:|:--------:|:---------------------------------------------------------------------------:|:-------------:|
| `metadataBordersParameters` | `String` | `JSON-structure for pairs "regex for statements":"regex for module names".` |      ``       |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MetadataBorders-off
// BSLLS:MetadataBorders-on
```

### Parameter for config

```json
"MetadataBorders": {
    "metadataBordersParameters": ""
}
```
