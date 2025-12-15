# Overuse "Reference" in a query (RefOveruse)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Using ".Ref" to a field of a reference type will result in an implicit left join with the source table of this field, and it has no value, but only generates excessive load on the DBMS.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
Query.Text = "Select Files.File.Ref, // error
   | Files.File
   | From
   | InternalFiles AS Files";
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* Useful Information: [Dereferencing Complex Type Reference Fields in Query Language](https://its.1c.ru/db/v8std/content/654/hdoc)
