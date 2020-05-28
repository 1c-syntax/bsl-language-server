# Deprecated 8.3.12 platform features. (DeprecatedAttributes8312)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Info` | `Yes` | `1` | `deprecated`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The following items are deprecated and their use is not recommended since platform version 8.3.12:

- Для системного перечисления ГруппировкаПодчиненныхЭлементовФормы реализовано значение ГоризонтальнаяВсегда. Значение системного перечисления ГруппировкаПодчиненныхЭлементовФормы.Горизонтальная считается устаревшим;

- Системное перечисление ОриентацияМетокДиаграммы более не доступно. Актуальный вариант ОриентацияПодписейДиаграммы;

- The following properties and methods of Chart object are obsolete and not recommended for use:

    - ColorPalette
    - GradientPaletteStartColor
    - GradientPaletteEndColor
    - GradientPaletteMaxColors
    - GetPalette
    - SetPalette

- Names of properties of the object ChartPlotArea:

    - ShowScale
    - ScaleLines
    - ScaleColor

- Свойства объекта ОбластьПостроенияДиаграммы являются устаревшими, не рекомендуются для использования и поддерживаются для совместимости:

    - ShowSeriesScaleLabels - recommended to use the SeriesScale.ScaleLabelLocation
    - ShowPointsScaleLabels- recommended to use the PointsScale.ScaleLabelLocation
    - ShowValuesScaleLabels - recommended to use the ValuesScale.ScaleLabelLocation
    - ShowScaleValueLines - recommended to use the ValuesScale.ОтображениеЛинийСетки
    - ValueScaleFormat - recommended to use the ValuesScale.LabelFormat
    - LabelsOrientation - recommended to use the PointsScale.LabelOrientation

- Свойство ОтображатьЛегенду объектов Диаграмма, ДиаграммаГанта, СводнаяДиаграмма является устаревшим и не рекомендуется для использования.

- Свойство ОтображатьЗаголовок объектов Диаграмма, ДиаграммаГанта, СводнаяДиаграмма является устаревшим и не рекомендуется для использования.

- Метод глобального контекста ОчиститьЖурналРегистрации() применим только к журналу в формата SQLite, признан устаревшим и его использование не рекомендуется.

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Platform 8.3.12 changelog](https://1c-dn.com/library/v8update_2079252603_new_functionality_and_changes/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedAttributes8312-off // BSLLS:DeprecatedAttributes8312-on
```

### Parameter for config

```json
"DeprecatedAttributes8312": false
```
