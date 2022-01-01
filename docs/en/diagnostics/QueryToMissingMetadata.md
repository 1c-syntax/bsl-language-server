# Using non-existent metadata in the query (QueryToMissingMetadata)

|  Type   | Scope | Severity  | Activated by default | Minutes<br> to fix |            Tags             |
|:-------:|:-----:|:---------:|:--------------------:|:------------------------:|:---------------------------:|
| `Error` | `BSL` | `Blocker` |        `Yes`         |           `5`            | `suspicious`<br>`sql` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description

With frequent changes to the metadata model, queries may appear that refer to renamed or deleted metadata. In addition, erroneous table names can occur when manually modifying queries without validation with the query designer.

Executing queries against non-existent metadata will generate a runtime error.

## Examples

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

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
- [Working with queries - 1C development standards (RU)](https://its.1c.ru/db/v8std#browse:13:-1:26:27)
- [Optimization of queries - 1C development standards (RU)](https://its.1c.ru/db/v8std#browse:13:-1:26:28)

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
