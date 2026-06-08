# Comparison with a boolean constant (CompareWithBoolean)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Comparing an expression with the boolean constant `True` or `False` using the `=` and `<>` operators is redundant
and reduces code readability. If the expression already has the `Boolean` type, it is enough to use it directly
(or with the `NOT` operator).

Moreover, such a construction can hide a design error: if for some reason the type of the expression differs from
`Boolean`, the code branch may be executed incorrectly.

## Examples

Bad:

```bsl
If Value = True Then
    // ...
EndIf;

If Value <> False Then
    // ...
EndIf;
```

Good:

```bsl
If Value Then
    // ...
EndIf;

If NOT Value Then
    // ...
EndIf;
```

## Sources

* [Standard: Comparison with True and False (RU)](https://its.1c.ru/db/v8std#content:441) (item 4)
