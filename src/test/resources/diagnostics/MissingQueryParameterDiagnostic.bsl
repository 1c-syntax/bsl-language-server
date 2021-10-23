Процедура ПерваяОшибка_НовыйЗапрос()

    Запрос1 = Новый Запрос("ВЫБРАТЬ
        |    Справочник1.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник1 КАК Справочник1
        |ГДЕ
        |    Справочник1.Ссылка = &Параметр1"); // ошибка

    РезультатЗапроса = Запрос1.Выполнить();

КонецПроцедуры

Процедура Вторая_НетОшибки_НовыйЗапрос()

    Запрос2 = Новый Запрос("ВЫБРАТЬ
        |    Справочник2.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник2 КАК Справочник2
        |ГДЕ
        |    Справочник2.Ссылка = &Параметр2"); // нет ошибки

    Запрос2.УстановитьПараметр("Параметр2", Ссылка);

    РезультатЗапроса = Запрос2.Выполнить();
КонецПроцедуры

Процедура ТретьяОшибка_ЗапросТекст()

    Запрос3 = Новый Запрос;
    Запрос3.Текст = "ВЫБРАТЬ
        |    Справочник1.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник1 КАК Справочник1
        |ГДЕ
        |    Справочник1.Ссылка = &Параметр3"; // ошибка

    РезультатЗапроса = Запрос3.Выполнить();

КонецПроцедуры

Процедура Четвертая_НетОшибки_ЗапросТекст()

    Запрос4 = Новый Запрос;
    Запрос4.Текст = "ВЫБРАТЬ
        |    Справочник4.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник4 КАК Справочник4
        |ГДЕ
        |    Справочник4.Ссылка = &Параметр4"; // нет ошибки

    Запрос4.УстановитьПараметр("Параметр4", Ссылка);

    РезультатЗапроса = Запрос4.Выполнить();
КонецПроцедуры

Процедура ПятаяОшибка_СначалаТекстПотомНовыйЗапрос()

    Текст5 = "ВЫБРАТЬ
        |    Справочник1.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник1 КАК Справочник1
        |ГДЕ
        |    Справочник1.Ссылка = &Параметр5"; // ошибка

    Запрос5 = Новый Запрос(Текст5);
    РезультатЗапроса = Запрос5.Выполнить();

КонецПроцедуры

Процедура Шестой_ТекстБезСозданияЗапроса()

    НеиспользуемыйТекст6 = "ВЫБРАТЬ
        |    Справочник6.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник6 КАК Справочник6
        |ГДЕ
        |    Справочник6.Ссылка = &Параметр6"; // не ошибка

КонецПроцедуры

Процедура Седьмой_СначалаТекстПотомЗапросСДругимТекстом(Текст)

    НеиспользуемыйТекст7 = "ВЫБРАТЬ
        |    Справочник7.Ссылка КАК Ссылка
        |ИЗ
        |    Справочник.Справочник7 КАК Справочник7
        |ГДЕ
        |    Справочник7.Ссылка = &Параметр7"; // не ошибка

    Запрос7 = Новый Запрос(Текст);
    //РезультатЗапроса = Запрос7.Выполнить();

КонецПроцедуры
