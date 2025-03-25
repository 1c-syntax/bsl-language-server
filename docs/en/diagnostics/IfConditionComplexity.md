# Usage of complex expressions in the "If" condition (IfConditionComplexity)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Complex expressions (with more than 3 boolean constructs) must be extracted to separated method or variable.

## Examples

Bad:

```bsl
If Id = "Expr1"
    Or Id = "Expr2"
    Or Id = "Expr3"
    Or Id = "Expr4"
    Or Id = "Expr5"
    Or Id = "Expr6"
    Or Id = "Expr7"
    Or Id = "Expr8"
    Or Id = "Expr9" Then

   doSomeWork();

EndIf; 
```

Good:

```bsl
If IsCorrectId(Id) Then
   doSomeWork();
КонецЕсли;

Function IsCorrectId(Id)

    Return Id = "Expr1"
            Or Id = "Expr2"
            Or Id = "Expr3"
            Or Id = "Expr4"
            Or Id = "Expr5"
            Or Id = "Expr6"
            Or Id = "Expr7"
            Or Id = "Expr8"
            Or Id = "Expr9";

EndFunction
```
