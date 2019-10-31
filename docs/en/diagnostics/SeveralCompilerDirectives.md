# Erroneous indication of several compilation directives

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `No` | `5` | `unpredictable`<br/>`error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Indication of several compilation directives for a method or a variable is an error. Besides, indication of several complilation directives adduce to uncertaint. Will the code be compiled? If yes, in what context?

## Examples

Incorrect

```Bsl
&AtServer
&AtClient
Var MyVariable;

&AtServer
&AtClient
Procedure MyProcedure()

EndProcedure
```
