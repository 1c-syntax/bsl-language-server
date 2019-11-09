
    // RU

    // Pass
    Результат = Новый Структура("МВТ, ТекстЗапроса, Параметры",
                                 Новый МенеджерВременныхТаблиц,
                                 ТекстЗапроса,
                                 Новый Структура);

    // Warn
    Результат = Новый Структура("ДанныеНоменклатуры, Количество",
                                 Новый Структура("Код, Наименование"),
                                 10);

    Результат = Новый Структура("ЗаполнитьПризнакХарактеристикиИспользуются,                    // Warn
                                |ЗаполнитьПризнакТипНоменклатуры,
                                |ПустаяСтруктура,
                                |ЗаполнитьПризнакВариантОформленияПродажи,
                                |МВТ",
                                Новый Структура("Номенклатура", "ХарактеристикиИспользуются"),  // Warn
                                Новый Структура("Номенклатура", "ТипНоменклатуры"),             // Warn
                                Новый Структура,                                                // Pass
                                Новый Структура("Номенклатура", "ВариантОформленияПродажи"),    // Warn
                                Новый МенеджерВременныхТаблиц);                                 // Pass

    Результат = Новый Структура("Параметры",                                                        // Warn
                                Новый Структура("ФиксированнаяСтруктура",                           // Warn
                                                Новый ФиксированнаяСтруктура(Новый Струкутура)));   // Pass

    // EN

    // Pass
    Result = New Structure("TTM, Query, Params",
                            New TempTablesManager,
                            Query,
                            New Structure);

    // Warn
    Result = New Structure("GoodsData, Count",
                            New Structure("Code, Name"),
                            10);

    Result = New Structure("FillCharacter,                          // Warn
                            |FillType,
                            |EmptyStructure,
                            |FillDealType,
                            |TTM",
                            New Structure("Goods", "Character"),    // Warn
                            New Structure("Goods", "Type"),         // Warn
                            New Structure,                          // Pass
                            New Structure("Goods", "DealType"),     // Warn
                            New TempTablesManager);                 // Pass

    Result = New Structure("Params",                                                // Warn
                            New Structure("FixedStructure",                         // Warn
                                            New FixedStructure(New Structure)));    // Pass

    Result = New Structure("Params",                                              // Pass
                            FillStructure(New FixedStructure(New Structure)));    // Pass

    Result = New Structure("field1, field2, field3", New Array(), New Array(), New Array()); // Pass

    // FP
    А = Новый Структура(Новый ФиксированнаяСтруктура(Мок_ПараметрыПроцедуры));
    А = Новый ФиксированнаяСтруктура(Новый Структура("Источник, Данные"));