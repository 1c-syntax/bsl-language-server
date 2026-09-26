# Call of a method unavailable in the caller context (CompilationDirectiveIncompatibleCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A compilation directive defines where a method of a form or command module is compiled, and therefore which
methods of the same module it can call:

* a client method (`&AtClient`) is not available when compiling on the server and to methods without context;
* a server method with context (`&AtServer` or no directive) is not available to methods without context
  (`&AtServerNoContext`, `&AtClientAtServerNoContext`).

If a method calls a method that does not exist in its context, the module does not compile: "Procedure or
function with the specified name is not defined". For a form this means the whole form does not open.

The diagnostic checks calls of the same module's methods by name, including calls inside expressions. Calls
through an object (`ThisObject.Method()`) are not checked by the platform at compile time, and are not checked
here either. Methods with a directive not available for the module type are skipped - they are reported by
`CompilationDirectiveNotAllowed`.

`#If` preprocessor instructions are taken into account: a call is checked in the contexts (server, thin, web and
mobile client) where it is compiled according to the caller's directive and the surrounding `#If`, `#ElsIf`,
`#Else` conditions. For example, in a server method a call of a client method under `#If Client` is not compiled and
not reported, while under `#If Server` it is reported. A method without context does not see client methods and
server methods with context either on the client or on the server. A call under a condition with an operating system
symbol (`Linux`, `Windows`, `MacOS`) is not checked.

## Examples

#### Incorrect (form module):
```bsl
&AtServerNoContext
Function ProductData(Product)
    Return PrepareData(Product); // method with context called from a method without context
EndFunction

&AtServer
Function PrepareData(Product)
    ...
EndFunction

&AtServer
Procedure FillAtServer()
    UpdateTitle(); // client method called from a server method
EndProcedure

&AtClient
Procedure UpdateTitle()
    ...
EndProcedure
```

#### Correct:
```bsl
&AtServerNoContext
Function PrepareData(Product)
    ...
EndFunction

&AtClient
Procedure Fill(Command)
    FillAtServer();
    UpdateTitle();
EndProcedure
```

## Sources

* Syntax helper: "Built-in language" - "Compilation directives"
