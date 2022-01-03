# Erroneous indication of several compilation directives (SeveralCompilerDirectives)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |               Tags               |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:--------------------------------:|
| `Error` |         `BSL`<br>`OS`         | `Critical` |              `Yes`              |                 `5`                 |    `unpredictable`<br>`error`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is an error to specify multiple compilation directives to a module method or variable. In addition, specifying several different directives leads to ambiguities: will the code compile? And if so, in what context?

## Examples

Wrong

```bsl
&AtServer
&AtClient
Var MyVariable;

&AtServer
&AtClient
Procedure MyProcedure()

EndProcedure
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SeveralCompilerDirectives-off
// BSLLS:SeveralCompilerDirectives-on
```

### Parameter for config

```json
"SeveralCompilerDirectives": false
```
