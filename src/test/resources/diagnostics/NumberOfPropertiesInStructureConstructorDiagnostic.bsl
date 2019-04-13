
// Ru

// Структуры

// Pass
Результат = Новый Структура();

// Pass
Результат = Новый Структура("Номенклатура, Характеристика, Количество", Номенклатура, Характеристика, 5);

// Warning
Результат = Новый Структура("Номенклатура, Характеристика, Количество, Стоимость");

// Pass
Результат = Новый Структура("Номенклатура, Характеристика, Количество",
                            // Warning
                            Новый Структура("Наименование, Код, Производитель, Цена"));

// Фиксированные структуры

// Warning
Результат = Новый ФиксированнаяСтруктура("Номенклатура, Характеристика, Количество, Стоимость");

// Pass
Результат = Новый ФиксированнаяСтруктура("Номенклатура, Характеристика, Количество", Номенклатура, Характеристика, 5 );

// Прочие конструкторы

// Pass
Результат = Новый ОписаниеТипов(ИсходноеОписаниеТипов, ДобавляемыеТипы, ВычитаемыеТипы, КвалификаторыЧисла);

// Pass
Результат = Новый Запрос("ВЫБРАТЬ
                         |	втТаблица.А,
                         |	втТаблица.Б,
                         |	втТаблица.В,
                         |	втТаблица.Г
                         |ИЗ
                         |	&Таблица КАК втТаблица");


// En

// Structure

// pass
Result = New Structure();

// Pass
Result = New Structure("Goods, Property, Count", Goods, Property, 5);

// Warning
Result = New Structure("Goods, Property, Count, Cost");

// Pass
Result = New Structure("Goods, Property, Count",
                            // Warning
                            New Structure("Name, Code, Manufacturer, Price"));

// FixedStructure

// Warning
Result = New FixedStructure("Goods, Property, Count, Cost");

// Pass
Result = New FixedStructure("Goods, Property, Count", Goods, Property, 5);

// Other Constructors

// Pass
Result = New TypeDescription(OrigTypeDescription, NewTypes, DeletTypes, NumQualifier);

// Pass
Result = New Query("ВЫБРАТЬ
                   |	втТаблица.А,
                   |	втТаблица.Б,
                   |	втТаблица.В,
                   |	втТаблица.Г
                   |ИЗ
                   |	&Таблица КАК втТаблица");