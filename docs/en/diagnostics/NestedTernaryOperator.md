# Nested Ternary Operator

Use of nested ternary operators decrease code readability.

Samples of wrong usage:

```bsl
Result = ?(X%15 <> 0, ?(X%5 <> 0, ?(X%3 <> 0, x, "Fizz"), "Buzz"), "FizzBuzz");
```

```bsl
If ?(P.Emp_emptype = Null, 0, PageEmp_emptype) = 0 Then

      Status = "Done";

EndIf;
```

Possible refactoring:

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
