Процедура ПроверкаЧисел()

    ПонятнаяПеременная = 6; // Нет ошибки
    СекундВЧасе = 60 * 60; // Ошибка на двух числах

    Если ТекущаяДатаИВремя > СекундВЧасе Тогда // Нет ошибки

        Результат = ?(Число1 = 11, Чтото, 3); // Ошибка на 11

    КонецЕсли;

    Если Описание = 4 Тогда // Ошибка на 4

        Возврат;

    КонецЕсли;

КонецПроцедуры

Процедура Б()
    Если Описание2 > 11 Тогда // Ошибка на 11

        Чтото = Чтото + 1; // Нет ошибки из-за исключения
        Чтото = Чтото + 14; // Ошибка на 14

    КонецЕсли;

    ЭтоВоскресенье = ДеньНедели = 7; // Тут ошибка, хоть и выглядит нормально.
    ДеньНеделиВоскресенье = 7;
    ЭтоВоскресенье = ДеньНедели = ДеньНеделиВоскресенье; // А вот тут уже ошибки нет

    ПроверочноеПеречисление = Новый Массив;
    ПроверочноеПеречисление.Добавить(1); // Нет ошибки из-за исключения
    ПроверочноеПеречисление.Добавить(2); // ошибка
    ПроверочноеПеречисление.Добавить(3); // ошибка

    ПроверочнаяСтруктура = Новый Структура("Авто,ПростойВариант,СложныйВариант", 0, 1, 2); // ошибка только на 2
    ПроверочнаяСтруктура.Добавить("ЭкспертныйВариант", 3); // ошибка

КонецПроцедуры

Процедура А(А = 566) // пропущенная ошибка

КонецПроцедуры

Функция КодОшибки()

    Возврат 12;

КонецФункции

Процедура Индексы()
    Индекс1 = Коллекция.Индексы[20]; // замечание при allowMagicIndexes = false
    Метод(Индексы[21]) // замечание при allowMagicIndexes = false
КонецПроцедуры