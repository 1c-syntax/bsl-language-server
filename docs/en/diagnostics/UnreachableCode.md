# Unreachable Code (UnreachableCode)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |              Tags              |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------:|
| `Error` | `BSL`<br>`OS` | `Minor`  |             `Yes`             |          `10`           | `design`<br>`suspicious` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Code located after operators "Return", "GoTo", "Raise", "Break", "Continue" never will be executed.

Errors of unreachable code can be caused by developer carelessness when editing another's code.

## Examples

```bsl
Procedure Example()
    Return;
    // Code below operator Return will never be executed
    For each Line from Lines Do
        If Condition2 Then
            Method();
        EndIf;
    EndDo;
EndProcedure
```

```bsl
Function Example(Parameter1, Parameter2)
    If Error Then
        Raise "Error occurred";
        // After rise exception the code bellow will be ignored
        Parameter1 = Parameter2;
    EndIf; 
    Return Parameter1;
EndFunction
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnreachableCode-off
// BSLLS:UnreachableCode-on
```

### Parameter for config

```json
"UnreachableCode": false
```
