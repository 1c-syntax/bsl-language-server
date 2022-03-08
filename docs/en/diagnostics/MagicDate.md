# Magic dates (MagicDate)

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
