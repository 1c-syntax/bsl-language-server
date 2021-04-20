# Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" (PairingBrokenTransaction)

 |  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Tags    |
 |:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
 | `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `15`           | `standard` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Beginning of transaction and it's committing (rollback) have to be executed withing context of the same method.

## Examples

*Correct*

```bsl
Процедура ЗаписатьДанныеВИБ()

    НачатьТранзакцию();

    Попытка
        ... // чтение или запись данных
        ДокументОбъект.Записать()
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
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
        ... // additional operations to process the exception
    EndTry;

EndProcedure // чтение или запись данных
        ДокументОбъект.Записать()
        ЗафиксироватьТранзакцию();
    Исключение
        ОтменитьТранзакцию();
    ... // additional steps to handle the exception
    EndTry;

EndProcedure

```

## Reference

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
