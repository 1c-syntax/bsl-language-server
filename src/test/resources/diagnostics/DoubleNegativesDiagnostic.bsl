// Выражение в условии
Если Не ТаблицаЗначений.Найти(ИскомоеЗначение, "Колонка") <> Неопределено Тогда
    // Сделать действие
КонецЕсли;

А = Не Отказ <> Ложь;
А = Не (Отказ <> Ложь);
А = Не НекотороеЗначение() <> Неопределено;
А = Не Неопределено <> НекотороеЗначение();
А = Не (А <> Неопределено); // срабатывает
А = Не А <> Неопределено И Б = 5; // срабатывает
А = Не (А <> Неопределено и Б = 5); // не срабатывает
А = Не (А <> Неопределено или Б = 5); // не срабатывает
А = Не (Б = 5 и А <> Неопределено); // не срабатывает

Пока Не Таблица.Данные <> Неопределено Цикл
КонецЦикла;

Б = Не (Не А = 1 или Б <> Неопределено); // не срабатывает на "Не А = 1"
Б = Не (А <> 1 или Не Б <> Неопределено); // срабатывает на "Не Б <> Неопределено"
Б = Не (А <> 1 или Не Б = Неопределено); // не срабатывает на "Не Б <> Неопределено" т.к. сравнения вида Не Х = Неопределено популярны

Если Не Т.Найти(Значение) = Неопределено Тогда
    // не срабатывает, т.к. популярный код
КонецЕсли;

// Отрицание с проверкой на неравенство нелитералу

А = Не (Отказ <> НеЛитерал); // срабатывает
А = Не СложнаяФункция() <> НеЛитерал; // срабатывает

Б = Не (А = 1 или Б <> НеЛитерал); // не срабатывает

// Прямое двойное отрицание

Б = Не (Не Значение);
Б = Не (Не Значение И ДругоеЗначение); // не срабатывает

// NoSuchElementException
Запись = РегистрыСведений.ЗаданияКПересчетуСтатуса.СоздатьМенеджерЗаписи();
Запись.Записать(Истина);

// C ошибкой разбора
// Вынесено в отдельную процедуру в блоке subAfterCodeBlock, т.к. иначе парсер ломается целиком и expression tree builder
// ничего не строит
Процедура Тест()

    Если Истина Тогда

        // C ошибкой разбора
        Если Тогда

        КонецЕсли;

        // С ошибкой разбора
        Пока А Цикл
            Если
            #Если Сервер Тогда
            F
            #Иначе
            G
            #КонецЕсли
            Тогда
            КонецЕсли;
        КонецЦикла;

    КонецЕсли;

КонецПроцедуры