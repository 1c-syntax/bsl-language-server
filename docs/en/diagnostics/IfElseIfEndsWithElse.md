# Else...The...ElseIf... statement should end with Else branch.

**If...Then...ElseIf...** statement should end with  **Else** branch.

Example:

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
