
//////////////////////////////////////////////
// Название модуля
//////////////////////////////////////////////

Перем А;
#Область Переменные     // <- Ошибка
Перем Б;
Перем Дд;
#КонецОбласти
Перем Ии;

#Область СлужебныеПроцедурыИФункции
Функция Аа() Экспорт
    Возврат 7;
КонецФункции
#КонецОбласти

Процедура Бб()
    #Область Методы2
    Сообщаить(42);
    #КонецОбласти
КонецПроцедуры

///////////////////////////////////////////
// инициализация
///////////////////////////////////////////

#Область Иниц           // <- Ошибки 2 (НЕстандартная область и дубликат)
    #Область Иниц2
        А = 78;
    #КонецОбласти
#КонецОбласти

Б = Аа() + А;

#Область Иниц           // <- Ошибка
Если Условие Тогда
    Ии = 79;
КонецЕсли;
#КонецОбласти
