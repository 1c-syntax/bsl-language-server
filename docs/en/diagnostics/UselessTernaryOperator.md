# Useless ternary operator (UselessTernaryOperator)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
The placement of Boolean constants "True" or "False" in the ternary operator indicates poor code thoughtfulness.

## Examples
Useless operators

```Bsl
A = ?(B = 1, True, False);
```
```Bsl
A = ?(B = 0, False, True);
```

Suspicious operators

```Bsl
A = ?(B = 1, True, True);
```
```Bsl
A = ?(B = 0, 0, False);
```
