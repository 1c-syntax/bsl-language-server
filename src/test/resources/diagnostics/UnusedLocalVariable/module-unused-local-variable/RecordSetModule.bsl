Перем ДляВнешнегоКода;

Процедура Проверка(Строка)
  Справочник1 = Строка.Справочник1;
  Сообщить(Строка.Справочник1);
  Отбор = Неопределено;
КонецПроцедуры

Процедура Исправлено(Строка)
  Справочник1 = Строка.Справочник1;
  Сообщить(справочник1);
  Filter = Undefined;
КонецПроцедуры

Procedure CheckLoops(Count)
  For Index = 1 To Count Do
    Message(Count);
  EndDo;
  For Each UnusedItem In ThisObject Do
    Message(Count);
  EndDo;
EndProcedure
