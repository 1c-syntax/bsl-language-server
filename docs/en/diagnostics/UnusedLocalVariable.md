# Unused local variable (UnusedLocalVariable)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                  Tags                  |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |           `1`           |    `brainoverload`<br>`badpractice`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Unused local variables should be removed

If a local variable is declared but not used, it is dead code and should be removed.
Doing so will improve maintainability because developers will not wonder what the variable is used for.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnusedLocalVariable-off
// BSLLS:UnusedLocalVariable-on
```

### Parameter for config

```json
"UnusedLocalVariable": false
```
