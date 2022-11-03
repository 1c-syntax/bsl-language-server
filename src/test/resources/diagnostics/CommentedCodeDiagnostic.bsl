// ++ 20.08.2019 18538: Добавление проверки флага "Только при первом проведении".
//ПервоеПроведение = Ложь;
//Если Источник.ДополнительныеСвойства.Свойство("ПервоеПроведение")
//    И Событие = "ОбработкаПроведения" Тогда
//    ПервоеПроведение = Источник.ДополнительныеСвойства.ПервоеПроведение;
//КонецЕсли;
// -- 20.08.2019 18538: Добавление проверки флага "Только при первом проведении".

////////////////////////////////////////////////////////////////////////////////
// ОБРАБОТЧИКИ ПОДГОТОВКИ ДАННЫХ ДЛЯ ПРОБИТИЯ ДОКУМЕНТА
// ++ 18.02.2019 20000: Добавление новых обязательных тегов Фискального Чека.
Процедура ПолучитьШаблонЧека(ОбщиеПараметры, ОписаниеОшибки, ПараметрыПробития) Экспорт

    // Определим вид выписки (расход/приход) и если указаны возвраты, то возврат
    Объект = ПараметрыПробития.Объект;

    //++ 26.06.2019 20630: перенос из типовой АА6
    // Определим телефон и/или емаил
    //Свойства = УправлениеСвойствами.ПолучитьЗначенияСвойств(Объект.Ссылка);
    //КИПокупателя = Свойства.Найти(ПланыВидовХарактеристик.ДополнительныеРеквизитыИСведения.ТелефонИЛИЭлектронныйАдресПокупателя, "Свойство");
    //Если НЕ КИПокупателя = Неопределено И ЗначениеЗаполнено(КИПокупателя.Значение) Тогда
    //    Телефон = СтрЗаменить(КИПокупателя.Значение, " " , "");
    //    Телефон = СтрЗаменить(Телефон, "(" , "");
    //    Телефон = СтрЗаменить(Телефон, ")" , "");
    //    Телефон = СтрЗаменить(Телефон, "-" , "");
    //
    //    Если ОбщегоНазначенияКлиентСервер.АдресЭлектроннойПочтыСоответствуетТребованиям(КИПокупателя.Значение) Тогда
    //        ОбщиеПараметры.ПокупательEmail = КИПокупателя.Значение;
    //    ИначеЕсли ОбщегоНазначенияКлиентСервер.СтрокаСодержитТолькоДопустимыеСимволы(Телефон,"+0123456789") И СтрНайти(Телефон,"+") = 1 Тогда
    //        ОбщиеПараметры.ПокупательНомер = КИПокупателя.Значение;
    //    Иначе
    //        ОписаниеОшибки = ?(ЗначениеЗаполнено(ОписаниеОшибки), ОписаниеОшибки+Символы.ПС, НСтр("ru = 'В поле ""Телефон или электронный адрес покупателя"" введены не корректные данные'"));
    //        Возврат;
    //    КонецЕсли;
    //КонецЕсли;

    // Настройка отправки электронного чека.
    // Если Электронно = Истина, то чек будет предоставлен в электронной форме, без печати.
    //ОбщиеПараметры.Электронно = Константы.НеПечататьФискальныйЧекПриОтправкеЭлектронногоЧекаПокупателю.Получить();// Если Электронно = Истина, то чек будет предоставлен в электронной форме, без печати.
    // SMS отправляет средствами 1C.
    //ОбщиеПараметры.Отправляет1СSMS = Константы.ЭлектронныйЧекSMSПередаютсяПрограммой1С.Получить() И ЗначениеЗаполнено(ОбщиеПараметры.ПокупательНомер);
    // Email отправляет средствами 1C.
    //ОбщиеПараметры.Отправляет1СEmail = Константы.ЭлектронныйЧекEmailПередаютсяПрограммой1С.Получить() И ЗначениеЗаполнено(ОбщиеПараметры.ПокупательEmail);

    // Дополнительный реквизит чека
    //Свойства = УправлениеСвойствами.ПолучитьЗначенияСвойств(Объект.Ссылка);
    //ДополнительныйРеквизитЧека = Свойства.Найти(ПланыВидовХарактеристик.ДополнительныеРеквизитыИСведения.ДополнительныйРеквизитЧека, "Свойство");
    //Если НЕ ДополнительныйРеквизитЧека = Неопределено И ЗначениеЗаполнено(ДополнительныйРеквизитЧека.Значение) Тогда
    //    ОбщиеПараметры.ДополнительныйРеквизит = ДополнительныйРеквизитЧека.Значение;
    //КонецЕсли;

    // Настройка отправки электронного чека
    ОбщиеПараметры.Отправляет1СSMS = НЕ Константы.ОтправлятьЭлектронныеЧекиПоSMSЧерезОФД.Получить()
        И ЗначениеЗаполнено(ОбщиеПараметры.ПокупательНомер);
    ОбщиеПараметры.Отправляет1СEmail = НЕ Константы.ОтправлятьЭлектронныеЧекиПоEmailЧерезОФД.Получить()
        И ЗначениеЗаполнено(ОбщиеПараметры.ПокупательEmail);
    ОбщиеПараметры.Электронно = Константы.НеПечататьФискальныйЧекПриОтправкеЭлектронногоЧекаПокупателю.Получить()
        И (ОбщиеПараметры.Отправляет1СSMS ИЛИ ОбщиеПараметры.Отправляет1СEmail);

    // Пока оставим так как есть. Нужно для расчета ОСН.
    //СтруктураОрганизация = ПолучитьПараметрыОрганизации(
    //    ОрганизацияОбъекта,
    //    ПодразделениеКомпанииОбъекта,
    //    СкладКомпанииОбъекта,
    //    Дата);
    //СтруктураОрганизация = ПолучитьПараметрыОрганизации(ОрганизацияОбъекта);
    СтруктураОрганизация = ПолучитьПараметрыОрганизации(
        ОрганизацияОбъекта,
        ПодразделениеКомпанииОбъекта,
        СкладКомпанииОбъекта,
        Дата);

КонецПроцедуры
// -- 18.02.2019 20000: Добавление новых обязательных тегов Фискального Чека.


//// Процедура ОбработкаПроведения_РеализацияАвтомобилей(Источник, Отказ, РежимПроведения)
////
////    ПроизведемДвиженияПоСкладскомуУчетуАвтомобилейСПробегом(Источник, Отказ, РежимПроведения);
////
////КонецПроцедуры

// Перем1 = Перем2 + 1;

//ДкОбъект.ДатаЗакрытия = ТекущаяДатаСеанса();
//ДкОбъект.Дата = ТекущаяДатаСеанса();

// Получает дерево метаданных конфигурации с заданным отбором по объектам метаданных.
//
// Параметры:
//   Отбор - Структура - содержит значения элементов отбора.
//		Если параметр задан, то будет получено дерево метаданных в соответствии с заданным отбором:
//		Ключ - Строка - имя свойства элемента метаданных;
//		Значение - Массив - множество значений для отбора.
//
// Пример инициализации переменной "Отбор":
//
// Массив = Новый Массив;
// Массив.Добавить("Константа.ИспользоватьСинхронизациюДанных");
// Массив.Добавить("Справочник.Валюты");
// Массив.Добавить("Справочник.Организации");
// Отбор = Новый Структура;
// Отбор.Вставить("ПолноеИмя", Массив);
//
//  Возвращаемое значение:
//   ДеревоЗначений - дерево описания метаданных конфигурации.
//
Функция ПолучитьДеревоМетаданныхКонфигурации(Отбор = Неопределено) Экспорт


КонецФункции

// Проверили, что закрываемая сделка не есть сам документ движения
// Посмотрим, запрещена ли по данной сделке переплата. И если запрещена, то падаем.

// И если сумма прихода отрицательная, т.е. производиться сторнирование (возвратный чек).

// СуммаНДСНаСкладе = 0;
// СуммаУпрНаСкладе = 0;

// Устанавливает указанный счет как основной.
// Текущий основной счет сбрасывается, вне зависимости от значения устанавливаемого.
// Ошибки выполнения фиксируются в журнале регистрации.
//
// Параметры:
//  Счет - СправочникСсылка.БанковскиеСчета, Неопределено - Счет устанавливаемый как основной;
//  Владелец - СправочникСсылка.Контрагенты,
//             СправочникСсылка.Организации,
//             СправочникСсылка.ПодразделенияКомпании - Владелец счета.
//
// Возвращаемое значение:
//  Булево - Успешность операции.
//
Функция ПроверочнаяФункция()

КонецФункции

// Возвращает объект метаданных по переданному идентификатору.
//
// Параметры:
//  Идентификатор - СправочникСсылка.ИдентификаторыОбъектовМетаданных,
//                  СправочникСсылка.ИдентификаторыОбъектовРасширений - идентификатор
//                    объекта метаданных конфигурации или расширения конфигурации.
//
//  КромеНесуществующихИНедоступных - Булево - если Истина тогда, когда объект метаданных
//                    не существует или не доступен, возвращает Null или Неопределено
//                    вместо вызова исключения.
//
// Возвращаемое значение:
//  ОбъектМетаданных - объект метаданных, соответствующий идентификатору.
//
//  Null - возвращается, когда КромеНесуществующихИНедоступных = Истина. Обозначает, что
//    для указанного идентификатора объект метаданных не существует (идентификатор устарел).
//
//  Неопределено - возвращается, когда КромеНесуществующихИНедоступных = Истина. Обозначает,
//    что идентификатор действующий, но в текущем сеансе ОбъектМетаданных не может быть получен.
//    Для расширений конфигурации это значит, что расширение установлено, но не подключено,
//    либо потому что перезапуск еще не выполнен, либо при подключении произошла ошибка.
//    Для конфигурации это значит , что в новом сеансе (новом динамическом поколении) объект
//    метаданных имеется, а в текущем (старом) сеансе нет.
//
Функция ОбъектМетаданныхПоИдентификатору(Идентификатор, КромеНесуществующихИНедоступных = Ложь) Экспорт

  Идентификаторы = Новый Массив;
  Идентификаторы.Добавить(Идентификатор);

  ОбъектыМетаданных = ОбъектыМетаданныхПоИдентификаторамСПопыткойПовтора(Идентификаторы, КромеНесуществующихИНедоступных);

  Возврат ОбъектыМетаданных.Получить(Идентификатор);

КонецФункции

// Возвращает объекты метаданных по переданным идентификаторам.
//
// Параметры:
//  Идентификаторы - Массив - со значениями:
//                     * Значение - СправочникСсылка.ИдентификаторыОбъектовМетаданных,
//                                  СправочникСсылка.ИдентификаторыОбъектовРасширений - идентификаторы
//                                    объектов метаданных конфигурации или расширений конфигурации.
//
//  КромеНесуществующихИНедоступных - Булево - если Истина тогда, когда объект метаданных
//                    не существует или не доступен, возвращает Null или Неопределено
//                    вместо вызова исключения.
//
// Возвращаемое значение:
//  Соответствие - со свойствами:
//   * Ключ - переданный идентификатор,
//   * Значение - ОбъектМетаданных - объект метаданных, соответствующий идентификатору.
//              - Null - возвращается, когда КромеНесуществующихИНедоступных = Истина. Обозначает, что
//                  для указанного идентификатора объект метаданных не существует (идентификатор устарел).
//              - Неопределено - возвращается, когда КромеНесуществующихИНедоступных = Истина. Обозначает,
//                  что идентификатор действующий, но в текущем сеансе ОбъектМетаданных не может быть получен.
//                  Для расширений конфигурации это значит, что расширение установлено, но не подключено,
//                  либо потому что перезапуск еще не выполнен, либо при подключении произошла ошибка.
//                  Для конфигурации это значит , что в новом сеансе (новом динамическом поколении) объект
//                  метаданных имеется, а в текущем (старом) сеансе нет.
//
Функция ОбъектыМетаданныхПоИдентификаторам(Идентификаторы, КромеНесуществующихИНедоступных = Ложь) Экспорт

  Возврат ОбъектыМетаданныхПоИдентификаторамСПопыткойПовтора(Идентификаторы, КромеНесуществующихИНедоступных);

КонецФункции

// Проверка = Новый Структура();

Процедура ПередЭтойФункциейНетКомментарияСОписанием()

    Возврат ОбъектыМетаданныхПоИдентификаторамСПопыткойПовтора(Идентификаторы, КромеНесуществующихИНедоступных);

КонецПроцедуры

#Область СлужебныеПроцедурыИФункции

// Добавляет в коллекцию оформляемых полей компоновки данных новое поле
//
// Параметры:
//	КоллекцияОформляемыхПолей 	- коллекция оформляемых полей КД
//	ИмяПоля						- Строка - имя поля.
//
// Возвращаемое значение:
//	ОформляемоеПолеКомпоновкиДанных - созданное поле.
//
// Пример:
// 	Форма.УсловноеОформление.Элементы[0].Поля = "Проверка";
//
&НаСервере
Функция ДобавитьОформляемоеПоле(КоллекцияОформляемыхПолей, ИмяПоля)

	ПолеЭлемента 		= КоллекцияОформляемыхПолей.Элементы.Добавить();
	ПолеЭлемента.Поле 	= Новый ПолеКомпоновкиДанных(ИмяПоля);

	Возврат ПолеЭлемента;

КонецФункции

// Перезапуск события "ПередОкончаниемРедактирования" с ОтменаНачалаРедактирования = Истина.

// Для вида "ВедетсяПоПриходу" нужно рассчитать отношение коэффициента списываемой единицы
// ко всем остальным единицам выборки.
// Стратегия: Если СкладКомпании.УчетЕдиницИзмерения = «Ведется по приходу», то количество и единицу измерения
// необходимо автоматически подобрать в зависимости от остатков на складе - сначала пытается списать
// ту единицу измерения, как она указана в документе, потом пытаемся подобрать ближайшую
// по кратности единицу измерения.

// Вызывается при завершении сеансов средствами подсистемы ЗавершениеРаботыПользователей.
//
// Параметры:
//  ФормаВладелец - УправляемаяФорма, из которой выполняется завершение сеанса,
//  МассивСеансов - Массив(Число (8,0,+)) - массив номеров сеанса, который будет завершен,
//  СтандартнаяОбработка - Булево, флаг выполнения стандартной обработки завершения сеанса
//    (подключение к агенту сервера через COM-соединение или сервер администрирования с
//    запросом параметров подключения к кластеру у текущего пользователя). Может быть
//    установлен в значение Ложь внутри обработчика события, в этом случае стандартная
//    обработка завершения сеанса выполняться не будет,
//  ОповещениеПослеЗавершенияСеанса - ОписаниеОповещения - описание оповещения, которое должно
//    быть вызвано после завершения сеанса (для автоматического обновления списка активных
//    пользователей). При установке значения параметра СтандартнаяОбработка равным Ложь,
//    после успешного завершения сеанса, для переданного описания оповещения должна быть
//    выполнена обработка с помощью метода ВыполнитьОбработкуОповещения (в качестве значения
//    параметра Результат следует передавать КодВозвратаДиалога.ОК при успешном завершении
//    сеанса). Параметр может быть опущен - в этом случае выполнять обработку оповещения не
//    следует.
//
// Синтаксис:
// Процедура ПриЗавершенииСеансов(ФормаВладелец, Знач МассивСеансов, СтандартнаяОбработка, Знач ОповещениеПослеЗавершенияСеансов = Неопределено) Экспорт

Процедура ШаблонМетода(Параметр)

    //<code>Если Истина Тогда
    //<code>Возврат;
    //<code>КонецЕсли;

КонецПроцедуры

#КонецОбласти

////////////////////////////////////////////////////////////////////////////////
// ПРОЦЕДУРЫ - ОБРАБОТЧИКИ СОБЫТИЙ ЭЛЕМЕНТОВ ФОРМЫ
//
// ОБЪЕКТЫ НЕ ДОБАВЛЕНЫ