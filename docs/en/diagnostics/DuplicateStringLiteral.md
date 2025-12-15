# Duplicate string literal (DuplicateStringLiteral)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It is bad form to use the same string literals multiple times in the same module or method:
- it can lead to problems with further maintenance, if necessary, change the value - there is a high probability of missing one of the repetitions
- it can be a consequence of "copy-paste" - the developer may have forgotten to change the code after copying a similar block of code.

### Features of the implementation of diagnostic

- Diagnostics with default settings does not respect the case of literal characters - the strings ` AAAA ` and ` AaaA ` are considered the same.
- You cannot specify a minimum parsed literal value less than the default. Short service literals are often used, which will generate unnecessary comments. For example: empty string "", selector numbers "1", "0", etc.
- You cannot reduce the allowed number of repetitions to less than 1, because it makes no practical sense.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad code

```bsl
Procedure Test(Param)
    Result = "Value";
    If Param = "One" Then
        Result = Result + One("Value");
    Else
        Result = Result + Two("Value");
    EndIf; 
EndProcedure
```

Сorrected:

```bsl
Procedure Test(Param)
    Result = "Value";
    If Param = "One" Then
        Result = Result + One(Result);
    Else
        Result = Result + Two(Result);
    EndIf; 
EndProcedure
```

Bad code

```bsl
Procedure Test2(Param)
    Result = "Value";
    If Param = "One" Then
        Result = Result + One("Value");
    Else
        Result = Result + Two("Value");
    EndIf; 
EndProcedure

Procedure Test3(Param)
    If Param = "Five" Then
        Result = Result + Five("Value");
    EndIf; 
EndProcedure
```

Сorrected

```bsl
Procedure Test2(Param)
    Result = "Value";
    If Param = "One" Then
        Result = Result + One(StringValue());
    Else
        Result = Result + Two(StringValue());
    EndIf; 
EndProcedure

Procedure Test3(Param)
    If Param = "Five" Then
        Result = Result + Five(StringValue());
    EndIf; 
EndProcedure

Function StringValue()
   Return "Value";
EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
