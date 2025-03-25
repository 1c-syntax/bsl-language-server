# Magic numbers (MagicNumber)

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
