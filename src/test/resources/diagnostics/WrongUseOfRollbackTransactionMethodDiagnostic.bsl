Функция Тест()
    НачатьТранзакцию();
    Попытка
        ЗафиксироватьТранзакцию();
    Исключение
        Сообщить("Сообщение");
        Сообщить("Сообщение");
        ОтменитьТранзакцию();  // Срабатывание здесь
    КонецПопытки;

    НачатьТранзакцию();
    ОтменитьТранзакцию();  // Срабатывание здесь
    Возврат;
КонецФункции

Function Test()

BeginTransaction();
Attempt
    DataLock = New DataLock;
    DataLockElement = DataLock.Add("Document.ReceiptNote");
    DataLockElement.SetValue("Reference", ReferenceForProcessing);

    DocumentObject.Record();

    CommitTransaction();
Exception
    DocumentObject.Record();
    DocumentObject.Record();
    RollbackTransaction();  // Срабатывание здесь

    Return;
EndFunction
