# Yoda conditions (YodaConditions)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A comparison where the literal is on the left of the operator (`If 0 = Sum`) and the variable or
expression is on the right is harder to read than the usual `If Sum = 0`: the left-hand side is
normally expected to be the value being checked, not a constant.

Unlike languages where assignment can appear in a condition, BSL has no `=`/`==` confusion, so the
only reason to keep such an order is readability. The diagnostic suggests putting the literal on the
right.

## Examples

Bad:

```bsl
If 0 = Sum Then
    // ...
EndIf;

If True = Flag Then
    // ...
EndIf;
```

Good:

```bsl
If Sum = 0 Then
    // ...
EndIf;

If Flag = True Then
    // ...
EndIf;
```

## Exceptions

* Comparison of two literals (`If 0 = 1 Then`) is not reported: there is nothing to reorder.
* Boolean literals (`True`, `False`) are checked like any others. Such comparisons may also be
  reported by the `CompareWithBoolean` diagnostic.

## Sources

* [Yoda conditions — Wikipedia](https://en.wikipedia.org/wiki/Yoda_conditions)
