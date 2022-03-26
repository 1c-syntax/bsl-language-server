# Missed postfix "ServerCall" (CommonModuleNameServerCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Common server modules for calling from the client contain server procedures and functions available from the client code. They constitute the client interface of the application server. Such procedures and functions are placed in common modules with the following property:

* Server (ServerCall is enabled)

Name common server modules to be called from the client according to general rules of naming metadata objects. Make sure they include the "ServerCall" (rus. "ВызовСервера") postfix.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesServerCall, CommonServerCall

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:2.2)
