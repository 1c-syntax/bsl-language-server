# Using a logical "OR" in the "WHERE" section of a query (LogicalOrInTheWhereSectionOfQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Не следует использовать `ИЛИ` в секции `ГДЕ` запроса. Это может привести к тому, что СУБД не сможет использовать 
индексы таблиц и будет выполнять сканирование, что увеличит время работы запроса и вероятность возникновения блокировок. 
Вместо этого следует разбить один запрос на несколько и объединить результаты.

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

For example, query:

```bsl
SELECT Goods.Description FROM Catalog.Goods AS Goods 
WHERE Code = "001" OR Cost = 10
```

should instead of a query:

```bsl
SELECT Goods.Description FROM Catalog.Goods AS Goods 
WHERE Code = "001" 

UNION ALL

SELECT Goods.Description FROM Catalog.Goods AS Goods 
WHERE Cost = 10

```

>**Important** - the current implementation of the diagnostic triggers on any `OR` in the `WHERE` section and may issue false positives for some conditions.

1) In the main condition, the `OR` operator can only be used for the last used or the only index field, when the `OR` operator can be replaced by the `IN` operator.

Correct:

```bsl
WHERE
    Table.Filed = &Value1
    OR Table.Filed = &Value2
```

because can be rewritten using the `IN` operator (you don’t need to specifically rewrite it, you can leave it as it is):

```bsl
WHERE
    Table.Field IN (&Value)
```

Incorrect:

```bsl
WHERE
    Table.Field1 = &Value1
    OR  Table.Field2 = &Value2
```

cannot be rewritten with `IN`, but can be rewritten with `UNION ALL` (each Field1 and Field2 must be indexed):

```bsl
WHERE
     Table.Field1 = &Value1

UNION ALL

WHERE
     Table.Field2 = &Value1
```

>Note: it is not always possible to replace `OR` with `UNION ALL`, make sure the result is really the same as with `OR` before applying.

2) Additionally, the 'OR' operator can be used without restriction.

Correct:

```bsl
WHERE
    Table.Filed1 = &Value1 // Main condition (use index)
    AND // Addition condition (can use OR)
    (Table.Filed2 = &Value2 OR Table.Filed3 = &Value3)
```

Correct:

```bsl
WHERE
    (Table.Filed1 = &Value1 OR Table.Filed1 = &Value2)
    AND
    (Table.Filed2 = &Value3 OR Table.Filed2 = &Value4)
```

because can be rewritten using 'IN' (no special rewriting needed, can be left as is):

```bsl
WHERE
    Table.Field1 IN (&Value1)   // Main condition
    AND Table.Field2 IN (&Value2) // Additional condition (or vice versa)
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [Standard: Effective Query Conditions, Clause 2 (RU)](https://its.1c.ru/db/v8std/content/658/hdoc)
- [Typical Causes of Suboptimal Query Performance and Optimization Techniques: Using Logical OR in Conditions (RU)](https://its.1c.ru/db/content/metod8dev/src/developers/scalability/standards/i8105842.htm#or)
- [Article on Habr: Interesting analysis of SQL queries in various DBMS (not about 1C) (RU)](https://m.habr.com/ru/company/lsfusion/blog/463095/)
