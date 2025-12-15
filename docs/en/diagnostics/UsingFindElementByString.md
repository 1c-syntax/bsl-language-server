# Using FindByName, FindByCode and FindByNumber (UsingFindElementByString)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The rule finds the use of the `FindByName`, `FindByCode` or `FindByNumber` methods using specific numbers, codes and names of elements or documents. Similar code may not work correctly in other databases. Often such code is test code included in the release version, which is also not recommended.

It is recommended to specify constant data values ​​from the database in "Сonstants" or predefined metadata elements.

## Examples

Incorrect:
```bsl
Position = Catalogs.Positions.FindByName("Senior Accountant");
```
or
```bsl
Position = Catalogs.Positions.FindByCode("00-0000001");
```

or

```bsl
Object = Documents.Invoice.FindByNumber("0000-000001", CurrentDate());
```

Acceptable use:
```bsl
Catalogs.Currencies.FindByCode(CurrentData.CurrencyCodeDigital);
```
```bsl
Catalogs.BankClassifier.FindByCode(BankDetails.BIK);
```

```bsl
Documents.Invoice.FindByNumber(Number);
```
