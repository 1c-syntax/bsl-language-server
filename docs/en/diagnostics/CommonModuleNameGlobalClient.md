# Global module with postfix "Client" (CommonModuleNameGlobalClient)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

For global modules, the "Global" (rus. "Глобальный") postfix is added , "Client" postfix should not be added.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:
InfobaseUpdateGlobalClient

Correct: 
InfobaseUpdateGlobal

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->



[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:3.2.1)
