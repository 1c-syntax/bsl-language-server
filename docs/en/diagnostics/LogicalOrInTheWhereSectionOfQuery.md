# Using a logical "OR" in the "WHERE" section of a query (LogicalOrInTheWhereSectionOfQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Do not use `OR` in the `WHERE` section of the query. Это может привести к тому, что СУБД не сможет использовать индексы таблиц и будет выполнять сканирование, что увеличит время работы запроса и вероятность возникновения блокировок. Instead, you should split one query into several and combine the results.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

For example, query:

```bsl
SELECT Item.Name FROM Directory.Products AS Item
WHERE Article = "001" OR Price = 10
```

should instead of a query:

```bsl
SELECT Product.Name FROM Directory.Products AS Product WHERE Article = "001"
UNION ALL
SELECT Product.Name FROM Directory.Products AS Product WHERE Price = 10
```
> **Important** - the current diagnostic implementation triggers any `OR` in the `WHERE` section and may give false positives for some conditions.

1) In the main condition, the `OR` operator can be used only for the last used or the only index field, when the `OR` operator can be replaced with the `IN` operator.

Correct:

```bsl
WHERE
     Table.Field = &Value1
     OR Table.Field = &Value2
```

since can be rewritten using the `IN` operator (you don't need to rewrite it specifically, you can leave it as it is):

```bsl
WHERE
    Table.Field IN (&Value)
```

Incorrect:

```bsl
WHERE
     Table.Field1 = &Value1
     OR Table.Field2 = &Value2
```

cannot be overwritten with `IN`, but can be overwritten with `UNION ALL` (each field Field1 and Field2 must be indexed):

```bsl
WHERE
    Table.Field1 = &Value1

ОБЪЕДИНИТЬ ВСЕ

WHERE
    Table.Field2 = &Value1
```
> Note: replacing `OR` with `UNION ALL` is not always possible, make sure the result is indeed the same as `OR` before use.

2) In an additional condition, the OR operator can be used without restrictions.

Correct:

```bsl
WHERE
     Table.Field1 = &Value1 // Main condition (uses index)
     AND // Additional condition (you can use OR)
     (Table.Field2 = &Value2 OR Table.Field3 = &Value3)
```

Correct:

```bsl
WHERE
     (Table.Field1 = &Value1 OR Table.Field1 = &Value2)
     AND
     (Table.Field2 = &Value3 OR Table.Field2 = &Value4)
```

since can be rewritten using the IN operator (you don't need to rewrite it specifically, you can leave it as it is):

```bsl
WHERE
     Table.Field1 B (&Values1) // Main condition
     AND Table.Field2 B (&Values2) // Additional condition (or vice versa)
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [Standard - Effective Query Conditions, Clause 2](https://its.1c.ru/db/v8std/content/658/hdoc)
- [Using Logical OR in Conditions - Typical Causes of Suboptimal Query Performance and Optimization Techniques](https://its.1c.ru/db/content/metod8dev/src/developers/scalability/standards/i8105842.htm#or)
- [Interesting analysis of SQL queries in various DBMS (not about 1C) - Article on Habr](https://m.habr.com/ru/company/lsfusion/blog/463095/)
