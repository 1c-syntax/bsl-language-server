# Mixing Latin and Cyrillic characters in one identifier (LatinAndCyrillicSymbolInWord)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Do not use identifiers consisting of characters from different languages, вecause it makes it difficult to use them further, forcing to switch the layout.  
Also, the diagnostics detects the erroneous use of characters from another language, when it was used unintentionally. For exaple: `o`, `c`, `B`, `p` and etc.

To reduce "noise" in the names consisting of several words beginning or ending in the word in another language, in the diagnostics option has been added that is included by default.  
If the parameter is enabled, then **NOT** are considered erroneous names like `ZebraДрайвер`, `КодHTTP`, `SMSШлюз` and the like.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
