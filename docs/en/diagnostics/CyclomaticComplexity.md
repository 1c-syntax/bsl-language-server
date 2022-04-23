# Cyclomatic complexity (CyclomaticComplexity)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Cyclomatic complexity of the program code is one of the oldest metrics, it was first mentioned by Thomas McCab in 1976.  
Cyclomatic complexity shows the minimum number of required tests. The most effective way to reduce cyclomatic complexity is to decompose the code, split the methods into simpler ones, and also optimize logical expressions.

Cyclomatic complexity increases by 1 for each of following constructions

- `For ... To .. Do`
- `For each ... Of ... Do`
- `If ... Then`
- `ElsIf ... Then`
- `Else`
- `Try ... Except ... EndTry`
- `GoTo ~Label`
- Binary operations `AND ... OR`
- Ternary operator
- `Procedure`
- `Function`

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
Function ServerModuleManager(Name)                                                      // 1
    ObjectFounded = False;                                                              // 0
                                                                                        // 0
    NameParts = StrSplit(Name, ".");                                                    // 0
    If NameParts.Count() = 2 Then                                                       // 1
                                                                                        // 0
        TypeName = Upper(NameParts[0]);                                                 // 0
        ObjectName = NameParts[1];                                                      // 0
                                                                                        // 0
        If TypeName = Upper("Constants") Then                                           // 1
            If Metadata.Constants.Find(ObjectName) <> Undefined Then                    // 1
                ObjectFounded = True;                                                   // 0
            EndIf;                                                                      // 0
        ElsIf TypeName = Upper("InformationRegisters") Then                            // 1
            If Metadata.InformationRegisters.Find(ObjectName) <> Undefined Then         // 1
                ObjectFounded = True;                                                   // 0
            EndIf;                                                                      // 0
        Else                                                                            // 1
            ObjectFounded = False;                                                      // 0
        EndIf;                                                                          // 0
    EndIf;                                                                              // 0
                                                                                        // 0
    If Not ObjectFounded Then                                                           // 1
        Raise СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(                  // 0
            НСтр("ru = 'Объект метаданных ""%1"" не найден,                             // 0
                       |либо для него не поддерживается получение модуля менеджера.'"), // 0
            Name);                                                                      // 0
    EndIf;                                                                              // 0
    SetSafeMode(True);                                                                  // 0
    Module = Eval(Name);                                                                // 0
    F = ?(SomeCondition1, True, Undefined);                                             // 1
    А = ?(SomeCondition1, True, ?(SomeCondition2, False, Undefined));                   // 2
    M = True Or 7;                                                                      // 1
    Return Module;                                                                      // 0
EndFunction                                                                              // Total 12
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Cyclomatic Complexity PHP](https://pdepend.org/documentation/software-metrics/cyclomatic-complexity.html)
* [Cyclomatic Complexity (RU)](https://ru.wikipedia.org/wiki/%D0%A6%D0%B8%D0%BA%D0%BB%D0%BE%D0%BC%D0%B0%D1%82%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%B0%D1%8F_%D1%81%D0%BB%D0%BE%D0%B6%D0%BD%D0%BE%D1%81%D1%82%D1%8C)
