# Unused local method (UnusedLocalMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Modules should not have unused procedures and functions. Diagnostics can skip `attachable methods` that have prefixes specified in the diagnostic parameter.

Event handlers are not reported as unused: the platform calls them itself, so there is no call in the module body. In a managed form module, handlers are the procedures declared by the form — its own events, its items' events and command actions.

The diagnostic does not run:

* in an **ordinary** form module — its events are not modelled in the context, so a handler would be indistinguishable from a forgotten method;
* in a module whose type could not be determined — that is how a single file opened outside a project looks: the module owner, and therefore its events, are invisible, so every handler would look like a method nobody calls.

## Sources

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
