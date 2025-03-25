# Missed postfix "Client" (CommonModuleNameClient)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Client common modules contain client business logic (functionality specific to the client only) and have the following features:

* Client (Managed application)
* Client (Ordinary application)

In cases where client methods should be available only in managed application mode (or only in regular application mode or only in external connection mode), a different combination of these two features is allowed.

Client common modules are named with the "Client" postfix ( "Клиент" in Rus). Except when the Global flag is on.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesClient, CommonClient, StandardSubsystemsClient

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:2.3)
