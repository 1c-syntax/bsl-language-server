# Magic dates (MagicDate)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                  Tags                  |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------------:|
| `Code smell` |    `BSL`<br>`OS`    | `Minor`  |             `Yes`             |           `5`           |    `badpractice`<br>`brainoverload`    |

## Parameters


|       Name        |   Type   |                                   Description                                   |             Default value              |
|:-----------------:|:--------:|:-------------------------------------------------------------------------------:|:--------------------------------------:|
| `authorizedDates` | `String` | `Allowed dates, comma separated. Example: 00010101,00010101000000,000101010000` | `00010101,00010101000000,000101010000` |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Magic date is any date in your code that does not immediately become apparent without being immersed in context.

## Examples

Bad

```bsl
If now < '20151021' Then
    HoverBoardIsReal = Undefined;
EndIf;
```

Good

```bsl
PredictedDate = '20151021'; 
If now < PredictedDate Then
    HoverBoardIsReal = Undefined;
EndIf;
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MagicDate-off
// BSLLS:MagicDate-on
```

### Parameter for config

```json
"MagicDate": {
    "authorizedDates": "00010101,00010101000000,000101010000"
}
```
