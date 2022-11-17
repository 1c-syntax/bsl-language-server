# Duplicated code blocks in If...Then...ElseIf... statements (IfElseDuplicatedCodeBlock)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

**If...Then...ElseIf...** statement should not have duplicated code blocks.

## Examples

```bsl
If p = 0 Then
    t = 0;
ElseIf p = 1 Then
    t = 1;
ElseIf p = 2 Then
    t = 1;
Else
    t = -1;
EndIf;
```
