Процедура Тест1_ЛевоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники.Ссылка  //<-- ошибка
    |ИЗ Справочник.Склады КАК Склады
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники
    |ПО Склады.Кладовщик = Сотрудники.Ссылка
    |";

КонецПроцедуры

Процедура Тест2()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники2.Ссылка  //<-- ошибка
    |ИЗ Справочник.Склады КАК Склады
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники
    |ПО Склады.Кладовщик = Сотрудники.Ссылка
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники2
    |ПО Склады.Кладовщик = Сотрудники2.Ссылка
    |";

КонецПроцедуры

Процедура Тест3()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники3.Ссылка,  //<-- ошибка
    |ЕСТЬNULL(Сотрудники3.Ссылка, 0)  // не ошибка
    |ИЗ Справочник.Склады КАК Склады
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники3
    |ПО Склады.Кладовщик = Сотрудники3.Ссылка
    |";

КонецПроцедуры

Процедура Тест4()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Склады.Ссылка
    |ИЗ Справочник.Склады КАК Склады
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники4
    |ПО Склады.Кладовщик = Сотрудники4.Ссылка
    |ГДЕ Сотрудники4.Флаг                   //<-- ошибка
    |И ЕСТЬNULL(Сотрудники4.Флаг, Истина)   //не ошибка
    |";

КонецПроцедуры

Процедура Тест5_ПравоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Склады5.Ссылка,  //<-- ошибка
    |ЕСТЬNULL(Склады5.Ссылка, 0)  // не ошибка
    |ИЗ Справочник.Склады КАК Склады5
    |ПРАВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники5
    |ПО Склады5.Кладовщик = Сотрудники5.Ссылка
    |";

КонецПроцедуры

Процедура Тест6_ВнутреннееСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Склады6.Ссылка  //не ошибка
    |ИЗ Справочник.Склады КАК Склады6
    |ВНУТРЕННЕЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники6
    |ПО Склады6.Кладовщик = Сотрудники6.Ссылка
    |";

КонецПроцедуры

Процедура Тест7_ВУсловииСоединения()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Истина
    |ИЗ Справочник.Склады КАК Склады7
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники7
    |ПО Склады7.Кладовщик = Сотрудники7.Ссылка
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники71
    |ПО Сотрудники7.Ссылка    //<-- ошибка
    | = Сотрудники71.Ссылка
    | И ЕСТЬNULL(Сотрудники7.Ссылка, Истина)    // не ошибка
    |";

КонецПроцедуры

Процедура Тест8_ПолноеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники8.Ссылка,  //<-- ошибка
    |Склады8.Ссылка,  //<-- ошибка
    |Сотрудники8.Организация,  //<-- ошибка
    |ЕСТЬNULL(Сотрудники8.Ссылка, 0),  // не ошибка
    |ЕСТЬNULL(Склады8.Ссылка, 0)  // не ошибка
    |ИЗ Справочник.Склады КАК Склады8
    |ПОЛНОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники8
    |ПО Склады8.Кладовщик = Сотрудники8.Ссылка
    |";

КонецПроцедуры

Процедура Тест9_ЕстьНЕ_NULL_ЛевоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники9.Ссылка  // не ошибка
    |ИЗ Справочник.Склады КАК Склады9
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники9
    |ПО Склады9.Кладовщик = Сотрудники9.Ссылка
    //|ГДЕ (НЕ (Сотрудники9.Реквизит ЕСТЬ NULL))
    |ГДЕ (Сотрудники9.Реквизит ЕСТЬ НЕ NULL)
    |";

КонецПроцедуры

Процедура Тест10_ЕстьНЕ_NULL_ЛевоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники10.Ссылка  // не ошибка
    |ИЗ Справочник.Склады КАК Склады10
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники10
    |ПО Склады10.Кладовщик = Сотрудники10.Ссылка
    |ГДЕ (НЕ (Сотрудники10.Реквизит ЕСТЬ NULL)) // TODO (Сотрудники10.Реквизит ЕСТЬ НЕ NULL)
    |";

КонецПроцедуры

Процедура Тест11_ОдинаковыеИменаТаблицВОбъединении()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ
    |	Контрагенты11.Ссылка КАК Ссылка
    |ПОМЕСТИТЬ ВТ
    |ИЗ
    |	Таблица КАК Контрагенты11
    |
    |ОБЪЕДИНИТЬ ВСЕ
    |
    |ВЫБРАТЬ
    |   Таблица11.Ссылка КАК Ссылка,
    |   Контрагенты11.Ссылка КАК Ссылка1    //<-- ошибка
    |ИЗ
    |	Справочник.Склады КАК Таблица11
    |   ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Контрагенты КАК Контрагенты11
    |   ПО Таблица11.Ссылка = Контрагенты11.Ссылка
    |
    |ОБЪЕДИНИТЬ ВСЕ
    |
    |ВЫБРАТЬ
    |   Таблица11.Ссылка КАК Ссылка,
    |   Контрагенты11.Ссылка КАК Ссылка1    //не ошибка
    |ИЗ
    |	Справочник.Склады КАК Таблица11
    |   ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Контрагенты КАК Контрагенты11
    |   ПО Таблица11.Ссылка = Контрагенты11.Ссылка
    |ГДЕ (НЕ (Контрагенты11.Реквизит ЕСТЬ NULL)) // TODO Контрагенты11.Реквизит ЕСТЬ НЕ NULL
    |";

КонецПроцедуры

Процедура Тест13_Есть_NULL_ЛевоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники13.Ссылка  // ошибка
    |ИЗ Справочник.Склады КАК Склады13
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники13
    |ПО Склады13.Кладовщик = Сотрудники13.Реквизит
    |ГДЕ Сотрудники13.Реквизит ЕСТЬ NULL // не ошибка
    |";

КонецПроцедуры

Процедура Тест14_ЕстьНЕ_NULL_ЛевоеСоединение()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Сотрудники14.Ссылка  // не ошибка
    |ИЗ Справочник.Склады КАК Склады14
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники14
    |ПО Склады14.Кладовщик = Сотрудники14.Ссылка
    |ГДЕ (НЕ (Сотрудники14.Реквизит ЕСТЬ NULL)) //TODO НЕ Сотрудники14.Реквизит ЕСТЬ NULL
    |";

КонецПроцедуры

Процедура Тест15_в_ГДЕ_Есть_NULL_НоНетОбращенийКПолямТаблицы()

    Запрос = Новый Запрос;
    Запрос.Текст =
    "ВЫБРАТЬ Истина
    |ИЗ Справочник.Склады КАК Склады15
    |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Сотрудники КАК Сотрудники15
    |ПО Склады15.Кладовщик = Сотрудники15.Ссылка
    |ГДЕ Сотрудники15.Реквизит ЕСТЬ NULL // не ошибка
    |";
КонецПроцедуры

 // Разрыв в соединении роняет с npe
 Запрос = "ВЫБРАТЬ
           |	ДополнительныеСведения.КодИФНСФЛ КАК КодИФНСФЛ
           |ИЗ
           |	РегистрСведений.АдресныеОбъекты КАК АдресныеОбъектыУровень0
           |
           |	ОБЪЕДИНИТЬ ВСЕ
           |
           |	ВЫБРАТЬ
           |	ДополнительныеСведения.КодИФНСФЛ
           |ИЗ
           |	РегистрСведений.АдресныеОбъекты КАК АдресныеОбъектыУровень0
           |		ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений.АдресныеОбъекты КАК АдресныеОбъектыУровень1
           |		ПО АдресныеОбъектыУровень0.РодительскийИдентификатор = АдресныеОбъектыУровень1.Идентификатор
           |		ЛЕВОЕ СОЕДИНЕНИЕ РегистрСведений."+АдресныеОбъекты+" КАК АдресныеОбъектыУровень2
           |		ПО (АдресныеОбъектыУровень1.РодительскийИдентификатор = АдресныеОбъектыУровень2.Идентификатор)
           |";
