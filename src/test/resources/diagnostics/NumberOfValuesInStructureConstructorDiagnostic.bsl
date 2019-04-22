
// Ru

// Структуры

// Pass
Результат = Новый Структура;

// Pass
Результат = Новый Структура();

// Pass
Результат = Новый Структура("Номенклатура, Характеристика, Количество", Номенклатура, Характеристика, 5);

// Pass
Результат = Новый Структура("Номенклатура, Характеристика, Количество, Стоимость");

// Warning
Результат = Новый Структура("Номенклатура, Характеристика, Количество, Стоимость", Номенклатура, Характеристика, 5, 10);

// Pass
Результат = Новый Структура("Номенклатура, Характеристика, Количество",
                            // Warning
                            Новый Структура("Наименование, Код, Производитель, Цена",,,,));

// Фиксированные структуры

// Pass
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

// Pass
Result = New Structure;

// Pass
Result = New Structure();

// Pass
Result = New Structure("Goods, Property, Count", Goods, Property, 5);

// Pass
Result = New Structure("Goods, Property, Count, Cost");

// Warning
Result = New Structure("Goods, Property, Count, Cost", Goods, Property, 5, 10);

// Pass
Result = New Structure("Goods, Property, Count",
                            // Warning
                            New Structure("Name, Code, Manufacturer, Price", Name,,,100));

// FixedStructure

// Pass
Result = New FixedStructure("Goods, Property, Count, Cost");

// Pass
Result = New FixedStructure("Goods, Property, Count", Goods, Property, 5);

// Pass
Результат = Новый Массив;

// Pass
Результат = Новый ("КакойТоТип");