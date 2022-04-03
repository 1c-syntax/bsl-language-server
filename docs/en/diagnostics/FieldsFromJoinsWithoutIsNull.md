# No NULL checks for fields from joined tables (FieldsFromJoinsWithoutIsNull)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Diagnostics checks fields from left, right, full joins that are not checked with `ISNULL()` or `IS NOT NULL`  or `NOT IS NULL` .

Queries cannot use attributes from left-join or right-join tables without checking the values for `NULL`. 
Such a call can lead to errors if the join condition is not met and there are no matching records in the left or right table. 
As a result, as a result of executing the query, you may receive unexpected data and the system may behave in an incorrect way.

It is important to remember that any comparison of the value `NULL` with any other expression is always false, even the comparison of `NULL` and `NULL` is always false. 
The following are examples of such incorrect comparisons. 
It is correct to compare with `NULL` - operator `IS NULL` or function `ISNULL()`.

Left \ right joins are often used, although the data allows an inner join without checking for `NULL`.

Additional checks of field values can be performed in the 1C code, and not in the query text. This makes it difficult to read the code and refactor the code, because the context of the access to the field has to be considered in several places. 
It should be remembered that simple checks in a query are performed a little faster and easier than in interpreted 1C code.

These problems are the most common mistakes made by 1C developers of all skill levels.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Example showing NULL comparison problems - joining 2 tables incorrectly and showing different comparison methods
```sdbl
SELECT
  CASE
    WHEN LeftTable.Fld2 = 0 THEN "Equals 0 - does not work"
    WHEN LeftTable.Fld2 <> 0 THEN "NOT Equals 0 - does not work"
    WHEN LeftTable.Fld2 = NULL THEN "Equals NULL - does not work"
    WHEN LeftTable.Fld2 IS NULL THEN "IS NULL - it works"
    WHEN ISNULL(LeftTable.Fld2, 0) = 0  THEN "ISNULL() - and this works too"
    ELSE "else"
  END
ИЗ
  First AS First
  LEFT JOIN LeftTable AS LeftTable
  ON FALSE
```

Suspicious code for accessing an joined table attribute
```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  РегистрПродажи.Сумма КАК Сумма // здесь ошибка
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
Correct
```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  ЕстьNULL(РегистрПродажи.Сумма, 0) КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
Also correct:
```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  ВЫБОР КОГДА РегистрПродажи.Сумма Есть NULL ТОГДА 0
  ИНАЧЕ  РегистрПродажи.Сумма 
  КОНЕЦ КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
Possible variant
```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  РегистрПродажи.Сумма КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
ГДЕ
    РегистрПродажи.Документ ЕСТЬ НЕ NULL
    //или НЕ РегистрПродажи.Документ ЕСТЬ NULL
```
The last one is not the best, because it actually emulates an inner join.
It is more correct to explicitly specify `INNER JOIN` instead of using a left join with a `IS NOT NULL` or `NOT IS NULL` check

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [Standard: Using the ISNULL function (RU)](https://its.1c.ru/db/metod8dev/content/2653/hdoc)
* [Guidelines: The concept of "empty" values (RU)](https://its.1c.ru/db/metod8dev/content/2614/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
    * [Guidelines: What is the difference between a value of type Undefined and a value of type Null? (RU)](https://its.1c.ru/db/metod8dev#content:2516:hdoc)
* [Methodical recommendations: Peculiarities of communication with the virtual table of residuals (RU)](https://its.1c.ru/db/metod8dev/content/2657/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Standard: Sorting by query field that can potentially contain NULL. The article "Ordering query results" (RU)](https://its.1c.ru/db/v8std/content/412/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Methodological recommendations: Fields of a hierarchical directory can contain NULL (RU)](https://its.1c.ru/db/metod8dev/content/2649/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
    * [Guidelines: How to get the records of a hierarchical table and arrange them in the order of the hierarchy (RU)](https://its.1c.ru/db/pubqlang/content/27/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Online book "1C: Enterprise Query Language": How to get data from different tables for the same field (RU)](https://its.1c.ru/db/pubqlang#content:43:hdoc)
