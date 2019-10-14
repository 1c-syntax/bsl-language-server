# Violating transaction rules for the 'CommitTransaction' method

The CommitTransaction method should be the last one in the Try block, just before the Exception operator, to ensure that there is no exception after the CommitTransaction.

Source: [Транзакции: правила использования (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)
