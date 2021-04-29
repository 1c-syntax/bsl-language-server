# Join with virtual table (JoinWithVirtualTable)

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                       Tags                       |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------------------------:|
 | `Code smell` | `BSL` | `Major`  |             `Yes`             |          `10`           | `sql`<br>`standard`<br>`performance` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

When writing queries, you should not use virtual tables joins. Only metadata objects or temporary tables should be joined to each other.

If the query uses a join to a virtual table (for example, AccumulationRegister.Products.Balance) and the query is slow, then it is recommended to move the data reading from the virtual table into a separate query with the results saved in a temporary table.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Standard: Restrictions on SubQuery and Virtual Table Joins (RU)](https://its.1c.ru/db/v8std#content:655:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:JoinWithVirtualTable-off
// BSLLS:JoinWithVirtualTable-on
```

### Parameter for config

```json
"JoinWithVirtualTable": false
```
