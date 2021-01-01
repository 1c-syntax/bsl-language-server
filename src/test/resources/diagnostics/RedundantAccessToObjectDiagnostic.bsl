Процедура Тест(Данные)

    ЭтотОбъект.Контрагент                  = Данные.Контрагент; // Ошибка
    ЭтотОбъект.Договор                     = Данные.Договор;    // Ошибка
    ЭтотОбъект["ПолеКонтактнойИнформации"] = Данные.Телефон;    // Тут ошибки быть не должно

    Переменная  = ЭтотОбъект.Значение;    // Ошибка
    Переменная2 = ЭтотОбъект["Значение"]; // Тут ошибки быть не должно
    Переменная3 = ЭтотОбъект.Значение();  // Ошибка

    ЭтотОбъект.ВыполнитьЗаполнениеПоСтруктуре(Данные);          // Ошибка

КонецПроцедуры

Procedure Test(Data)
    
    ThisObject.Counterparty               = Data.Counterparty; // Error
    ThisObject.Contract                   = Data.Contract;     // Error
    ThisObject["ContactInformationField"] = Data.Phone;

    Variable = ThisObject.Value; // Error

    ThisObject.RunFillByStructure(Data); // Error
    ThisObject["ContactInformationField"].Value = Data.Phone;
    ThisObject["ContactInformationField"].Value2 = ThisObject["ContactInformationField"].Value;

EndProcedure
