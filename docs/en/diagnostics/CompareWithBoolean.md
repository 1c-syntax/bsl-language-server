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

## Exceptions

The diagnostic is triggered only if the type of the second operand can be inferred and
it is **unambiguously** `Boolean`. If the type cannot be determined or is a union of
types, no issue is reported — in such cases comparison with a boolean constant may be
justified.

For example, `SafeMode()` returns a value of type `Boolean | String`, so comparison with
`False` is correct here and no issue is reported:

```bsl
If SafeMode() <> False Then
    // ...
EndIf;
```

## Sources

* [Standard: Comparison with True and False (RU)](https://its.1c.ru/db/v8std#content:441) (item 4)
