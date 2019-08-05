Процедура Проц1()
    // Парность соблюдается
    НачатьТранзакцию();
    Действие();
    ЗафиксироватьТранзакцию();
КонецПроцедуры

Процедура Проц2()
    // Парность соблюдается
    BeginTransaction();
    Действие();
    CommitTransaction();
КонецПроцедуры

Функция Функ1()
    // Парность соблюдается
    BeginTransaction();
    Действие();
    CommitTransaction();
    Возврат Истина;
КонецФункции

Процедура Проц3()
    Действие();
    ЗафиксироватьТранзакцию(); // Парность не соблюдается здесь
КонецПроцедуры

Процедура Проц4()
    BeginTransaction(); // Парность не соблюдается здесь
    Действие();
КонецПроцедуры

Процедура Проц5()
    НачатьТранзакцию();
    Действие();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию(); // Парность не соблюдается здесь
КонецПроцедуры

Процедура Проц6()
    НачатьТранзакцию(); // Парность не соблюдается здесь
    НачатьТранзакцию();
    Действие();
    ЗафиксироватьТранзакцию();
КонецПроцедуры

Процедура Проц7()
    // Парность соблюдается
    НачатьТранзакцию();
    НачатьТранзакцию();
    Действие();
    ЗафиксироватьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию();
КонецПроцедуры

Процедура Проц8()
    // Парность соблюдается
    НачатьТранзакцию();
    Если Истина Тогда
        НачатьТранзакцию();
    КонецЕсли;
    Действие();
    Если Условие1() Тогда
        ЗафиксироватьТранзакцию();
    КонецЕсли;
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию();
КонецПроцедуры

Процедура Проц8()
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию(); // Парность не соблюдается здесь
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию(); // Парность не соблюдается здесь
КонецПроцедуры

Процедура Проц9()
    НачатьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию(); // Парность не соблюдается здесь
КонецПроцедуры

Процедура Проц9()
    НачатьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    НачатьТранзакцию();
    ЗафиксироватьТранзакцию();
    ЗафиксироватьТранзакцию();
    зафиксироватьТРАНЗакциЮ(); // Парность не соблюдается здесь
КонецПроцедуры