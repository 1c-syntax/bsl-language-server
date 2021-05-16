# Incorrect expression line break (IncorrectLineBreak)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Info` | `Yes` | `2` | `standard`<br>`badpractice` 

## Parameters 

 Name | Type | Description | Default value 
 :-: | :-: | :-- | :-: 
 `checkFirstSymbol` | `Boolean` | ```Check beginning of line for invalid characters``` | ```true``` 
 `listOfIncorrectFirstSymbol` | `String` | ```Vertical bar-separated characters that should not start the line(special characters must be escaped)``` | ```\)|;|,|\);``` 
 `checkLastSymbol` | `Boolean` | ```Check end of line for invalid characters``` | ```true``` 
 `listOfIncorrectLastSymbol` | `String` | ```Vertical bar-separated characters that must not end in strings(special characters must be escaped)``` | ```ИЛИ|И|OR|AND|\+|-|/|%|\*``` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Long arithmetic expressions are carried as follows:
one entry can contain more than one operand;
when wrapping, operation characters are written at the beginning of the line (and not at the end of the previous line);
operands on a new line are preceded by standard indentation, or they are aligned to the beginning of the first operand, regardless of the operation signs.

If necessary, parameters of procedures, functions and methods should be transferred as follows:

* parameters are either aligned to the beginning of the first parameter, or preceded by standard indentation;
* closing parenthesis and operator separator ";" are written on the same line as the last parameter;
* the formatting method that offers the auto-formatting function in the configurator is also acceptable

Complex logical conditions in If ... ElseIf ... EndIf should be carried as follows:

* The basis for the newline if the line length is limited to 120 characters;
* logical operators AND, OR are placed at the beginning of a line, and not at the end of the previous line;
* all conditions are preceded by the standard first indent, or they are aligned at the start of work without taking into account the logical operator (it is recommended to use spaces to align expressions relative to the first line).

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
AmountDocument = AmountWithoutDiscount +
                 AmountManualDiscounts +
                 AmountAutomaticDiscount;
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Источник: [Стандарт: Перенос выражений (RU)](https://its.1c.ru/db/v8std#content:444:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IncorrectLineBreak-off
// BSLLS:IncorrectLineBreak-on
```

### Parameter for config

```json
"IncorrectLineBreak": {
    "checkFirstSymbol": true,
    "listOfIncorrectFirstSymbol": "\\)|;|,|\\);",
    "checkLastSymbol": true,
    "listOfIncorrectLastSymbol": "ИЛИ|И|OR|AND|\\+|-|/|%|\\*"
}
```
