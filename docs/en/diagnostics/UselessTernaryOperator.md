# Useless ternary operator (UselessTernaryOperator)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                Tags                 |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:-----------------------------------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           |    `badpractice`<br>`suspicious`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
The placement of Boolean constants "True" or "False" in the ternary operator indicates poor code thoughtfulness.

## Examples
Useless operators

```Bsl
A = ?(True, 1, 0);
```
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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UselessTernaryOperator-off
// BSLLS:UselessTernaryOperator-on
```

### Parameter for config

```json
"UselessTernaryOperator": false
```
