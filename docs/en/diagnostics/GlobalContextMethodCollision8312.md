# Global context method names collision (GlobalContextMethodCollision8312)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The platform version `8.3.12` implements new methods of the global context, which may coincide with the configuration functions of the application solution.

|      Russian variant       |  English variant  |
|:--------------------------:|:-----------------:|
|        ПроверитьБит        |     CheckBit      |
|  ПроверитьПоБитовойМаске   |  CheckByBitMask   |
|       УстановитьБит        |      SetBit       |
|         ПобитовоеИ         |    BitwiseAnd     |
|        ПобитовоеИли        |     BitwiseOr     |
|        ПобитовоеНе         |    BitwiseNot     |
|        ПобитовоеИНе        |   BitwiseAndNot   |
| ПобитовоеИсключительноеИли |    BitwiseXor     |
|    ПобитовыйСдвигВлево     | BitwiseShiftLeft  |
|    ПобитовыйСдвигВправо    | BitwiseShiftRight |

The configuration functions must either be renamed or deleted, replacing the call to them with the methods of the global context.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Source: [Migration configuration to the "1C: Enterprise 8.3" platform without the compatibility mode with version 8.2 ](https://its.1c.ru/db/metod8dev#content:5293:hdoc:pereimenovaniya_metodov_i_svojstv)
