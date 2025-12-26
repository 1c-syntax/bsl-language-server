# Erroneous indication of several compilation directives (SeveralCompilerDirectives)

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
