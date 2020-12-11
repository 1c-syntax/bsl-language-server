Процедура Тест(Данные)

    ЭтотОбъект.Контрагент                  = Данные.Контрагент; // Ошибка
    ЭтотОбъект.Договор                     = Данные.Договор;    // Ошибка
    ЭтотОбъект["ПолеКонтактнойИнформации"] = Данные.Телефон;    // Тут ошибки быть не должно

    Переменная  = ЭтотОбъект.Значенние;    // Ошибка
    Переменная2 = ЭтотОбъект["Значенние"]; // Ошибка
    Переменная3 = ЭтотОбъект.Значенние();  // Ошибка

    ЭтотОбъект.ВыполнитьЗаполнениеПоСтруктуре(Данные);          // Ошибка

КонецПроцедуры

Procedure Test(Data)
    
    ThisObject.Counterparty               = Data.Counterparty; // Error
    ThisObject.Contract                   = Data.Contract;     // Error
    ThisObject["ContactInformationField"] = Data.Phone;

    Variable = ThisObject.Value; // Error

    ThisObject.RunFillByStructure(Data); // Error

EndProcedure
