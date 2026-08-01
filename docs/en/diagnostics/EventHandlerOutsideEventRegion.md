# Event handler outside standard region (EventHandlerOutsideEventRegion)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A method whose name matches a platform event of the module's owner type (for example, `OnWrite` in a document object module) is treated as an event handler. By the BSL coding standard such methods must reside in the standard region:

* for object and similar modules — `#Region EventHandlers` (`#Область ОбработчикиСобытий`).

In a form module the region depends on **what declares the handler** in `Form.xml`:

| what is handled | region |
|---|---|
| an event of the form itself | `#Region FormEventHandlers` |
| an event of an item outside tables | `#Region FormHeaderItemsEventHandlers` |
| an event of a table item (or of the table itself) | `#Region FormTableItemsEventHandlers<TableName>` |
| a form command | `#Region FormCommandsEventHandlers` |

RU region names are accepted as well. When the configuration is unavailable and the declaring entity cannot be determined, any of the form regions above will do.

The diagnostic fires when such a method has no parent region, or its region is not the one the standard prescribes for it.

## Examples

```bsl
// Fires: OnWrite is a platform event, must be inside EventHandlers
#Region Private

Procedure OnWrite(Cancel)
    // ...
EndProcedure

#EndRegion

// Does not fire:
#Region EventHandlers

Procedure OnWrite(Cancel)
    // ...
EndProcedure

#EndRegion
```

In a form module:

```bsl
// Fires: a command handler placed in the form events region
#Region FormEventHandlers

Procedure FillCommand(Command)
    // ...
EndProcedure

#EndRegion

// Does not fire:
#Region FormCommandsEventHandlers

Procedure FillCommand(Command)
    // ...
EndProcedure

#EndRegion
```

## Sources

* Source: [BSL coding standards. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)
