# Assigning aliases to selected fields in a query (AssignAliasFieldsInQuery)

|     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                       Tags                       |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------------------------:|
| `Code smell` | `BSL` | `Важный` |             `Yes`             |           `1`           | `standard`<br>`sql`<br>`badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Рекомендуется указывать и необязательные конструкции запроса, прежде всего - явно назначать псевдонимы полям, в целях повышения наглядности текста запроса и "устойчивости" использующего его кода. Например, если в алгоритме используется запрос с полем, объявленным как

```bsl
CashBox.Currency
```
при изменении имени реквизита нужно будет также изменить и код, осуществляющий обращение по имени свойства Валюта к выборке из результата запроса. If the field is declared as
```bsl
CashBox.Currency As Currency
```
then changing the attribute name will only change the request text.

Особенно внимательно следует относиться к автоматически присваиваемым псевдонимам для полей – реквизитов других полей, типа "... Касса.Валюта.Наименование...". В приведенном выше примере поле получит автоматический псевдоним ВалютаНаименование, а не Наименование.

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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:AssignAliasFieldsInQuery-off
// BSLLS:AssignAliasFieldsInQuery-on
```

### Parameter for config

```json
"AssignAliasFieldsInQuery": false
```
