# Execution query on cycle (CreateQueryInCycle)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |     Tags      |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:-------------:|
| `Error` |         `BSL`<br>`OS`         | `Critical` |              `Yes`              |                `20`                 | `performance` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Execution query in cycle.

## Examples

Bad

```bsl

// BanksToProcessing - contains an array of banks

InidividualQuery = New Query("
  |SELECT
  | BankAccounts.Ref AS Account
  |FROM
  | Catalog.BankAccounts AS BankAccounts
  |WHERE
  | BankAccounts.Bank = &Bank");

For Each Bank From BanksToProcess Do
  InidividualQuery .SetParameter("Bank", Bank);
  AccountsSelection = InidividualQuery .Execute().Select();
  While AccountsSelection.Next() Do
    ProcessBankAccounts(AccountsSelection.Account);
  EndDo;
EndDo;


```

Good

```bsl
// BanksToProcess - contains an array of banks

MergedQuery = New Query("
  |SELECT
  | BankAccounts.Ref AS Account
  |FROM
  | Catalog.BankAccounts AS BankAccounts
  |WHERE
  | BankAccounts.Bank In(&BanksToProcess)");

MergedQuery.SetParameter("BanksToProcess", BanksToProcess);
AccountsSelection = MergedQuery.Execute().Select();
While AccountsSelection.Next() Do
  ProcessBankAccounts(AccountsSelection.Account);
EndDo;

```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CreateQueryInCycle-off
// BSLLS:CreateQueryInCycle-on
```

### Parameter for config

```json
"CreateQueryInCycle": false
```
