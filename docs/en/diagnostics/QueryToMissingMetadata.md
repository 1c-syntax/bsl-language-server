# Using non-existent metadata in the query (QueryToMissingMetadata)

|   Type    |    Scope    |   Severity    |    Activated<br>by default    |    Minutes<br>to fix    |            Tags             |
|:--------:|:-----------------------------:|:-------------:|:------------------------------:|:-----------------------------------:|:---------------------------:|
| `Error` |             `BSL`             | `Blocker` |              `Yes`              |                 `5`                 |    `suspicious`<br>`sql`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Using non-existent metadata in the query (QueryToMissingMetadata)

Type

## Scope

Severity
Activated by default
Minutes<br> to fix
Tags

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
- `suspicious`<br>`sql`
- <!-- Блоки выше заполняются автоматически, не трогать -->

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:QueryToMissingMetadata-off
// BSLLS:QueryToMissingMetadata-on
```

### Query for an already deleted register:

```sdbl
SELECT
    Table.Field1 AS Field1
FROM
    InformationRegister.InfoRegOld AS Table
```
