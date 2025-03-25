# Join with sub queries (JoinWithSubQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

When writing queries, you should not use subquery joins. Only metadata objects or temporary tables should be joined to each other.

If the query contains joins with subqueries, then this can lead to negative consequences:

- Very slow query execution with low load on server hardware
- Unstable work of the request. Sometimes the query can work fast enough, sometimes very slow
- Significant difference in query execution time for different DBMS
- Increased query sensitivity to the relevance and completeness of sql statistics. After a complete update of statistics, the query may work quickly, but after a while it will slow down

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

An example of a potentially dangerous query using a subquery join:

```bsl
SELECT *
FROM Document.Sales
LEFT JOIN (
   SELECT Field1 ИЗ InformationRegister.Limits
   WHERE Field2 In (&List)
   GROUP BY
   Field1
) BY Refs = Field1
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Standard: Restrictions on SubQuery and Virtual Table Joins (RU)](https://its.1c.ru/db/v8std#content:655:hdoc)
