# Usage of complex expressions in the "If" condition (IfConditionComplexity)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Minor` | `Yes` | `5` | `brainoverload` 

## Parameters 

 Name | Type | Description | Default value 
 :-: | :-: | :-- | :-: 
 `maxIfConditionComplexity` | `Integer` | ```Acceptable number of logical expressions in operator If condition``` | ```3``` 

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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IfConditionComplexity-off
// BSLLS:IfConditionComplexity-on
```

### Parameter for config

```json
"IfConditionComplexity": {
    "maxIfConditionComplexity": 3
}
```
