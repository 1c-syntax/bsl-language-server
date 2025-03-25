# Using of the deprecated method "Find" (DeprecatedFind)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Method "Find" is deprecated. Use "StrFind" instead.

## Examples

Incorrect:

```bsl

If Find(Employee.Name, "Boris") > 0 Then

EndIf; 

```


Correct:

```bsl

If StrFind(Employee.Name, "Boris") > 0 Then

EndIf; 

```
