# Compilation directive is not available in this module (CompilationDirectiveNotAllowed)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The set of compilation directives depends on the module type:

* a managed form module accepts `&AtClient`, `&AtServer`, `&AtServerNoContext`, `&AtClientAtServerNoContext`;
* a command module accepts `&AtClient`, `&AtServer`, `&AtClientAtServer`;
* in a common module, `&AtClient` and `&AtServer` further restrict the contexts enabled by the module flags.

A directive from another module type's set is parsed without a syntax error but is not defined for this module.
The most common case is `&AtClientAtServer` in a form module instead of `&AtClientAtServerNoContext`: the method
becomes unavailable to some callers, and the behavior depends on the client type. For example, a call to such
a method from a server procedure of the form does not compile, and the form does not open.

For a common module, the diagnostic checks whether at least one possible compilation context remains after
applying the directive. For example, `&AtClient` excludes a method when only the External connection flag is enabled.
`&AtServerNoContext`, `&AtClientAtServerNoContext`, and `&AtClientAtServer` do not declare an available common-module
method, even when both client and server flags are enabled.

Enabling the shared [`supportThickClientFileMode`](../features/ConfigurationFile.md) setting also includes
the file-mode thick client. In that case, a server common module without a client flag does not necessarily make
`&AtClient` an invalid declaration: its method can still exist in a file-mode thick client.
By default, that deployment mode is excluded, so such a method is considered unavailable.
`MethodCallUnavailable` separately checks calls in contexts where the method does not exist.
The declaration check does not depend on that diagnostic's selected contexts or the surrounding preprocessor branch.
Missing common-module metadata does not imply incompatible flags.

When a directive leaves no available contexts, `CompilationDirectiveNeedLess` does not add a duplicate recommendation.
The recommendation remains for valid directives in common modules.

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
* [Compilation directives and preprocessor commands](https://kb.1ci.com/1C_Enterprise_Platform/Guides/Developer_Guides/1C_Enterprise_Development_Standards/Code_conventions/Using_1C_Enterprise_language_structures/Compilation_directives_and_preprocessor_commands/)
* Platform bug: [&AtClientAtServer in a managed form module (RU)](https://www.hostedredmine.com/issues/930235)
