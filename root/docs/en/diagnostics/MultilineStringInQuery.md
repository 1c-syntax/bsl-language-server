# Multi-line literal in query (MultilineStringInQuery)

|  Type   | Scope |  Severity  |    Activated<br>by default    |    Minutes<br>to fix    |                             Tags                             |
|:-------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:------------------------------------------------------------:|
| `Error` | `BSL` | `Critical` |             `Yes`             |           `1`           |       `badpractice`<br>`suspicious`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Multi-line literals are rarely used in query texts, mostly these are error results due to an incorrect number of double quotes.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

In the example below, the selection will have two fields instead of three.

```bsl
Query = New Query;
Query.Text = "SELECT
|   OrderGoods.Cargo AS Cargo,
|   ISNULL(OrderGoods.Cargo.Code, "") AS CargoCode,
|   ISNULL(OrderGoods.Cargo.Name, "") AS CargoName
|FROM
|   Document.Order.Goods AS OrderGoods
|WHERE
|   OrderGoods.Ref = &Ref";
```

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
// BSLLS:MultilineStringInQuery-off
// BSLLS:MultilineStringInQuery-on
```

### Parameter for config

```json
"MultilineStringInQuery": false
```
