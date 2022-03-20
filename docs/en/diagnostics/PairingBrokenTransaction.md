# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" (PairingBrokenTransaction)

|  Type   |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Error` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |          `15`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Beginning of transaction and it's committing (rollback) have to be executed withing context of the same method.

## Examples

*Correct*

```bsl
Procedure WriteDataToIB()

    StartTransaction();

    Try
        ... // read or write data
        DocumentObject.Write()
        CommitTransaction();
    Raise
        RollbackTransaction();
        ... // additional steps to handle the exception
    EndTry;

EndProcedure
```

*Incorrect*

```bsl
Procedure WriteDataToIB()

    StartTransaction();
    WriteDocument();

EndProcedure;

Procedure WriteDocument()

    Try
        ... // read or write data
        DocumentObject.Write()
        CommitTransaction();
    Raise
        RollbackTransaction();
        ... // additional steps to handle the exception
    EndTry;

EndProcedure

```

## Sources

* [Transactions: Rules of Use (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:PairingBrokenTransaction-off
// BSLLS:PairingBrokenTransaction-on
```

### Parameter for config

```json
"PairingBrokenTransaction": false
```
