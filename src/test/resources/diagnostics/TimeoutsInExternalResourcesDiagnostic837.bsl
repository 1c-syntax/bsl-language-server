
Процедура ПроверкаКейса()

    FTPСоединение = Новый FTPСоединение(Сервер, Порт, Пользователь, Пароль); // <<- ошибка
    Таймаут = 100;
    FTPСоединение = Новый FTPСоединение(Сервер, Порт, Пользователь, Пароль,,,, Неопределено); // <<- ошибка

    FTPСоединение = Новый FTPСоединение(Сервер, Порт, Пользователь, Пароль,, 60);

    Определения = Новый WSОпределения("http://localhost/test.asmx?WSDL"); // <<- ошибка

    Прокси = Новый WSПрокси(Определения, "http://localhost/", "test", "test", Неопределено, 1);

    ПроксиДва = Новый WSПрокси(Определения, "http://localhost/", "test", "test"); // <<- ошибка

    Определения =
        Новый WSОпределения("http://localhost/test.asmx?WSDL", "Пользователь", "Пароль", Неопределено, Таймаут);

КонецПроцедуры

Процедура HTTPБезТаймАута()
    HTTPСоединение = Новый HTTPСоединение("zabbix.localhost", 80); // <<- ошибка
КонецПроцедуры

Процедура HTTPСТаймаутомВОбъявлении()
    HTTPСоединение = Новый HTTPСоединение("zabbix.localhost", 80,,,, Таймаут);
КонецПроцедуры

Процедура HTTPСТаймаутомВСвойстве()
    HTTPСоединение = Новый HTTPСоединение("zabbix.localhost", 80);
    HTTPСоединение.Таймаут = 1;
КонецПроцедуры

Функция НовыйИнтернетПочтовыйПрофильБезТаймАута()
    Профиль = Новый ИнтернетПочтовыйПрофиль; // <<- ошибка
    Профиль.Пользователь = "admin";
    Возврат Профиль;
КонецФункции

Функция НовыйИнтернетПочтовыйПрофильСТаймАутом()
    Профиль = Новый ИнтернетПочтовыйПрофиль;
    Профиль.Пользователь = "admin";
    Профиль.Таймаут = 5;
    Возврат Профиль;
КонецФункции

Функция НовыйИнтернетПочтовыйПрофильСТаймАутомИзФункции()
    Профиль = Новый ИнтернетПочтовыйПрофиль;
    Профиль.Пользователь = "admin";
    Профиль.Таймаут = ТаймАут();
    Возврат Профиль;
КонецФункции

Функция ТаймАут()
    Возврат 1;
КонецФункции

Процедура ТестУсловия()

    УсловиеИстина = Истина;

    HTTPСоединение = Новый HTTPСоединение("zabbix.localhost", 80);
    Если УсловиеИстина Тогда
        HTTPСоединение.Таймаут = 1;
    КонецЕсли;

КонецПроцедуры

Процедура ТестНаВложенныеБлокиКода()

    Попытка
    	Эластик_Соединение = Новый HTTPСоединение(Эластик_Сервер, Эластик_Порт, Эластик_Пользователь, Эластик_Пароль);
    Исключение
    	Эластик_Соединение = Неопределено;
    КонецПопытки;

КонецПроцедуры

Профиль = Новый ИнтернетПочтовыйПрофиль;

Структура.Вставить("ИнтернетПочтовыйПрофиль",  Новый ИнтернетПочтовыйПрофиль);
Структура.ТаймАут(ИнтернетПочтовыйПрофиль.ТаймАут); // Где то тут будет FP

