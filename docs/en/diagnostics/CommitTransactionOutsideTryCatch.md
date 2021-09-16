# Violating transaction rules for the 'CommitTransaction' method (CommitTransactionOutsideTryCatch)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Tags    |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The CommitTransaction method should be the last one in the Try block, just before the Exception operator, to ensure that there is no exception after the CommitTransaction.

## Sources

* [Transactions: terms of use (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommitTransactionOutsideTryCatch-off
// BSLLS:CommitTransactionOutsideTryCatch-on
```

### Parameter for config

```json
"CommitTransactionOutsideTryCatch": false
```
