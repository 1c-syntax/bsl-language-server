# Excessive AutoTest Check (ExcessiveAutoTestCheck)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Standard 772, Interaction with Automated Testing Tools, has been canceled. In this regard, verification of the "АвтоТест" parameter in the form code is no longer necessary.

## Examples
```bsl
If Parameters.Property("АвтоТест") Then
    Return;
EndIf;
```

and in handler Filling in object module:

```bsl
// Skip processing to get the form when sending the "АвтоТест" program.
If FillData = "АвтоТест" Then
    Return;
EndIf;
```

## Sources
* Источник: [Standard: Modules. Part 3 (RU)](https://its.1c.ru/db/v8std#content:456:hdoc:3)
