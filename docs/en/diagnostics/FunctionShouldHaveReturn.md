# The function should have return (FunctionShouldHaveReturn)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |                 Tags                  |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------------------------------:|
| `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `suspicious`<br>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

`Function` differs from a `Procedure` only that it necessarily returns a value and can be used in expressions.

Based on the above-mentioned, a `function` which does not contain a return is itself erroneous. Corrections required:

- implement return if the implemented method is a function
- rewrite function to procedure if return is not needed

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FunctionShouldHaveReturn-off
// BSLLS:FunctionShouldHaveReturn-on
```

### Parameter for config

```json
Параметр конфигурационного файла
```
