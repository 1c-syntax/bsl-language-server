# Violating transaction rules for the 'CommitTransaction' method (CommitTransactionOutsideTryCatch)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The CommitTransaction method should be the last one in the Try block, just before the Exception operator, to ensure that there is no exception after the CommitTransaction.

## Sources

* [Transactions: terms of use (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)
