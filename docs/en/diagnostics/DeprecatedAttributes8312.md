# Deprecated 8.3.12 platform features. (DeprecatedAttributes8312)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Info` | `Yes` | `1` | `deprecated` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The following items are deprecated and their use is not recommended since platform version 8.3.12:

- Implemented the new AlwaysHorizontal value of the ChildFormItemsGroup system enum. The Horizontal value of the ChildFormItemsGroup system enum is deprecated;

- ChartLabelsOrientation system enum is no longer available. Actual variant is ChartLabelsOrientation;

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

- Properties of FullTextSearchManager object are obsolete, not recomended for use and supported only for backward compatibility:

    - ShowSeriesScaleLabels - recommended to use the SeriesScale.ScaleLabelLocation
    - ShowPointsScaleLabels- recommended to use the PointsScale.ScaleLabelLocation
    - ShowValuesScaleLabels - recommended to use the ValuesScale.ScaleLabelLocation
    - ShowScaleValueLines - recommended to use the ValuesScale.ОтображениеЛинийСетки
    - ValueScaleFormat - recommended to use the ValuesScale.LabelFormat
    - LabelsOrientation - recommended to use the PointsScale.LabelOrientation

- Property ShowLegend of Chart, GanttChart, PivotChart objects are obsolete and not recomended for use.

- Property ShowTitle of Chart, GanttChart, PivotChart objects are obsolete and not recomended for use.

- The global context method ClearEventLog() is applicable only to a event log in SQLite format, is obsolete and its use is not recommended.

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Platform 8.3.12 changelog](https://1c-dn.com/library/v8update_2079252603_new_functionality_and_changes/)

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
