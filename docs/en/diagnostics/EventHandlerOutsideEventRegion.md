# Event handler outside standard region (EventHandlerOutsideEventRegion)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A method whose name matches a platform event of the module's owner type (for example, `OnWrite` in a document object module) is treated as an event handler. By the BSL coding standard such methods must reside in the standard region:

* for object and similar modules — `#Region EventHandlers` (`#Область ОбработчикиСобытий`);
* for form modules — `#Region FormEventHandlers`, `#Region FormHeaderItemsEventHandlers`, `#Region FormTableItemsEventHandlers<TableName>` (plus their RU equivalents).

The diagnostic fires when such a method has no parent region or its region name differs.

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

## Sources

* Source: [BSL coding standards. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)
