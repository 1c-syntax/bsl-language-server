Процедура Тест1()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Ссылка
    |Из РегистрСведений.Курсы.СрезПоследних КАК Т //<-- ошибка
    |";

КонецПроцедуры

Процедура Тест2()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Измерение Из Справочник.Справочник1 КАК Спр
    |Левое соединение
    |РегистрНакопления.Склады.Остатки(Склад = &Параметр) КАК Т
    |По Спр.Поле1 = Т.Местонахождение";

КонецПроцедуры

Процедура Тест3()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Регистратор Из Справочник.Справочник1 КАК Спр
    |Правое соединение
    |РегистрНакопления.Склады.Остатки(, Склад = &Параметр) КАК Т
    |   По Спр.Поле1 = Т.Местонахождение";

КонецПроцедуры

Процедура Тест4()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Измерение
    |Из РегистрСведений.Курсы.СрезПоследних(&Период) как Курсы //<-- не ошибка
    |Левое соединение РегистрНакопления.Склады.Остатки(Склад = &Параметр) КАК Т
    |По Курсы.Поле1 = Т.Измерение";

КонецПроцедуры

Процедура Тест5()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Ссылка
    |Из РегистрНакопления.Склады.Остатки() как Т //<-- ошибка
    |";

КонецПроцедуры

Процедура Тест6()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Ссылка
    |Из РегистрНакопления.Склады.Остатки(, ) как Т //<-- ошибка
    |";

КонецПроцедуры

Процедура Тест7()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Ссылка
    |Из РегистрНакопления.Склады.Остатки(, Склад = &Параметр) как Т
    |";

КонецПроцедуры

Процедура Тест8()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "Выбрать Т.Ссылка
    |Из РегистрНакопления.Склады.Остатки(&Период, ) как Т //<-- считаем ошибкой
    |";

КонецПроцедуры