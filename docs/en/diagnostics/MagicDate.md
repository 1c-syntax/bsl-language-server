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

Also, a good solution is to use a special method with "telling name" that returns
constant

```bsl
Function DateInventionHover()
    Return '20151021';
EndFunction

If CurrentDate < DateInventionHover() Then
    HoverBoardWillBeInvented = Undefined;
EndIf;
```
