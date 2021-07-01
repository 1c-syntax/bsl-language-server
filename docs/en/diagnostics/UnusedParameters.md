# Unused parameter (UnusedParameters)

|     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |            Теги            |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------:|
| `Code smell` | `OS`  | `Major`  |             `Yes`             |           `5`           | `design`<br>`unused` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Methods shouldn't contain unused parameters.

## Examples

```bsl
Function AddTwoNumbers(Val FirstValue, Val SecondValue, Val UnusedParameter)

    Return FirstValue + SecondValue;

EndFunction
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnusedParameters-off
// BSLLS:UnusedParameters-on
```

### Parameter for config

```json
"UnusedParameters": false
```
