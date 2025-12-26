# Using of "FULL OUTER JOIN" in queries (FullOuterJoinQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
You should not use FULL OUTER JOIN in queries, especially in PostgreSQL database. It is better to rewrite query without FULL OUTER JOIN.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Query below will lead to high load on PostgreSQL database.
```bsl
Procedure Test1()

    Query = New Query;
    Query.Text = "SELECT
                   |    Goods.Product AS Product,
                   |    ISNULL(SalesPlan.Sum, 0) AS PlanSum,
                   |    ISNULL(SalesActual.Sum, 0) AS ActualSum
                   |FROM
                   |    Goods AS Goods
                   |        LEFT JOIN SalesPlan AS SalesPlan
                   |            FULL OUTER JOIN SalesActual AS SalesActual // Should trigger here
                   |            ON SalesPlan.Product = SalesActual.Product
                   |        ON Goods.Product = SalesPlan.Product";

EndProcedure
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Ограничение на использование конструкции "ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ" в запросах (RU)](https://its.1c.ru/db/v8std#content:435:hdoc)
* [Руководство администратора, особенности использования PostgreSQL (RU)](https://its.1c.ru/db/metod8dev#content:1556:hdoc)
