# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" (PairingBrokenTransaction)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Beginning of transaction and it's committing (rollback) have to be executed withing context of the same method.

The diagnostic does not follow `If` / `Return` control flow. Inside a method it matches calls on two independent stacks:

* `BeginTransaction` / `CommitTransaction`
* `BeginTransaction` / `RollbackTransaction`

Two rollbacks and one begin produce a hit on the second rollback. That is expected.

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

*Incorrect — early rollback inside `Try`*

```bsl
BeginTransaction();
Try
    If ShouldExit Then
        RollbackTransaction();
        Return;
    EndIf;
    CommitTransaction();
Except
    RollbackTransaction();
EndTry;
```

The early `RollbackTransaction()` inside `Try` consumes the pair for `BeginTransaction()`. The call in `Except` is left unpaired, so the message is `Missing paired call of "BeginTransaction" for method "RollbackTransaction"`.

Do not call `RollbackTransaction()` and then `Return` inside `Try`. If you need to stop before commit, raise an exception and roll back only in `Except`.

## Sources

* [Transactions: Rules of Use (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)
