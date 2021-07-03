# Statement should end with semicolon symbol ";" (SemicolonPresence)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |               Tags                |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Minor`  |             `Yes`             |           `1`           | `standard`<br>`badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In the texts of program procedures and functions, operators should be separated by a semicolon (";"). The end of the line is not a sign of the end of the statement. Despite the fact that in some cases the platform allows you to skip the semicolon, you must always indicate this character, clearly indicating the completion of the statement.

**NOTE**: The keywords `Procedure`, `EndProcedure`, `Function`, `EndFunction` are not operators, but operator brackets, therefore, **DO NOT** end with a semicolon (this can lead to module execution errors).

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SemicolonPresence-off
// BSLLS:SemicolonPresence-on
```

### Parameter for config

```json
"SemicolonPresence": false
```
