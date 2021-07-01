# Variable is assigned to itself (SelfAssign)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |     Теги     |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:------------:|
| `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `suspicious` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is meaningless to assign a variable to itself and usually points to an error.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SelfAssign-off
// BSLLS:SelfAssign-on
```

### Parameter for config

```json
"SelfAssign": false
```
