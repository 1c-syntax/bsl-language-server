# Using FindByName, FindByCode and FindByNumber (UsingFindElementByString)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The rule finds the use of the `FindByName`, `FindByCode` or `FindByNumber` methods using specific numbers, codes and names of elements or documents. 
Similar code may not work correctly in other databases.
Often such code is test code included in the release version, which is also not recommended.

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
Documents.Invoice.FindByNumber(Number, CurrentDate());
```

Resolved platform manager calls have separate messages for omitted search options:

* `CatalogManager.FindByDescription`: explicitly pass the second argument, `ExactMatch`. The default searches by a description prefix; `True` requests a full match. Explicit `False` is treated as an intentional choice.
* `DocumentManager.FindByNumber`: pass the second argument, `IntervalDate`, for periodic numbering. This date selects the number search interval.

An empty argument slot is also an omission. Each call produces one message, either for the omitted option or the existing literal search warning. The new checks require a resolved manager type and signature; unknown and user methods do not receive the new messages.

These methods can return deletion marked items and do not report duplicate matches. Use a query with explicit filtering and check the result count when deletion marks or uniqueness matter. Exact matching and an interval date do not provide these guarantees.
