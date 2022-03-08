# Duplicated conditions in If...Then...ElseIf... statements (IfElseDuplicatedCondition)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

**If...Then...ElseIf...** statement should not have duplicated conditions.

## Examples

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
