# Nested ternary operator (NestedTernaryOperator)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |      Tags       |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------:|
 | `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `5`           | `brainoverload` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Use of nested ternary operators decrease code readability.

## Examples

### Incorrect use of ternary operators

```bsl
Result = ?(X%15 <> 0, ?(X%5 <> 0, ?(X%3 <> 0, x, "Fizz"), "Buzz"), "FizzBuzz"); 
```

```bsl
If ?(P.Emp_emptype = Null, 0, PageEmp_emptype) = 0 Then

      Status = "Done";

EndIf;
```

### Possible implementation

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

```bsl
If PageEmp_emptype = Null OR PageEmp_emptype = 0 Then

      Status = "Done";

End If;
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:NestedTernaryOperator-off
// BSLLS:NestedTernaryOperator-on
```

### Parameter for config

```json
"NestedTernaryOperator": false
```
