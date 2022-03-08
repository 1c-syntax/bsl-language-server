# The function always returns the same primitive value (FunctionReturnsSamePrimitive)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

A function should not return the same primitive value. If the result of the function isn't use into code, then you need the function rewrite to the procedure.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad:
```bsl
Function CheckString(Val RowTable)

    If ItsGoodString(RowTable) Then
        ActionGood();
        Return True;
    ElsIf ItsNodBadString(RowTable) Then
        ActionNoBad();
        Return True;
     Else
        Return True;
    EndIf;

EndFunction
```

Good:
```bsl
Function CheckString(Val RowTable)

    If ItsGoodString(RowTable) Then
        ActionGood();
    ElsIf ItsNodBadString(RowTable) Then
        ActionNoBad();
    Else
        ActionElse();
    EndIf;

EndFunction
```

## Nuances

Attachable functions excluded from the scan. Example:
```bsl
Function Attachable_RandomAction(Command)

    If ValueIsFilled(CurrentDate) Then
        Return Undefined;
    EndIf;

    Return Undefined;

EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
