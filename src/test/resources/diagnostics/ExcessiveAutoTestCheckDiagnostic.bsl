
Процедура ПриСозданииНаСервере()

    Если Параметры.Свойство("АвтоТест") Тогда // Ругаемся

        Возврат;

    КонецЕсли;

КонецПроцедуры

Процедура ОбработкаЗаполнения(ДанныеЗаполнения, ТестЗаполения, СтандартнаяОбработка)

    // Пропускаем обработку, чтобы гарантировать получение формы при передаче параметра "АвтоТест"
    Если ДанныеЗаполнения = "АвтоТест" Тогда // Ругаемся
        Возврат;
    КонецЕсли;

КонецПроцедуры

Процедура ПроверитьВыполение(Перечень)

    Если Перечень.Свойство("АвтоТест") Тогда // Ругаемся так как могло быть передано из формы

        Возврат;

    КонецЕсли;

КонецПроцедуры

Процедура БезОшибок()

    Перечень.Вставить("АвтоТест", "АвтоТест");

    Если Перечень.Свойство("АвтоТест") Тогда // Тут не ругаемся

        ВыполняемДействиеСПеречнем(Перечень);
        Возврат;

    КонецЕсли;

КонецПроцедуры

&AtServer
Procedure OnCreateAtServer()

    If Parameters.Property("AutoTest") Then // Issue
        Return;
    EndIf;

EndProcedure

Procedure Filling()

    If VariableName = "AutoTest" Then // Issue
        Return;
    EndIf;

EndProcedure

Procedure Check(List)

    If List.Property("AutoTest") Then // Issue

        Return;

    EndIf;

EndProcedure

Procedure NoError(List)

    If List.Property("AutoTest") Then // No error

        List.Delete("AutoTest");
        Return;

    EndIf;

EndProcedure

Если Отказ Тогда

    Возврат;

КонецЕсли;

If List.Property("AutoTest") Then // No error

    List.Delete("AutoTest");

EndIf;