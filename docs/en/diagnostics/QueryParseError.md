# Query text parsing error (QueryParseError)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

When writing queries, you must follow the following rule: the query text must be opened by the query designer.

This rule allows you to quickly check the correctness of the syntax of the query, as well as revision and maintenance.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect

```bsl
Text = "SELECT
| Goods.Name  AS Name ,
| Goods. " + FieldNameCode + " AS Code
|From
| Catalog.Goods КАК Goods";
```

Right

```bsl
Text = "SELECT
| Goods.Name AS Name,
| &FieldNameCode AS Code
|FROM
| Catelog.Goods AS Goods";

Text = StrReplace(Text, 
                            "&FieldNameCode", 
                            "Goods." + FieldNameCode);
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Standard: Working with queries (RU). Formatting queries texts](https://its.1c.ru/db/v8std#content:437:hdoc)
