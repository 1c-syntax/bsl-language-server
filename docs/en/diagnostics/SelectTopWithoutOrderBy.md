# Using 'SELECT TOP' without 'ORDER BY' (SelectTopWithoutOrderBy)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Using the `TOP N` construct without specifying the sort order in `ORDER BY` or conditions in the `WHERE` section is fraught with unexpected results:

- The order of the returned results may differ in different DBMSs
- The order in different copies of information security will differ from the order expected by the developer

According to the standard, the absence of the sentence `ORDER BY` is justified only in cases where

- the algorithm for processing query results does not rely on a specific order of records
- the result of processing the executed request is not shown to the user
- query result - obviously one record

In the above cases, it is recommended not to add the clause `ORDER BY` to the request body, as this leads to additional time-consuming execution of the request.

### Diagnostic ignorance in code

During the analysis, constructions are considered erroneous:

- Using `TOP N` in the union regardless of the presence of `ORDER BY` because ordering occurs after the union
- Using `TOP N` where `N> 1` if missing `ORDER BY`
- Using `TOP 1`, if there is no `ORDER BY` and conditions in `WHERE`. This rule is disabled by default by a diagnostic option

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
SELECT TOP 1 // < - No error, there is a condition
   Reference.Link
OF
   Directory.Contractors AS Directory
WHERE
   Reference.Ref. IN (
       SELECT TOP 10 // < - Error, no sorting
           Link
       OF
           Reference, Contractors)

UNION ALL

SELECT TOP 10 // < - Error, no sorting (and cannot be)
   Reference.Link
OF
   Directory.Contractors AS Directory

UNION ALL

SELECT TOP 1 // < - Always error, even 1
   Reference.Link
OF
   Directory.Contractors AS Directory

SORT BY
   Link
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Standard: Ordering Query Results (RU)](https://its.1c.ru/db/v8std#content:412:hdoc)
