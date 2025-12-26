# Using external code tools (UsingExternalCodeTools)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

For application solutions it is forbidden to execute in unsafe mode any code on the 1C:Enterprise server that is not part of the application solution (configuration) itself.  
The restriction does not apply to the code that has passed the audit, and to the code executed on the client.

Examples of invalid execution of "external" code in unsafe mode:

* external reports and processings (print forms, etc.)
* configuration extensions

### Diagnostic ignorance in code

At the moment, the server context is not analyzed, so diagnostic works both at client and server contexts

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Restriction on the execution of "external" code](https://its.1c.ru/db/v8std#content:669:hdoc)
