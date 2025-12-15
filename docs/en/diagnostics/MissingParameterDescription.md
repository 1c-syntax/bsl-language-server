# Method parameters description are missing (MissingParameterDescription)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The description of a method (procedure or function) should be formatted correctly to help programmers use the functionality correctly.

If a method contains parameters, then in its description, in the block of the same name, descriptions for all parameters must be given in the same order as in the method signature.

Diagnostic detects typical errors:

- Lack of description of all parameters
- Absence of a description of some of the parameters, indicating for which parameter the description was not found
- The presence in the description of parameters that are absent in the method signature (which could remain from refactoring)
- Poor parameter description: when the parameter name is present in the method description, but the parameter type and type description are not specified

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Standart: Procedures and functions description
