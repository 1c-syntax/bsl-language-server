# Erroneous indication of several compilation directives (SeveralCompilerDirectives)

 |  Type   |        Scope        |  Severity  | Activated<br>by default | Minutes<br>to fix |               Tags               |
 |:-------:|:-------------------:|:----------:|:-----------------------------:|:-----------------------:|:--------------------------------:|
 | `Error` | `BSL`<br>`OS` | `Critical` |             `Yes`             |           `5`           | `unpredictable`<br>`error` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Indication of several compilation directives for a method or a variable is an error. Besides, indication of several complilation directives adduce to uncertaint. Will the code be compiled? If yes, in what context?

## Examples

Incorrect

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
