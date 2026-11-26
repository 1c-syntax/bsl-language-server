# Event handler signature mismatch (EventHandlerInvalidSignature)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The signature of a platform-event handler method does not match the event contract: parameter count diverges. If the platform invokes the handler with N parameters but fewer are declared, the runtime either errors out or silently drops the parameter.

The contract is taken from the platform syntax helper (via bsl-context). All overloads of the event signature are considered; the diagnostic fires only if none of them fits.

## Examples

```bsl
// Fires: OnWrite requires Cancel parameter at least; empty signature does not fit.
Procedure OnWrite()
    // ...
EndProcedure

// Does not fire:
Procedure OnWrite(Cancel)
    // ...
EndProcedure
```

## Sources

* Source: [BSL coding standards. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)
