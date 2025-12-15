# Scheduled job handler (ScheduledJobHandler)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Certain requirements are imposed on the methods of scheduled job handlers.
Any export procedure or function of a non-global common server module can be used as a scheduled job method. If the scheduled job method is a function, then its return value is ignored.

If the scheduled job is predefined, then its handler should not have parameters.
Otherwise, the parameters of such a scheduled job can be any values ​​that are allowed to be sent to the server. The parameters of a scheduled job must exactly match the parameters of the procedure or function it calls.

Diagnostics checks the following signs of the correctness of the scheduled job handler method:

- there is a common module and a shared module method specified as a handler
- common module is server
- the method is export
- the method has no parameters if the scheduled job is predefined
- method body is not empty
- there are no other scheduled jobs that refer to the same handler method

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Set of articles "Scheduled job" - standard 1C (RU)](https://its.1c.ru/db/v8std#browse:13:-1:1:6)
- [Article "Scheduled job" from the developer's guide 1C 8.3 (RU)](https://its.1c.ru/db/v8322doc#bookmark:dev:TI000000794)
