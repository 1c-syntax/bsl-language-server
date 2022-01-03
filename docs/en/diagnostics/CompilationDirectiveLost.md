# Methods compilation directive (CompilationDirectiveLost)

|      Type      |    Scope    | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                Tags                 |
|:-------------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:-----------------------------------:|
| `Code smell` |             `BSL`             | `Major` |              `Yes`              |                 `1`                 |    `standard`<br>`unpredictable`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
All methods of managed forms and commands must have compilation directives.

## Examples

#### Incorrect:
```bsl
Procedure OnCreateAtServer()
...
EndProcedure 
```

#### Correct:
```bsl
&AtServer
Procedure OnCreateAtServer()
...
EndProcedure 
```

## Sources

* Helpful information: [Development of an interface for applied solutions on the 1C: Enterprise 8 platform (RU)](https://its.1c.ru/db/pubv8devui#content:189:1)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CompilationDirectiveLost-off
// BSLLS:CompilationDirectiveLost-on
```

### Parameter for config

```json
"CompilationDirectiveLost": false
```
