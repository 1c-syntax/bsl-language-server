Процедура Тест()
    тест = ОбластьПостроенияДиаграммы.ОтображатьШкалу;
    ОбластьПостроенияДиаграммы.ЛинииШкалы = Ложь;
    ОбластьПостроенияДиаграммы.ЦветШкалы = Ложь;
    ОбластьПостроенияДиаграммы.ОтображатьПодписиШкалыСерий = Ложь;
    ОбластьПостроенияДиаграммы.ОтображатьПодписиШкалыТочек = Ложь;
    ОбластьПостроенияДиаграммы.ОтображатьПодписиШкалыЗначений = Ложь;
    ОбластьПостроенияДиаграммы.ОтображатьЛинииЗначенийШкалы = Ложь;
    ОбластьПостроенияДиаграммы.ФорматШкалыЗначений = Ложь;
    ОбластьПостроенияДиаграммы.ОриентацияМеток = Ложь;
КонецПроцедуры

Procedure Test()
    ChartPlotArea.ShowScale = True;
    ChartPlotArea.ShowSeriesScaleLabels = True;
    ChartPlotArea.ShowPointsScaleLabels = True;
    ChartPlotArea.ShowValuesScaleLabels = True;
    ChartPlotArea.ShowScaleValueLines = True;
    ChartPlotArea.ValueScaleFormat = True;
    ChartPlotArea.LabelsOrientation = True;
EndProcedure

Процедура Тест2()
    Диаграмма.ОтображатьЛегенду = Истина;
    Диаграмма.ОтображатьЗаголовок = Истина;
    ДиаграммаГанта.ОтображатьЛегенду = Истина;
    ДиаграммаГанта.ОтображатьЗаголовок = Истина;
    СводнаяДиаграмма.ОтображатьЛегенду = Истина;
    СводнаяДиаграмма.ОтображатьЗаголовок = Истина;

    Диаграмма.ПалитраЦветов = Истина;
    Диаграмма.ЦветНачалаГрадиентнойПалитры = Истина;
    Диаграмма.ЦветКонцаГрадиентнойПалитры = Истина;
    Диаграмма.МаксимальноеКоличествоЦветовГрадиентнойПалитры = Истина;

    Тест = Диаграмма.ПолучитьПалитру();
    Диаграмма.УстановитьПалитру(Неопределено);
КонецПроцедуры

Procedure Test2()
    Chart.ShowLegend = True;
    GanttChart.ShowLegend = True;
    PivotChart.ShowLegend = True;
    Chart.ShowTitle = True;
    GanttChart.ShowTitle = True;
    PivotChart.ShowTitle = True;

    Chart.ColorPalette = True;
    Chart.GradientPaletteStartColor = True;
    Chart.GradientPaletteEndColor = True;
    Chart.GradientPaletteMaxColors = True;

    Chart.GetPalette();
    Chart.SetPalette(True);

EndProcedure

Процедура Тест3()
    Ориентация = ОриентацияМетокДиаграммы.Авто;
КонецПроцедуры

Процедура Тест4()
    ОчиститьЖурналРегистрации(Отбор);
КонецПроцедуры

Procedure Test4()
    ClearEventLog(Filter);
EndProcedure

Процедура Тест5()
    Группировка = ГруппировкаПодчиненныхЭлементовФормы.Горизонтальная;
КонецПроцедуры

Procedure Test5()
    test = ChildFormItemsGroup.Horizontal;
EndProcedure
