# Magic numbers

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `Yes` | `1` | `badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Magic numbers are any number in your code that does not immediately become apparent without being immersed in context.

## Examples

Bad
```Bsl
Function GetsTheInterval(Duration)

     Return Duration < 10 * 60 * 60;

End Function
```

Good
```Bsl
Function GetsTheInterval (Duration in Seconds)
    
     MinutesHour     = 60;
     SecondsMinute   = 60;
     SecondsHour     = SecondsMinute * MinutesHour;
     HoursIninterval = 10;
     Return Duration < HoursWininterval * SecondsHour;

End Function
```
