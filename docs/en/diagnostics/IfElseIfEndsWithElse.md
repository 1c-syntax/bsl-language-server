# Using If...Then...ElsIf... statement

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Major` | `Нет` | `10` | `badpractice` |


## TODO PARAMS

## Description

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
