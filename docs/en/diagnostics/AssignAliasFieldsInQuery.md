# Assigning aliases to selected fields in a query (AssignAliasFieldsInQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It is recommended to specify optional query constructs, first, to explicitly assign aliases to fields in order to increase the clarity of the query text and the "steadiness" of the code that uses it.  
For example, if the algorithm uses a query with a field declared as

```bsl
CashBox.Currency
```
when changing the name of the attribute, you will also need to change the code that calls the selection from the query result by the name of the Currency property. If the field is declared as

```bsl
CashBox.Currency As Currency
```
then changing the attribute name will only change the request text.

You should be especially careful about automatically assigned aliases for fields - attributes of other fields, such as "... CashBox.Currency.Name...". In the above example, the field will be automatically getting aliased CurrencyName, but not Name.

Be sure to include the AS keyword before the alias of the source field.

The aliases of tables and fields from secondary queries from "UNION" are not checked by the diagnostics.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl   
    Query = New Query;
Query.Text =
"SELECT
|   Currencies.Ref, // Incorrectly
|   Currencies.Ref AS AliasFieldsRef, // Correctly
|   Currencies.Code Code // Incorrectly
|FROM
|   Catalog.Currencies AS Currencies // Ignored
|
|UNION ALL
|
|SELECT
|   Currencies.Ref, // Ignored
|   Currencies.Ref, // Ignored
|   Currencies.Code // Ignored
|FROM
|   Catalog.Currencies AS Currencies // Ignored
|;
|
|////////////////////////////////////////////////////////////////////////////////
|SELECT
|   Currencies.Ref, // Incorrectly
|   Currencies.Ref AS AliasFieldsRef, // Correctly
|   Currencies.Code Code // Incorrectly
|FROM
|   Catalog.Currencies AS Currencies // Ignored
|
|UNION ALL
|
|SELECT
|   Currencies.Ref, // Ignored
|   Currencies.Ref, // Ignored
|   Currencies.Code // Ignored
|FROM
|   Catalog.Currencies AS Currencies"; // Ignored

Query1 = New Query;
Query1.Text =
"SELECT
|   NestedRequest.Ref AS Ref // Correctly
|FROM
|   (SELECT
|       Currencies.Ref // Incorrectly
|   FROM
|       Catalog.Currencies AS Currencies) AS NestedRequest"; // Ignored 
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
Source: [Making query text](https://its.1c.ru/db/v8std#content:437:hdoc)
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
