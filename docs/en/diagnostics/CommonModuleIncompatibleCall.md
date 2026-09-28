# Call of a common module unavailable in the call context (CommonModuleIncompatibleCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Where a common module is available depends on its flags:

* on the server, a module with the "Server" flag is visible;
* on the client, a module with the "Client (managed application)" or "Server call" flag is visible.

A call of a module that does not exist in the call context does not compile: "Variable not defined", and for a
global module "Procedure or function with the specified name is not defined".

A common module method declared inside a `#If` preprocessor instruction exists only where the condition is true. A
call of such a method from the same module in another context is an error: without the module name it does not
compile, through the module name (`Module.Method()`) it fails at runtime with "Object method not found". The
diagnostic checks both by the module text. A call from another module (for example, of a client-server module method under `#If Server` from the
client) fails at runtime with "Object method not found"; such calls are not checked yet: the text of the called
module may not be loaded.

The call context is taken from the caller's directive (form and command modules), the common module flags or the
module type (object and manager modules - server) and the surrounding `#If` instructions. The server, thin, web and
mobile client contexts are checked. A call under a condition with an operating system symbol is not checked.

## Examples

#### Incorrect:
```bsl
// Form module
&AtClient
Procedure Fill(Command)
    FileOperations.DeleteTemporaryFiles(); // server module without the "Server call" flag
EndProcedure
```

#### Correct:
```bsl
// Call the server through a module with the "Server call" flag or a server method of the form
&AtClient
Procedure Fill(Command)
    FileOperationsServerCall.DeleteTemporaryFiles();
EndProcedure
```

## Sources

* Syntax helper: "Built-in language" - "Preprocessor instructions"
* Standard: [Using compilation directives and preprocessor instructions (RU)](https://its.1c.ru/db/v8std#content:439:hdoc)
