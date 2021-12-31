# Обращение к несуществующим метаданным в запросе (QueryToMissingMetadata)

|  Type   | Scope | Severity  | Activated by default | Minutes<br> to fix |            Tags             |
|:-------:|:-----:|:---------:|:--------------------:|:------------------------:|:---------------------------:|
| `Error` | `BSL` | `Blocker` |        `Yes`         |           `5`            | `suspicious`<br>`sql` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description

При активной разработке и изменения модели метаданных могут появляться запросы, в которых идет обращение к переименованным или удалили метаданным. Также ошибочные имена таблиц могут возникать при ручном изменения запросов, без проверки с помощью конструктора запросов.

При выполнении запросов к несуществующим метаданным будет возникать ошибка исполнения.

## Examples

Запрос к уже удаленному регистру:
```sdbl
SELECT
    Table.Field1 AS Field1
FROM
    InformationRegister.InfoRegOld AS Table
```
Запрос с соединением к переименованному регистру:
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
- [Работа с запросами - стандарты разработки 1С](https://its.1c.ru/db/v8std#browse:13:-1:26:27)
- [Оптимизация запросов - стандарты разработки 1С](https://its.1c.ru/db/v8std#browse:13:-1:26:28)

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
