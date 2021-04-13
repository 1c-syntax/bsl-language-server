# Violating transaction rules for the 'BeginTransaction' method (BeginTransactionBeforeTryCatch)

 |  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Tags    |
 |:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
 | `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `standard` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The BeginTransaction method must be outside the TryCatch block immediately before the Try operator;

## Sources

+ [Transactions: Rules of Use (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:BeginTransactionBeforeTryCatch-off
// BSLLS:BeginTransactionBeforeTryCatch-on
```

### Parameter for config

```json
"BeginTransactionBeforeTryCatch": false
```
