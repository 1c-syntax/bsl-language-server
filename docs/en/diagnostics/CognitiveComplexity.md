# Cognitive complexity

Cognitive complexity shows how difficult it is to perceive the written code.

High cognitive complexity clearly indicates the need for refactoring to make future support easier.
 
The most effective way to reduce cognitive complexity is to decompose the code, split the methods into simpler ones, and also optimize logical expressions.

## Parameters

- `complexityThreshold` - `Integer` - Acceptable cognitive complexity of the method. Default value: 15.
- `checkModuleBody` - `Boolean` - Check module body. Default value: Yes.

## Cognitive Complexity calculation

Bellow are given code analysis rules, conditions increase cognitive complexity.
**Reference**: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf)

### Each next block increases complexity by 1

```bsl

// Цикл `Для каждого`
Для каждого Элемент Из Коллекция Цикл                // +1
КонецЦикла;

// Цикл `Для`
Для Ит = Начало По Конец Цикл                        // +1
КонецЦикла;

// Цикл `Пока`
Пока Условие Цикл                                    // +1
КонецЦикла;


// Условие
Если Условие Тогда                                   // +1

// Альтернативная ветвь условия
ИначеЕсли Условие2 Тогда                             // +1

// Ветвь по-умолчанию
Иначе                                                // +1
КонецЕсли;

// Тернарный оператор
Значение = ?(Условие, ЗначениеИстина, ЗначениеЛожь); // +1

Попытка
// Обработка исключения
Исключение                                           // +1
КонецПопытки;

// Переход на метку
Перейти ~Метка;                                      // +1

// Бинарные логические операции

Пока Условие ИЛИ Условие2 Цикл                       // +2
КонецЦикла;

Если Условие И Условие2 Тогда                        // +2

ИначеЕсли Условие2                                   // +1
        ИЛИ Условие3 И Условие4 Тогда                // +2

КонецЕсли;

Значение = ?(Условие ИЛИ Условие2 ИЛИ НЕ Условие3,   // +3
                ЗначениеИстина, ЗначениеЛожь); 

Значение = Одно ИЛИ Второе;                          // +1

Значение = А <> B;                                   // +1

```

### For each nesting level, next blocks get additional 1 to complexity

```bsl

// Цикл `Для каждого`
Для каждого Элемент Из Коллекция Цикл
КонецЦикла;

// Цикл `Для`
Для Ит = Начало По Конец Цикл
КонецЦикла;

// Цикл `Пока`
Пока Условие Цикл
КонецЦикла;


// Условие
Если Условие Тогда
КонецЕсли;

// Тернарный оператор
Значение = ?(Условие, ЗначениеИстина, ЗначениеЛожь);

Попытка
// Обработка исключения
Исключение
КонецПопытки;

~Метка:

```

### Alternative branches, binary operations, and go to label do not increase cognitive complexity when nested.

## `Cognitive complexity` examples

Bellow are code examples and their cognitive complexity calculation.

```bsl
Function Example1(ClassType)
    If ClassType.Unknown() Then                                                  // +1, condition expression, no nesting
        Return Chars.UnknownSymbol;
    EndIf;

    AmbiguityFound = False;
    ListSymbols = ClassType.GetSymbol().Children.Find("name");
    For Each Symbol in ListSymbols Do
// +1, loop, no nesting
        If Symbol.HasType(Symbols.Strage)                                         // +2, condition nested in loop, nesting 1
            AND NOT Symbols.Export() Then                                            // +1, logival operation, nesting not taken into account

            If CanOverride(Symbol) Then                                     // +3, nested condition, nesting 2
                Overrideability = CheckOverrideability(Symbol, ClassType);
                If Overrideability = Undefined Then                           // +4, nested condition, nesting 3
                    If NOT AmbiguityFound Then                                 // +5, nested condition, nesting 4
                        AmbiguityFound = True;
                    EndIf;
                ElseIf Overrideability Then                                     // +1, alternative condition branch, nesting not taken into account
                    Return Symbol;
                EndIf;
            Else                                                                      // +1, default branch, nesting not taken into account
                Continue;
            EndIf;
        EndIf;
    EndDo;

    If AmbiguityFound Then                                                   // +1, no nesting
        Return Symbols.UnknownSymbol;
    EndIf;

    Return Undefined;
EndFunction

```

```bsl
Function Example2(Document)
    StartTransaction();
    NeedPost = ?(Document.Posted, FALSE,                                                        // +1, ternary operator
                                        ?(Document.DeletionMark, FALSE, TRUE));                  // +2, nested ternary operator, nesting 1
    Try                                                                                          // +0, try increases nesting level
        DocumentObject = Document.GetObject();
        If DocumentObject.Posted Then                                                           // +2, nested condition, nesting 1
            For Each TabularSectionLine Из DocumentObject.TabularSection Do
                    // +3, nested loop, nesting 2
                If TabularSectionLine.Column1 = 7                                               // +4, nested condition, nesting 3
                        OR TabularSectionLine.Column2 = 7 Then                                  // +1, logical operation, nesting not taken into account
                    Continue;
                EndIf;
                If TabularSectionLine.Column4 > 1 Then                                         // +4, nested condition, nesting 3
                    Break;
                Else                                                                               // +1, default branch, nesting not taken into account
                    If TabularSectionLine.Column1 + TabularSectionLine.Column2 = 2 Then     // +5, nested condition, nesting 4
                        TabularSectionLine.Column10 = TabularSectionLine.Column1 * 2;
                    EndIf;
                EndIf;
            EndDo;
        Else
// +1, default branch, nesting not taken into account
            NeedPost = DocumentObject.Date > CurrentDate();                                      // +1, logical operation, nesting not taken into account
            Goto ~Label;                                                                          // +1, go to label, nesting not taken into account
        EndIf;

        If NeedPost Then                                                                      // +2, nested condition, nesting 1
            DocumentObject.Write(DocumentWriteMode.Posting);
        ElseIf NOT NeedPost Then                                                              // +1, alternative branch, nesting not taken into account
            DocumentObject.Write(DocumentWriteMode.Write);
        Else                                                                                        // +1, default branch, nesting not taken into account
            Raise "Why?";
        EndIf;
    Except
// +1, except processing
        RetryWrite = FALSE;
        Try
// +0, try, increases nesting level
            If DocumentObject.Posted Then                                                       // +3, nested condition, nesting 2
                DocumentObject.Write(DocumentWriteMode.Write);
            EndIf;
        Raise
// +2, except processing, nesting 1
            RetryWrite = ИСТИНА;
        EndTry;
        If NOT RetryWrite Then                                                                // +2, nested condition, nesting 1
            While TransactionIsActive() Do                                                             // +3, nested loop, nesting 2
                CancelTransaction();
            EndDo;
        EndIf;
        Raise "Error"
    EndTry;

    ~Label:
    Return Undefined;
EndFunction

```
