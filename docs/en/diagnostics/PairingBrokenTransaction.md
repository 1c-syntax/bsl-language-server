# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()"

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Нет` | `15` | `standard` |


## <TODO PARAMS>

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
