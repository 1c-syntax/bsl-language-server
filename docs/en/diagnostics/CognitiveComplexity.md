# Cognitive complexity (CognitiveComplexity)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Cognitive complexity shows how difficult it is to perceive the written code.  
High cognitive complexity clearly indicates the need for refactoring to make future support easier.  
The most effective way to reduce cognitive complexity is to decompose the code, split the methods into simpler ones, and also optimize logical expressions.

### Cognitive Complexity calculation

Bellow are given code analysis rules, conditions increase cognitive complexity.

#### Each next block increases complexity by 1

```bsl

// Loop `For each`
For Each Element in Collection Do                    // +1
EndDo;

// Loop `For`
For i = StartValue To EndValue Do                    // +1
EndDo;

// Loop `While`
While Condition Do                                   // +1
EndDo;


// Condition
If Condition1 Then                                   // +1

// Alternative condition branch
ElseIf Condition2 Then                               // +1

// default branch
Else
EndIf;

// ternary operator
Value = ?(Condition, ValueTrue, ValueFalse);         // +1

Try
// Exception handling
Except                                               // +1
EndTry;

// Go to label
Goto ~Label;                                          // +1

// Binary logical operations

While Condition1 Or Condition2 Do                    // +2
EndDo;

If Condition1 And Condition2 Then                    // +2

ElseIf Condition2                                    // +1
        Or Condition3 And Condition4 Then            // +2

EndIf;

Value = ?(Condition1 Or Condition2 Or Not Condition3,// +3
                ValueTrue, ValueFalse);

Value = First Or Second;                             // +1

Value = A <> B;                                      // +1

```

#### For each nesting level, next blocks get additional 1 to complexity


```bsl

// Loop `For each`
For Each Element in Collection Do
EndDo;

// Loop `For`
For i = StartValue To EndValue Do
EndDo;

// Loop `While`
While Condition Do
EndDo;

// Condition
If Condition1 Then
EndIf;

// ternary operator
Value = ?(Condition, ValueTrue, ValueFalse);

Try
// Exception handling
Except
EndTry;

~Label:

```

#### Alternative branches, binary operations, and go to label do not increase cognitive complexity when nested

## Examples

Bellow are code examples and their cognitive complexity calculation.

```bsl
Функция Пример1(ТипКласса)
    Если ТипКласса.Неизвестен() Тогда                                                  // +1, условие, вложенности нет
        Возврат Символы.НеизвестныйСимвол;
    КонецЕсли;

    НеизвестностьНайдена = Ложь;
    СписокСимволов = ТипКласса.ПолучитьСимвол().Потомки.Поиск("имя");
    Для Каждого Символ Из СписокСимволов Цикл                                          // +1, цикл, вложенности нет
        Если Символ.ИмеетТип(Символы.Странное)                                         // +2, условие вложенное в цикл, вложенность 1
            И НЕ Символы.Экспортный() Тогда                                            // +1, логическая операция, вложенность не учитывается

            Если МожноПереопределить(Символ) Тогда                                     // +3, вложенное условие, вложенность 2
                Переопределяемость = ПроверитьПереопределяемость(Символ, ТипКласса);
                Если Переопределяемость = Неопределено Тогда                           // +4, вложенное условие, вложенность 3
                    Если НЕ НеизвестностьНайдена Тогда                                 // +5, вложенное условие, вложенность 4
                        НеизвестностьНайдена = Истина;
                    КонецЕсли;
                ИначеЕсли Переопределяемость Тогда                                     // +1, альтернативная ветвь условия, вложенность не учитывается
                    Возврат Символ;
                КонецЕсли;
            Иначе                                                                      // +1, ветвь по-умолчанию, вложенность не учитывается
                Продолжить;
            КонецЕсли;
        КонецЕсли;
    КонецЦикла;

    Если НеизвестностьНайдена Тогда                                                   // +1, вложенности нет
        Возврат Символы.НеизвестныйСимвол;
    КонецЕсли;

    Возврат Неопределено;
КонецФункции

```

```bsl
Function Example2(Document)
    StartTransaction();
    NeedPost = ?(Document.Posted, FALSE,                                                         // +1, ternary operator
                                        ?(Document.DeletionMark, FALSE, TRUE));                  // +2, nested ternary operator, nesting 1
    Try                                                                                          // +0, try increases nesting level
        DocumentObject = Document.GetObject();
        If DocumentObject.Posted Then                                                            // +2, nested condition, nesting 1
            For Each TabularSectionLine Из DocumentObject.TabularSection Do                      // +3, nested loop, nesting 2
                If TabularSectionLine.Column1 = 7                                                // +4, nested condition, nesting 3
                        OR TabularSectionLine.Column2 = 7 Then                                   // +1, logical operation, nesting not taken into account
                    Continue;
                EndIf;
                If TabularSectionLine.Column4 > 1 Then                                          // +4, nested condition, nesting 3
                    Break;
                Else                                                                            // +1, default branch, nesting not taken into account
                    If TabularSectionLine.Column1 + TabularSectionLine.Column2 = 2 Then         // +5, nested condition, nesting 4
                        TabularSectionLine.Column10 = TabularSectionLine.Column1 * 2;
                    EndIf;
                EndIf;
            EndDo;
        Else                                                                                    // +1, default branch, nesting not taken into account
            NeedPost = DocumentObject.Date > CurrentDate();                                     // +1, logical operation, nesting not taken into account
            Goto ~Label;                                                                        // +1, go to label, nesting not taken into account
        EndIf;

        If NeedPost Then                                                                        // +2, nested condition, nesting 1
            DocumentObject.Write(DocumentWriteMode.Posting);
        ElseIf NOT NeedPost Then                                                                // +1, alternative branch, nesting not taken into account
            DocumentObject.Write(DocumentWriteMode.Write);
        Else                                                                                    // +1, default branch, nesting not taken into account
            Raise "Why?";
        EndIf;
    Except                                                                                      // +1, except processing
        RetryWrite = FALSE;
        Try                                                                                     // +0, try, increases nesting level
            If DocumentObject.Posted Then                                                       // +3, nested condition, nesting 2
                DocumentObject.Write(DocumentWriteMode.Write);
            EndIf;
        Raise                                                                                   // +2, except processing, nesting 1
            RetryWrite = ИСТИНА;
        EndTry;
        If NOT RetryWrite Then                                                                  // +2, nested condition, nesting 1
            While TransactionIsActive() Do                                                      // +3, nested loop, nesting 2
                CancelTransaction();
            EndDo;
        EndIf;
        Raise "Error"
    EndTry;

    ~Label:
    Return Undefined;
EndFunction

```

## Sources

* [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf)
