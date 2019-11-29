# Procedure should not return Value (ProcedureReturnsValue)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Blocker` | `Yes` | `5` | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostics finds procedures with returning Values.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ProcedureReturnsValue-off
// BSLLS:ProcedureReturnsValue-on
```

### Parameter for config

```json
"ProcedureReturnsValue": false
```
