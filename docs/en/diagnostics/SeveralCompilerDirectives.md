# Erroneous indication of several compilation directives

Indication of several compilation directives for a method or a variable is an error. Besides, indication of several complilation directives adduce to uncertaint. Will be the code is compiled? If so, then what context?

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
