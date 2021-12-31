# Deprecated 8.3.12 platform features. (DeprecatedAttributes8312)

|     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |     Tags     |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `deprecated` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
The following items are deprecated and their use is not recommended since platform version 8.3.12:

* Для системного перечисления `ГруппировкаПодчиненныхЭлементовФормы` реализовано значение `ГоризонтальнаяВсегда`, значение `ГруппировкаПодчиненныхЭлементовФормы.Горизонтальная` считается устаревшим
* `ChartLabelsOrientation` system enum is no longer available. Actual variant is `ChartLabelsOrientation`
* The following properties and methods of Chart object are obsolete and not recommended for use:
   * `ПалитраЦветов`;
   * `ЦветНачалаГрадиентнойПалитры`;
   * `ЦветКонцаГрадиентнойПалитры`;
   * `GradientPaletteMaxColors`;
   * `ПолучитьПалитру()`;
   * `УстановитьПалитру()`.

* Names of properties of the object `ChartPlotArea`:
   * `ShowScale`
   * `ScaleLines`
   * `ScaleColor`

* Следующие свойства объекта `ОбластьПостроенияДиаграммы` являются устаревшими, не рекомендуются для использования и поддерживаются для совместимости:
   * `ОтображатьПодписиШкалыСерии` - рекомендуется использовать `ШкалаСерий.ПоложениеПодписейШкалы`
   * `ОтображатьПодписиШкалыТочек` - рекомендуется использовать `ШкалаТочек.ПоложениеПодписейШкалы`
   * `ОтображатьПодписиШкалыЗначений` - рекомендуется использовать `ШкалаЗначений.ПоложениеПодписейШкалы`
   * `ОтображатьЛинииЗначенийШкалы` - рекомендуется использовать `ШкалаЗначений.ОтображениеЛинийСетки`
   * `ФорматШкалыЗначений` - рекомендуется использовать `ШкалаЗначений.ФорматПодписей`
   * `ОриентацияМеток` - доступа рекомендуется использовать `ШкалаТочек.ОриентацияПодписей`

* Свойства `ОтображатьЛегенду` и `ОтображатьЗаголовок` объектов `Диаграмма`, `ДиаграммаГанта`, `СводнаяДиаграмма` являются устаревшими и не рекомендуются для использования
* Global context method `ClearEventLog()` is deprecated and only applicable to logs that have `SQLite` format

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Platform 8.3.12 changelog](https://dl04.1c.ru/content/Platform/8_3_12_1714/1cv8upd_8_3_12_1714.htm)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedAttributes8312-off
// BSLLS:DeprecatedAttributes8312-on
```

### Parameter for config

```json
"DeprecatedAttributes8312": false
```
