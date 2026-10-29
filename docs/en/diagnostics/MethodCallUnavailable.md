# Member access unavailable in the calling context (MethodCallUnavailable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Checks whether methods and properties are available in the compilation contexts of the access.
The calculation combines the module kind, platform type availability from Syntax Assistant,
common module flags, method directives and preprocessor conditions.

Checks local unqualified calls, exported methods of global common modules, application modules and
external connection modules, and source methods resolved by type inference through module names,
variables or function results. Platform methods and properties, including global-context properties, use
Syntax Assistant availability. Application specializations inherit availability from their base type,
such as `DocumentManager`. Unknown availability does not prove a restriction.
Each known target in a union is checked independently: an available
target does not hide an unavailable one. The Arbitrary type is skipped without suppressing other targets.
Before reporting, type inference is refined for the checked compilation context. Assignments and function
returns in inactive preprocessor branches do not contribute targets in that context.
If a called function's body has been released, its known indexed return types are preserved;
refining its return value for a compilation context requires the body to be available.
Alternative declarations
with the same name are considered together. Each access produces one diagnostic per compilation
context where the member is unavailable. In a chain such as `Catalogs.Name.Select()`, the first unavailable
access is reported: if `Catalogs` is prohibited, its dependent accesses do not produce duplicate messages
in that mode. Accesses inside arguments are checked independently. The context is included in parentheses in the message;
file mode distinguishes the client and server parts of forms and commands.
When a local declaration is excluded in the checked context, an available global method is considered,
including a platform function with the same name.

Declaration and call contexts are calculated separately. Form methods retain the restrictions
on calls from context-free methods. In client/server mode, the common module Server call flag
permits remote calls from clients and external connections; it does not make the module execute on the client.
In file mode, common module visibility combines the server flag with the corresponding client
or external connection flag.

Common modules use their Server, Client (managed application), Client (ordinary application)
and External connection flags. Syntax Assistant availability is combined with module-kind rules.
Forms and commands have client and server parts. HTTP/Web service, integration service, bot and WebSocket
modules use all contexts listed in Syntax Assistant. Object, manager, record set, value manager and
recalculation modules also use their platform type availability, including external data source tables and cubes.
If this information is absent, the context remains unknown and the module is skipped.
Session modules are checked in server and file contexts; their event availability in Syntax Assistant
does not define the compilation contexts of the module. `&AtClient` and `&AtServer` directives are
also considered in global common modules.
Ordinary forms are checked in managed mode only when Use ordinary forms in managed application is enabled.

Each context accepts `on`, `off` or `auto` (the default):

* `on` enables the context regardless of automatic project selection.
* `off` disables checking that context in every module.
* `auto` uses the default run mode and configuration use purposes.

In `auto`, managed applications exclude the thick ordinary client. Ordinary applications select
the thick ordinary client among platform client modes. Mobile platform use enables mobile application
client/server; platform application use enables the other modes. Both purposes enable both groups.
Mobile client and mobile standalone server belong to platform applications.
As a temporary exception, `checkMobileStandaloneServer = auto` disables mobile standalone server
checks regardless of metadata. Automatic selection requires reading standalone configuration content
in [MDClasses](https://github.com/1c-syntax/mdclasses/issues/728) and using it in BSLLS.
Until that support is integrated, enable this context explicitly with `on` if needed.
An unknown module kind is skipped even when context parameters are `on`.

For example, a project without COM access can use:

```json
{
  "diagnostics": {
    "parameters": {
      "MethodCallUnavailable": {
        "checkExternalConnection": "off"
      }
    }
  }
}
```

The parameters are `checkServer`, `checkThinClient`, `checkWebClient`, `checkMobileClient`,
`checkThickClientManagedApplication`, `checkThickClientOrdinaryApplication`,
`checkExternalConnection`, `checkMobileApplicationClient`, `checkMobileApplicationServer`
and `checkMobileStandaloneServer`. Disabling a check does not change preprocessor semantics.
A thick-client option controls its client/server variant and, only when the shared
[`supportThickClientFileMode`](../features/ConfigurationFile.md) setting is enabled, its file variant.
That project setting defaults to false; `on` in the diagnostic parameters does not override it.
The external-connection option controls both variants independently of that setting.

Preprocessor evaluation supports server, thin, web, mobile and both thick clients, external
connections, mobile application client/server and mobile standalone server. File and client/server
variants are distinct: a file-mode thick client sees both Client and Server, including inside a client
procedure of a managed form. Method directives still distinguish the client and server parts of forms and commands.
Disabling `checkServer` therefore does not disable file-based thick client checks where Server can also be true.
Nested branches, NOT, AND, OR and parentheses
are supported. Unknown conditions, OS symbols and missing module metadata remain unknown and
are not treated as errors.

Methods without known source declarations and platform version compatibility are outside this rule.
Version compatibility is checked by UnavailableMemberCall.
Runtime If conditions, including helper calls such as IsMobileClient(), do not narrow compilation
contexts. This diagnostic does not analyze their runtime semantics.

## Examples

A context-free server method in a managed form cannot call a context-bound server method:

```bsl
&AtServerNoContext
Procedure Execute()
    Fill(); // Unavailable call
EndProcedure

&AtServer
Procedure Fill()
EndProcedure
```

Make Fill context-free if it does not need form data, or call it from a context-bound method.

A method declared only under #If ThinClient cannot be called unconditionally from a client
method: it has no declaration in the web and mobile client contexts. Guard the call with the
same condition or provide implementations for the other contexts.

## Sources

* [1C:Enterprise 8.3.27 Developer Guide](https://1c-dn.com/library/tutorials/1c_enterprise_developer_guide_8_3_27/)
* [Creating common modules](https://kb.1ci.com/1C_Enterprise_Platform/Guides/Developer_Guides/1C_Enterprise_Development_Standards/Creating_and_modifying_metadata_objects/Configuration_operation_arrangement/Creating_common_modules/)
