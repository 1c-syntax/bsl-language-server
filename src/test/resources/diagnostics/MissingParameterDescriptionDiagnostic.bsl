Функция БезПараметровИОписания()
КонецФункции

Функция БезОписания(Параметр1, Параметр2)
КонецФункции

// Описание есть, но нет параметров
Функция Пример1(Параметр1, Параметр2)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
Функция Пример2()
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
Функция Пример3(Параметр1)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
Функция Пример4(Параметр2, Параметр3)
КонецФункции

// Описание есть,
// Параметры:
// Параметр2 - Строка - Описание параметра 2
// Параметр1 - Строка - Описание параметра 1
Функция Пример5(Параметр1, Параметр2)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка
// Параметр2
Функция Пример6(Параметр1, Параметр2)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
// Параметр2 - Строка - Описание параметра 2
Функция Пример7(Параметр1, Параметр2)
КонецФункции

// Описание есть,
// Параметры:
// Параметр3 - Строка - Описание параметра 3
// Параметр4 - Строка - Описание параметра 4
// Параметр5
Функция Пример8(Параметр1, Параметр2)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
// Параметр3 - Строка - Описание параметра 3
// Параметр4 - Строка - Описание параметра 4
// Параметр5 - тип
Функция Пример9(Параметр1, Знач Параметр4)
КонецФункции

// Описание есть,
// Параметры:
// Параметр1 - Строка - Описание параметра 1
// Параметр2 - Строка - Описание параметра 2
Функция Пример10(параметр1, ПаРамЕтр2)
КонецФункции

// См. Пример10()
Функция Пример11(параметр1, ПаРамЕтр2)
КонецФункции

// Загружает настройку из хранилища общих настроек, как метод платформы Загрузить,
// объектов СтандартноеХранилищеНастроекМенеджер или ХранилищеНастроекМенеджер.<Имя хранилища>,
// но с поддержкой длины ключа настроек более 128 символов путем хеширования части,
// которая превышает 96 символов.
// Кроме того, возвращает указанное значение по умолчанию, если настройки не существуют.
// Если нет права СохранениеДанныхПользователя, возвращается значение по умолчанию без ошибки.
//
// В возвращаемом значении очищаются ссылки на несуществующий объект в базе данных, а именно
// - возвращаемая ссылка заменяется на указанное значение по умолчанию;
// - из данных типа Массив ссылки удаляются;
// - у данных типа Структура и Соответствие ключ не меняется, а значение устанавливается Неопределено;
// - анализ значений в данных типа Массив, Структура, Соответствие выполняется рекурсивно.
//
// Параметры:
//   КлючОбъекта          - Строка           - см. синтакс-помощник платформы.
//   КлючНастроек         - Строка           - см. синтакс-помощник платформы.
//   ЗначениеПоУмолчанию  - Произвольный     - значение, которое возвращается, если настройки не существуют.
//                                             Если не указано, возвращается значение Неопределено.
//   ОписаниеНастроек     - ОписаниеНастроек - см. синтакс-помощник платформы.
//   ИмяПользователя      - Строка           - см. синтакс-помощник платформы.
//
// Возвращаемое значение:
//   Произвольный - см. синтакс-помощник платформы.
//
Функция BUG_1490(КлючОбъекта, КлючНастроек, ЗначениеПоУмолчанию = Неопределено,
			ОписаниеНастроек = Неопределено, ИмяПользователя = Неопределено) Экспорт
КонецФункции

// Делает некоторые вещи с массивом строк
//
// Параметры:
//  МассивСтрок - Массив из Строка - Массив строк
Функция BUG_1620(МассивСтрок)
КонецФункции