# Using FindByName, FindByCode and FindByNumber (UsingFindElementByString)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is forbidden to use the search methods for elements "FindByName" or "FindByCode".

## Examples

```bsl
Position = Catalogs.Positions.FindByName("Senior Accountant");
```

or

```bsl
Position = Catalogs.Positions.FindByCode("00-0000001");
```

Acceptable use:
```bsl
Catalogs.Currencies.FindByCode(CurrentData.CurrencyCodeDigital);
```
```bsl
Catalogs.BankClassifier.FindByCode(BankDetails.BIK);
```
