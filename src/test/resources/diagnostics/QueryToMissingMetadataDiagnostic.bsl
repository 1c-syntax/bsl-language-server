Запрос = Новый Запрос;
Запрос.Текст = "ВЫБРАТЬ
               |  Таблица.Справочник1 КАК Справочник1
               |ИЗ
               |  РегистрСведений.УстаревшееИмяРегистра КАК Таблица"; // ошибка
Запрос.Выполнить();

Запрос2 = Новый Запрос;
Запрос2.Текст = "ВЫБРАТЬ
               |  Таблица.Справочник1 КАК Справочник1
               |ИЗ
               |  РегистрСведений.РегистрСведений1 КАК Таблица"; // не ошибка
Запрос2.Выполнить();

ЗапросСоединение = Новый Запрос;
ЗапросСоединение.Текст = "ВЫБРАТЬ
               |  Таблица.Справочник1 КАК Справочник1
               |ИЗ
               |  РегистрСведений.РегистрСведений1 КАК Таблица
               |  ВНУТРЕННЕЕ СОЕДИНЕНИЕ РегистрСведений.УдалитьИмяРегистра КАК ТаблицаФильтр // ошибка
               |  ПО ТаблицаФильтр.Справочник1 = Таблица.Справочник1";
ЗапросСоединение.Выполнить();

ЗапросСоединение2 = Новый Запрос;
ЗапросСоединение2.Текст = "ВЫБРАТЬ
               |  Таблица.Справочник1 КАК Справочник1
               |ИЗ
               |  РегистрСведений.РегистрСведений1 КАК Таблица
               |  ВНУТРЕННЕЕ СОЕДИНЕНИЕ РегистрСведений.РегистрСведений1 КАК ТаблицаФильтр // не ошибка
               |  ПО ТаблицаФильтр.Справочник1 = Таблица.Справочник1";
ЗапросСоединение2.Выполнить();

Запрос2 = Новый Запрос;
Запрос2.Текст = "ВЫБРАТЬ
               |  Таблица.Справочник1 КАК Справочник1
               |ИЗ
               |  РегистрСведений.рЕГИСТРСведений1 КАК Таблица"; // не ошибка
Запрос2.Выполнить();

Запрос1 = Новый Запрос;
Запрос1.Текст = "ВЫБРАТЬ
               |  Таблица1.Поле1 КАК Поле1
               |ИЗ
               |  ВнешнийИсточникДанных.ВнешнийИсточникДанных1.Таблица.Таблица1 КАК Таблица1"; // не ошибка
Запрос1.Выполнить();

Запрос2 = Новый Запрос;
Запрос2.Текст = "ВЫБРАТЬ
               |  Таблица1.Поле1 КАК Поле1
               |ИЗ
               |  ВнешнийИсточникДанных.ВнешнийИсточникДанных2.Таблица.Таблица1 КАК Таблица1"; // ошибка
Запрос2.Выполнить();

ИмяТаблицы = "Таблица10";
Запрос3 = Новый Запрос;
Запрос3.Текст = "ВЫБРАТЬ
               |  Таблица1.Поле1 КАК Поле1
               |ИЗ
               |  ВнешнийИсточникДанных.ВнешнийИсточникДанных1.Таблица." + ИмяТаблицы + " КАК Таблица1"; // не ошибка
Запрос3.Выполнить();

ИмяТаблицы = "Таблица10";
Запрос4 = Новый Запрос;
Запрос4.Текст = "ВЫБРАТЬ
               |  Таблица1.Поле1 КАК Поле1
               |ИЗ
               |  ВнешнийИсточникДанных.ВнешнийИсточникДанных2.Таблица." + ИмяТаблицы + " КАК Таблица1"; // ошибка
Запрос4.Выполнить();
