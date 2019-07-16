//good
Для Каждого Элемент Из Коллекция Цикл
    Если Элемент < 10 Тогда
        НеЭтаКоллекция.Удалить(Элемент);
    КонецЕсли;
КонецЦикла;

//good
Для Каждого Элемент Из Коллекция Цикл
    Если Элемент < 10 Тогда
        Удалить(Элемент);
    КонецЕсли;
КонецЦикла;

//error1
Для Каждого Элемент Из Коллекция.ЕщеКоллекция Цикл
    Если Элемент < 10 Тогда
        Коллекция.ЕщеКоллекция.Удалить(Элемент);
    КонецЕсли;
КонецЦикла;

//error2
for each elem in mass do
    mass.delete(elem);
enddo;

//error3
for each elem in mass do
    mass.delete( (elem ));
enddo;

//error4
Для Каждого Элемент Из Коллекция Цикл
    Коллекция.Удалить(Элемент);
КонецЦикла;

//error5
Для Каждого Элемент Из Коллекция Цикл
    Если Элемент < 10 Тогда
        Коллекция.Удалить(Элемент);
    КонецЕсли;
КонецЦикла;

//error6
for each elem in mass do
    mass.delete(elem+1);
enddo;

//error7
for each elem in mass.mass1().mass2 do
    mass.mass1().mass2.delete(elem+1);
enddo;

//error7
for each elem in mass().mass1.mass2() do
    mass().mass1.mass2().delete(elem+1);
enddo;
