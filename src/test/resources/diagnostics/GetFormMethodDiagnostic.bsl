Процедура Тест()
    Док=Документы.ЗаявкаНаОперацию.СоздатьДокумент();
    Форма=Док.ПолучитьФорму("ФормаДокумента"); // Срабатывание здесь
КонецПроцедуры

Процедура Тест2()
    ФормаРедактора = ПолучитьФорму("Обработка.УниверсальныйРедактор.Форма"); // срабатывание здесь
КонецПроцедуры

Procedure Test()
    Doc = Documents.PlanOperation.CreateDocument();
    Form = Doc.GetForm("DocumentForm"); // срабатывание здесь
EndProcedure

Procedure Test2()
    Form = GetForm("Document.PlanOperation.Form"); // срабатывание здесь
EndProcedure
