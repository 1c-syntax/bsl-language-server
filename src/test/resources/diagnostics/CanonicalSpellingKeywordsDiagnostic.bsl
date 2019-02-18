
Процедура ПроверкаКаноническихСлов()

    #Область ОбъявлениеПеременных
    
    // Корректное выражение
    Перем А;
    // Выражение с ошибками
    ПерЕМ Б;
    
    // Корректное выражение
    А = Неопределено;
    Б = Новый Массив();
    
    // Выражение с ошибками
    А = НЕОПРЕделено;
    Б = НоВый Массив();
    
    #КонецОбласти    
    
    #Область УсловныеОператоры
    
    x = 0;
    // Корректное выражение
    Если x % 15 = 0 Тогда
        Результат = "FizzBuzz";
    ИначеЕсли x % 3 = 0 Тогда
        Результат = "Fizz";
    ИначеЕсли x % 5 = 0 Тогда
        Результат = "Buzz";
    Иначе
        Результат = x;
    КонецЕсли;
    
    // Выражение с ошибками
    ЕслИ x % 15 = 0 ТогдА
        Результат = "FizzBuzz";
    ИначеЕСли x % 3 = 0 Тогда
        Результат = "Fizz";
    ИначеЕсли x % 5 = 0 Тогда
        Результат = "Buzz";
    ИнаЧе
        Результат = x;
    КонецЕсЛи;
    
    #КонецОбласти
    
    #Область Циклы
    
    // Корректное выражение
    Для Каждого СтрокаДанных Из x Цикл
        Прервать;
        Продолжить;
    КонецЦикла;
    
    // Корректное выражение
    Для каждого СтрокаДанных Из x Цикл
        Прервать;
        Продолжить;
    КонецЦикла;
    
    // Корректное выражение
    Для Счетчик = 1 По 5 Цикл
        Сообщить(Счетчик);
    КонецЦикла;
    
    // Корректное выражение
    Пока x > 0 Цикл
         Сообщить(Счетчик);
         x = x + 1;
    КонецЦикла;
    
    // Выражение с ошибками
    ДЛЯ КАЖДОГО СтрокаДанных ИЗ x ЦикЛ
       ПРервать;
       ПРодолжить;
    КонецЦиклА;
    
    // Выражение с ошибками
    Для Счетчик = 1 ПО 5 Цикл
        Сообщить(Счетчик);
    КонецЦикла;
    
    // Выражение с ошибками
    ПокА x > 0 Цикл
        Сообщить(Счетчик);
        x = x + 1;
    КонецЦикла;
    
    #КонецОбласти
    
    #Область ЛогическиеВыражения
    
    // Корректное выражение
    А = А И А Или А И Не А;
    А = А И А ИЛИ А И НЕ А;
    А = Истина;
    А = Ложь;
    
    // Выражение с ошибками
    А = А и А ИлИ А И нЕ А;
    А = ЛОЖЬ;
    А = ИсТИна;
    
    #КонецОбласти
    
    #Область ОбработкаИсключений
    
    // Корректное выражение
    Попытка
        А = Б;
    Исключение
        ВызватьИсключение "Исключение";
    КонецПопытки;
    
    // Выражение с ошибками
    ПопЫтка
        А = Б;
    ИсключенИЕ
        ВызваТЬИсключение "Исключение";
    КОНЕЦПопытки;
    
    #КонецОбласти
    
    #Область Прочее
    
    // Корректное выражение
    Перейти ~Метка1;
    Выполнить(А);
    
    // Выражение с ошибками
    ПЕРейти ~Метка1;
    ВЫПОЛНИТЬ(А);
    
    ~Метка1: Сообщить("Осуществлен переход по метке.");
    
    #КонецОбласти
    
КонецПроцедуры
    
// Корректное выражение
Процедура Тест(Знач Параметр) Экспорт
    ДобавитьОбработчик Параметр.Action, Тест2;
    УдалитьОбработчик Параметр.Action, Тест2;
КонецПроцедуры
    
// Корректное выражение
Функция Тест2()
    Возврат Истина;
КонецФункции
    
// Выражение с ошибками
ПРОЦЕДУРА Тест3(ЗнаЧ Параметр) ЭКспорт
    ДОБавитьОбработчик Параметр.Action, Тест2;
    УДАлитьОбработчик Параметр.Action, Тест2;
КонецПРоцедуры
    
// Выражение с ошибками
ФункцИЯ Тест4()
    ВозВРат Истина;
КонецФункцИИ

// Корректное выражение
#Если Сервер Или Клиент Или МобильноеПриложениеКлиент Или МобильноеПриложениеСервер Или МобильныйКлиент Тогда
#ИначеЕсли ТолстыйКлиентОбычноеПриложение Или ТолстыйКлиентУправляемоеПриложение И ВнешнееСоединение Тогда
#ИначеЕсли ТонкийКлиент И ВебКлиент И Не НаКлиенте И Не НаСервере Тогда
#Иначе
#КонецЕсли

// Выражение с ошибками
#ЕСЛИ СеРвер ИЛи КлИЕнт Или МобильнОЕПриложениеКлиент Или МобильноеПриложениеСЕРВЕР Или МобильныйКЛИент ТОГДА
#ИначеЕСЛИ ТолстыйКЛИЕНТОбычноеПриложение Или ТолстыйКЛИЕНТУправляемоеПриложение И ВнешнееСоЕДИНение Тогда
#ИначеЕсли ТонкийКЛИЕНТ И ВЕБКлиент И нЕ НАКлиенте И Не НаСеРВере Тогда
#ИнАЧе
#КонецЕСЛИ

// Корректное выражение
&НаСервере
Процедура Тест()
КонецПроцедуры

// Выражение с ошибками
&НАСервере
Процедура Тест()
КонецПроцедуры

// Корректное выражение
&НаКлиенте
Процедура Тест()
КонецПроцедуры

// Выражение с ошибками
&НАКлиенте
Процедура Тест()
КонецПроцедуры

// Корректное выражение
&НаСервереБезКонтекста
Процедура Тест()
КонецПроцедуры

// Выражение с ошибками
&НАСервереБезКонтекста
Процедура Тест()
КонецПроцедуры

// Корректное выражение
&НаКлиентеНаСервереБезКонтекста
Процедура Тест()
КонецПроцедуры

// Выражение с ошибками
&НАКлиентеНаСервереБезКонтекста
Процедура Тест()
КонецПроцедуры

// Корректное выражение
&НаКлиентеНаСервере
Процедура Тест()
КонецПроцедуры

// Выражение с ошибками
&НАКлиентеНаСервере
Процедура Тест()
КонецПроцедуры

// Корректное выражение
#Область НоваяОбласть
#КонецОбласти

// Выражение с ошибками
#ОБЛАСТЬ НоваяОбласть
#КонецОбластИ

Procedure CheckCanonicalKeyword()

    #Region VarDefinition

    // Correct
    Var А;
    // Warning
    VAr Б;

    // Correct
    А = Undefined;
    Б = New Array();

    // Warning
    А = UNDEFined;
    Б = nEw Array();

    #EndRegion

    #Region ConditionalOperators

    x = 0;
    // Correct
    If x % 15 = 0 Then
        Результат = "FizzBuzz";
    ElsIf x % 3 = 0 Then
        Результат = "Fizz";
    ElsIf x % 5 = 0 Then
        Результат = "Buzz";
    Else
        Результат = x;
    EndIf;

    // Warning
    IF x % 15 = 0 TheN
        Результат = "FizzBuzz";
    ElsIF x % 3 = 0 Then
        Результат = "Fizz";
    ElsIf x % 5 = 0 Then
        Результат = "Buzz";
    ELSE
        Результат = x;
    ENDIf;

    #EndRegion

    #Область Cycles

    // Correct
    For Each СтрокаДанных In x Do
        Break;
        Continue;
    EndDo;

    // Correct
    For each СтрокаДанных In x Do
        Break;
        Continue;
    EndDo;

    // Correct
    For Счетчик = 1 To 5 Do
        Сообщить(Счетчик);
    EndDo;

    // Correct
    While x > 0 Do
         Сообщить(Счетчик);
         x = x + 1;
    EndDo;

    // Warning
    FOR EACH СтрокаДанных IN x DO
       BReak;
       ContiNue;
    EndDO;

    // Warning
    For Счетчик = 1 TO 5 Do
        Сообщить(Счетчик);
    EndDo;

    // Warning
    WHILe x > 0 Do
        Сообщить(Счетчик);
        x = x + 1;
    EndDo;

    #EndRegion

    #Region BooleanExpressions

    // Correct
    А = А And А Or А And Not А;
    А = А AND А OR А AND NOT А;
    А = True;
    А = False;

    // Warning
    А = А AnD А oR А And NOt А;
    А = FALSe;
    А = TruE;

    #EndRegion

    #Region Exceptions

    // Correct
    Try
        А = Б;
    Except
        Raise "Исключение";
    EndTry;

    // Warning
    TRY
        А = Б;
    EXcePt
        RAISE "Исключение";
    EndTrY;

    #КонецОбласти

    #Region Other

    // Correct
    Goto ~label1;
    Execute(А);

    // Warning
    GOTO ~label1;
    EXECUTE(А);

    ~label1: Message("Осуществлен переход по метке.");

    #EndRegion

EndProcedure

// Correct
Procedure Test5(Val Param) Export
    AddHandler Param.Action, Test6;
    RemoveHandler Param.Action, Test6;
EndProcedure

// Correct
Function Test6()
    Return True;
EndFunction

// Warning
PROCEDURE Test7(VaL Param) ExPort
    ADDHandler Param.Action, Test6;
    REMoveHandler Param.Action, Test6;
EndPROCedure

// Warning
FUNCtion Test8()
    RETUrn True;
EnDFunction

// Correct
#If Server OR Client Or MobileAppClient OR MobileAppServer OR MobileClient Then
#ElsIf ThickClientOrdinaryApplication OR ThickClientManagedApplication And ExternalConnection Then
#ElsIf ThinClient AND WebClient And NOT AtClient And Not AtServer Then
#Else
#EndIf

// Warning
#if ServeR oR CLient Or MOBileAppClient OR MOBileAppServer OR MOBileClient THen
#ELSIf THickClientOrdinaryApplication OR THickClientManagedApplication ANd EXTernalConnection Then
#ElsIf ThiNClient AND WEBClient And NoT ATClient And Not ATServer Then
#ElSe
#EndIF

// Correct
&AtServer
Procedure Test()
EndProcedure

// Warning
&ATServer
Procedure Test()
EndProcedure

// Correct
&AtClient
Procedure Test()
EndProcedure

// Warning
&ATClient
Procedure Test()
EndProcedure

// Correct
&AtServerNoContext
Procedure Test()
EndProcedure

// Warning
&AtServerNOContext
Procedure Test()
EndProcedure

// Correct
&AtClientAtServerNoContext
Procedure Test()
EndProcedure

// Warning
&AtClientAtServerNOContext
Procedure Test()
EndProcedure

// Correct
&AtClientAtServer
Procedure Test()
EndProcedure

// Warning
&AtClientATServer
Procedure Test()
EndProcedure

// Correct
#Region newRegion
#EndRegion

// Warning
#RegioN newRegion
#EndRegioN

    