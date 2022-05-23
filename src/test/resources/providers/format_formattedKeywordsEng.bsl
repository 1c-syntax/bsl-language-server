Async Procedure тест(Val парам1) Export
  Var тест;
  
  If условие AND УсловиеДва OR NOT Условие3 Then
  ElsIf Условие4 Then
  Else
  EndIf;
  
  While условие Do
    Break;
  EndDo;
  
  For Each строка In таблица Do
    Continue;
  EndDo;
  
  Try
    объект = New Массив;
  Except
    Raise "исключение";
  EndTry
  
  Goto ~Метка1;
  ~Метка1 : сообщить("привет");
  
  AddHandler Накладная.ПриЗаписи, Обработка.ПриЗаписиДокумента;
  RemoveHandler Накладная.ПриЗаписи, Обработка.ПриЗаписиДокумента;
  
  Await call();
EndProcedure

Function тест2()
EndFunction