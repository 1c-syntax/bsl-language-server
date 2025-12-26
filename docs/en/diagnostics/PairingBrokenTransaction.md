# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" (PairingBrokenTransaction)

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

## Reference

* [Transactions: Terms of Use](https://its.1c.ru/db/v8std#content:783:hdoc)
