# Using of deprecated platform 8.3.17 global methods (DeprecatedMethods8317)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In the global context of the `8.3.17` platform, created `ErrorProcessing` properties and `error management text parameters` that allow you to customize error texts.

Global context methods are deprecated:

* `BriefErrorDescription()`
* `DetailErrorDescription()`
* `ShowErrorInfo()`

You should use the same methods of the `ErrorProcessing` object instead.

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Platform 8.3.17 changelog](https://dl03.1c.ru/content/Platform/8_3_17_1386/1cv8upd_8_3_17_1386.htm#27f2dc70-f0cf-11e9-8371-0050569f678a)
