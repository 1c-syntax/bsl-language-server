# Deprecated 8.3.12 platform features. (DeprecatedAttributes8312)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
The following items are deprecated and their use is not recommended since platform version 8.3.12:

* For the system enumeration `ChildFormItemsGroup` implemented the value `AlwaysHorizontal`, the value `ChildFormItemsGroup.Horizontal` is deprecated
* `ChartLabelsOrientation` system enum is no longer available. Actual variant is `ChartLabelsOrientation`
* The following properties and methods of Chart object are obsolete and not recommended for use:
   * `ColorPalette`;
   * `GradientPaletteStartColor`;
   * `GradientPaletteEndColor`;
   * `GradientPaletteMaxColors`;
   * `GetPalette()`;
   * `SetPalette()`.

* Names of properties of the object `ChartPlotArea`:
   * `ShowScale`
   * `ScaleLines`
   * `ScaleColor`

* Properties of `ChartPlotArea` object are obsolete, not recomended for use and supported only for backward compatibility:
   * `ShowSeriesScaleLabels` - it is recommended to use `SeriesScale.ScaleLabelLocation`
   * `ShowPointsScaleLabels` - it is recommended to use `PointsScale.ScaleLabelLocation`
   * `ShowValuesScaleLabels` - it is recommended to use `ValuesScale.ScaleLabelLocation`
   * `ShowScaleValueLines` - it is recommended to use `ValuesScale.GridLinesShowMode`
   * `ValueScaleFormat` - it is recommended to use `ValuesScale.LabelFormat`
   * `LabelsOrientation` - it is recommended to use `PointsScale.LabelOrientation`

* The `ShowLegend` and `ShowTitle` properties of the `Chart`, `GanttChart`, `PivotChart` objects are deprecated and deprecated for use
* Global context method `ClearEventLog()` is deprecated and only applicable to logs that have `SQLite` format

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Platform 8.3.12 changelog](https://dl04.1c.ru/content/Platform/8_3_12_1714/1cv8upd_8_3_12_1714.htm)
