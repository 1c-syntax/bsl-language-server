# ThisObject assign (ThisObjectAssign)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
In managed form modules and common modules, there should not be a variable named "ThisObject".

Often this error appears when updating the platform version: the "ThisObject" property of managed forms and common modules appeared in version 8.3.3 which could previously be used as a variable name.

## Examples

Incorrect:
```bsl

ThisObject = FormAttributeToValue("Object");

```

## Sources
