# Duplicated conditions in If...Then...ElsIf...

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Major` | `Нет` | `10` | `suspicious` |


## TODO PARAMS

## Description

# Duplicated conditions in If...Then...ElseIf... statements

**If...Then...ElseIf...** statement should not have duplicated conditions.

Example:

```bsl
If p = 0 Then
    t = 0;
ElseIf p = 1 Then
    t = 1;
ElseIf p = 1 Then
    t = 2;
Else
    t = -1;
EndIf;
```
