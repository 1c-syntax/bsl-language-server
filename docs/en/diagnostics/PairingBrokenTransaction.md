# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" (PairingBrokenTransaction)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Yes` | `15` | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Beginning of transaction and it's committing (rollback) have to be executed withing context of the same method.

## Examples

*Correct*

```bsl
Procedure WriteDataToIDb()

    BeginTransaction();

    Try
        ... // reading or writing data
        DocumentObject.Write()
        CommitTransaction();
    Except
        RollbackTransaction();
        ... // additional actions to process the exception
    EndTry;

EndProcedure
```

*Incorrect*

```bsl
Procedure WriteDataToIDb()
 
    Begintransaction();
    WriteDocument();

EndProcedure;

Procedure WriteDocument()

    Try
        ... // reading or writing data
        DocumentObject.Write()
        CommitTransacrion();
    Except
        RollbackTransaction();
    ... // additional actions to process the exception
    EndTry;

EndProcedure
```

## Sources

* [Transactions: Terms of Use](https://its.1c.ru/db/v8std#content:783:hdoc)

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
