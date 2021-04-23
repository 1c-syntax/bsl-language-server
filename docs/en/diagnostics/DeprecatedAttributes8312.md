# Platform 8.3.12 changelog

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |     Tags     |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------:|
 | `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `deprecated` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
The following items are deprecated and their use is not recommended since platform version 8.3.12:
* Для системного перечисления ГруппировкаПодчиненныхЭлементовФормы реализовано значение ГоризонтальнаяВсегда. Значение системного перечисления ГруппировкаПодчиненныхЭлементовФормы.Горизонтальная считается устаревшим;
* ChartLabelsOrientation system enum is no longer available. Actual variant is ChartLabelsOrientation;
* The following properties and methods of Chart object are obsolete and not recommended for use:
   * ColorPalette
   * GradientPaletteStartColor
   * GradientPaletteEndColor
   * GradientPaletteMaxColors
   * GetPalette
   * SetPalette
* Names of properties of the object ChartPlotArea:
   * ShowScale
   * ScaleLines
   * ScaleColor
* Properties of FullTextSearchManager object are obsolete, not recomended for use and supported only for backward compatibility:

   * ОтображатьПодписиШкалыСерии. Для доступа рекомендуется использовать ШкалаСерий.ПоложениеПодписейШкалы;
   * ОтображатьПодписиШкалыТочек. Для доступа рекомендуется использовать ШкалаТочек.ПоложениеПодписейШкалы;
   * ОтображатьПодписиШкалыЗначений. Для доступа рекомендуется использовать ШкалаЗначений.ПоложениеПодписейШкалы;
   * ShowPointsScaleLabels. Recommended to use the PointsScale.ScaleLabelLocation;
   * ФорматШкалыЗначений. Для доступа рекомендуется использовать ШкалаЗначений.ФорматПодписей;
   * ОриентацияМеток. Для доступа рекомендуется использовать ШкалаТочек.ОриентацияПодписей.
* Property ShowLegend of Chart, GanttChart, PivotChart objects are obsolete and not recomended for use.
* Property ShowTitle of Chart, GanttChart, PivotChart objects are obsolete and not recomended for use.
* Global context method ClearEventLog() is deprecated and only applicable to logs that have SQLite format.

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
