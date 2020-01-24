Процедура Тест()
    Форма=Док.ПолучитьФорму("ФормаДокумента");
    ДФ = Форма.ДанныеФормыВЗначение(Объект, Тип("ТаблицаЗначений")); // Срабатывание здесь
КонецПроцедуры

Процедура Тест2()
    ДФ = ДанныеФормыВЗначение(Объект, Тип("ТаблицаЗначений")); // срабатывание здесь
КонецПроцедуры

Procedure Test()
    Form = Doc.GetForm("DocumentForm");
    FD = Form.FormDataToValue(Object, Type("ValueTable")); // срабатывание здесь
EndProcedure

Procedure Test2()
    FormDataToValue(Object, Type("ValueTable")); // срабатывание здесь
EndProcedure