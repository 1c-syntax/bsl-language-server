# Duplicated code blocks in If...Then...ElseIf... statements

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `Нет` | `10` | `suspicious` |

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
