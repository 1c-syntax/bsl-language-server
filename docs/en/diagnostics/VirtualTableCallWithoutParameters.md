# Virtual table call without parameters (VirtualTableCallWithoutParameters)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When using virtual tables in queries, you should specify all conditions related to it in the table parameters.

It is not recommended to refer to virtual tables using conditions in the WHERE section, etc.

Such a query will return the correct (in terms of functionality) result, but it will be much more difficult for the DBMS to choose the optimal plan for its execution. In some cases, this can lead to errors in the DBMS optimizer and a significant slowdown in query performance.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
For example, a query uses the `WHERE` section to filter virtual table data:
```bsl
Query.Text = "SELECT
| Good
|FROM
| AccumulationRegister.MyGoods.Turnovers()
|WHERE
| Warehouse = &Warehouse";
```
When executing this query, first all records of the virtual table will be selected, then the part corresponding to the specified condition will be selected from them.

It is recommended that you limit the number of selected records as early as possible. To do this, pass conditions to the parameters of the virtual table.

```bsl
Query.Text = "SELECT
| Good
|FROM
| AccumulationRegister.MyGoods.Turnovers(, Warehouse = &Warehouse)";
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Standard: [Using virtual tables (RU)](https://its.1c.ru/db/v8std#content:657:hdoc)
* Standard: [Effective use of the virtual table «Turnovers» (RU)](https://its.1c.ru/db/v8std#content:733:hdoc)
* 1C Recommendation: [Using the Condition parameter when accessing a virtual table (RU)](https://its.1c.ru/db/metod8dev/content/5457/hdoc) (RU)
