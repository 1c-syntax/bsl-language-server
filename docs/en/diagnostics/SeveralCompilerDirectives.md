# Erroneous indication of several compilation directives

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `Нет` | `5` | `unpredictable`<br/>`error` |


## TODO PARAMS

## Description

# Erroneous indication of several compilation directives

Indication of several compilation directives for a method or a variable is an error. Besides, indication of several complilation directives adduce to uncertaint. Will the code be compiled? If yes, in what context?

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
