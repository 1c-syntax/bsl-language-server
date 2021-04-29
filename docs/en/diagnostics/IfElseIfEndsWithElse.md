# Else...The...ElseIf... statement should end with Else branch (IfElseIfEndsWithElse)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |     Tags      |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------:|
 | `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `badpractice` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Syntax construct **If ... Then ... ElseIf ...** must end with the **Else** branch.

## Examples

```bsl
If x % 15 = 0 Then
    Result = "FizzBuzz";
ElseIf x % 3 = 0 Then
    Result = "Fizz";
ElseIf x % 5 = 0 Then
    Result = "Buzz";
Else
    Result = x;
EndIf;
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IfElseIfEndsWithElse-off
// BSLLS:IfElseIfEndsWithElse-on
```

### Parameter for config

```json
"IfElseIfEndsWithElse": false
```
