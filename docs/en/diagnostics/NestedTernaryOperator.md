# Nested ternary operator (NestedTernaryOperator)

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
