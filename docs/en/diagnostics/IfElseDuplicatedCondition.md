# Duplicated conditions in If...Then...ElseIf... statements (IfElseDuplicatedCondition)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |     Tags     |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:------------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |          `10`           | `suspicious` |

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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IfElseDuplicatedCondition-off
// BSLLS:IfElseDuplicatedCondition-on
```

### Parameter for config

```json
"IfElseDuplicatedCondition": false
```
