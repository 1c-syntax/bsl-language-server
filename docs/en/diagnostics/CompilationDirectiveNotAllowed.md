# Compilation directive is not available in this module type (CompilationDirectiveNotAllowed)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The set of compilation directives depends on the module type:

* a managed form module accepts `&AtClient`, `&AtServer`, `&AtServerNoContext`, `&AtClientAtServerNoContext`;
* a command module accepts `&AtClient`, `&AtServer`, `&AtClientAtServer`.

A directive from another module type's set is parsed without a syntax error but is not defined for this module.
The most common case is `&AtClientAtServer` in a form module instead of `&AtClientAtServerNoContext`: the method
becomes unavailable to some callers, and the behavior depends on the client type. For example, a call to such
a method from a server procedure of the form does not compile, and the form does not open.

## Examples

#### Incorrect (form module):
```bsl
&AtServer
Procedure OnCreateAtServer(Cancel, StandardProcessing)
    UpdateAppearance(ThisObject);
EndProcedure

&AtClientAtServer
Procedure UpdateAppearance(Form)
    ...
EndProcedure
```

#### Correct:
```bsl
&AtClientAtServerNoContext
Procedure UpdateAppearance(Form)
    ...
EndProcedure
```

## Sources

* Syntax helper: "Built-in language" - "Compilation directives"
* Platform bug: [&AtClientAtServer in a managed form module (RU)](https://www.hostedredmine.com/issues/930235)
