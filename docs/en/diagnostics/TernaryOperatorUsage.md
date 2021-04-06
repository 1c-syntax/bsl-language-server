# Ternary operator usage (TernaryOperatorUsage)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |      Tags       |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------:|
 | `Code smell` | `BSL`<br>`OS` | `Minor`  |             `No`              |           `3`           | `brainoverload` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Instead of the ternary operator, use the "If-else" construct.

## Examples

Bad:

```bsl
Result = ?(X%15 <> 0, ?(X%5 <> 0, ?(X%3 <> 0, x, "Fizz"), "Buzz"), "FizzBuzz"); 
```

Good:

```bsl
If x% 15 = 0 Then
    Result = "FizzBuzz";
ElseIf, if x% 3 = 0 Then
    Result = "Fizz";
ElseIf, if x% 5 = 0 Then
    Result = "Buzz";
Else
    Result = x;
EndIf;
```

Bad:

```bsl
If ?(P.Emp_emptype = Null, 0, PageEmp_emptype) = 0 Then
      Status = "Done";
EndIf;
```
Good:

```bsl
If PageEmp_emptype = Null OR PageEmp_emptype = 0 Then
      Status = "Done";
End If;
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:TernaryOperatorUsage-off
// BSLLS:TernaryOperatorUsage-on
```

### Parameter for config

```json
"TernaryOperatorUsage": false
```
