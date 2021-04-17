# Global context method names collision (GlobalContextMethodCollision8312)

 |  Type   |        Scope        | Severity  | Activated<br>by default | Minutes<br>to fix |               Tags               |
 |:-------:|:-------------------:|:---------:|:-----------------------------:|:-----------------------:|:--------------------------------:|
 | `Error` | `BSL`<br>`OS` | `Blocker` |             `Yes`             |          `10`           | `error`<br>`unpredictable` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Начиная с версии платформы `8.3.12`, реализованы новые методы глобального контекста, которые могут совпаcть по имени с существующими функциями в коде конфигурации прикладного решений.

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

Необходимо существующие функции конфигурации прикладного решения необходимо либо переименовать, либо удалить, заменив обращение к ним на методы глобального контекста.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Источник: [Перевод конфигураций на платформу "1С:Предприятие 8.3" без режима совместимости с версией 8.2](https://its.1c.ru/db/metod8dev#content:5293:hdoc:pereimenovaniya_metodov_i_svojstv)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:GlobalContextMethodCollision8312-off
// BSLLS:GlobalContextMethodCollision8312-on
```

### Parameter for config

```json
"GlobalContextMethodCollision8312": false
```
