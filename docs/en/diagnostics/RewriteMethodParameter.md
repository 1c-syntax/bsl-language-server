# Rewrite method parameter (RewriteMethodParameter)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
It is wrong to write methods in which their arguments are overwritten immediately on entry.

It is necessary to correct this deficiency by removing the parameters, converting them to local variables.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Suspicious code
```bsl
Procedure Configor(Val ConnectionString, Val User = "", Val Pass = "") Export
  ConnectionString = "/F""" + DataBaseDir + """"; // Error
...
EndProcedure
```

Сorrected:
```bsl
Procedure Configor(Val DataBaseDir, Val User = "", Val Pass = "") Export
ConnectionString = "/F""" + DataBaseDir + """"; // No error
...
EndProcedure
```
or
```bsl
Procedure Configor(Val DataBaseDir, Val User = "", Val Pass = "") Export
 If Not EmpyString(DataBaseDir) Then
NewConnectionString = "/F""" + DataBaseDir + """";
Else
NewConnectionString = ConnectionString; // Hmm, where is this from?
EndIf;

...
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* [PVS-Studio V763. Parameter is always rewritten in function body before being used](https://pvs-studio.com/ru/docs/warnings/v6023)
