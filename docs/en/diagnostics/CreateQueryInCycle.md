# Execution query on cycle (CreateQueryInCycle)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Execution query in cycle.

## Examples

Incorrect

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

Correct

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
