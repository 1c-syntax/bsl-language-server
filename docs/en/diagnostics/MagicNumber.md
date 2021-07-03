# Magic numbers (MagicNumber)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |     Tags      |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------:|
| `Code smell` | `BSL`<br>`OS` | `Minor`  |             `Yes`             |           `1`           | `badpractice` |

## Parameters


|        Name         |   Type    |                     Description                      | Значение<br>по умолчанию |
|:-------------------:|:---------:|:----------------------------------------------------:|:------------------------------:|
| `authorizedNumbers` | `String`  | `allowed numbers, coma separated. Example:-1,0,1,60` |            `-1,0,1`            |
| `allowMagicIndexes` | `Boolean` |                `allow magic indexes`                 |             `true`             |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Magic numbers are any number in your code that does not immediately become apparent without being immersed in context.

## Examples

Bad

```bsl
Function GetsTheInterval(Duration)

     Return Duration < 10 * 60 * 60;

End Function
```

Good

```bsl
Function GetsTheInterval (Duration in Seconds)

    MinutesHour     = 60;
    SecondsMinute   = 60;
    SecondsHour     = SecondsMinute * MinutesHour;
    HoursIninterval = 10;
    Return Duration < HoursWininterval * SecondsHour;

End Function
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MagicNumber-off
// BSLLS:MagicNumber-on
```

### Parameter for config

```json
"MagicNumber": {
    "authorizedNumbers": "-1,0,1",
    "allowMagicIndexes": true
}
```
