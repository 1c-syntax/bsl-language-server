# Using non-existent metadata in the query (QueryToMissingMetadata)

|   Type    |    Scope    |   Severity    |    Activated<br>by default    |    Minutes<br>to fix    |            Tags             |
|:--------:|:-----------------------------:|:-------------:|:------------------------------:|:-----------------------------------:|:---------------------------:|
| `Error` |             `BSL`             | `Blocker` |              `Yes`              |                 `5`                 |    `suspicious`<br>`sql`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Due to frequent changes to the metadata model, queries may appear that refer to renamed or deleted metadata.
Also, errors can occur when you manually change queries, without checking with the query designer.

When accessing non-existent metadata in a query, a runtime error will occur.

## Scope

Query for an already deleted register:
```sdbl
SELECT
    Table.Field1 AS Field1
FROM
    InformationRegister.InfoRegOld AS Table
```
Query with a join to the renamed register:
```sdbl
SELECT
    Table.Field1 AS Field1
FROM
     InformationRegister.InfoReg AS Table 
     INNER JOIN InformationRegister.InfoRegOld AS FilterTable
     ON FilterTable.Field2 = Table.Field2
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
- [Development standards. Working with queries (RU)](https://its.1c.ru/db/v8std#browse:13:-1:26:27)
- [Development standards. Optimizing queries (RU)](https://its.1c.ru/db/v8std#browse:13:-1:26:28)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:QueryToMissingMetadata-off
// BSLLS:QueryToMissingMetadata-on
```

### Parameter for config

```json
"QueryToMissingMetadata": false
```
