# Using FindByName and FindByCode (UsingFindElementByString)

|     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                           Теги                           |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------------------------------:|
| `Code smell` | `BSL` | `Major`  |             `Yes`             |           `2`           | `standard`<br>`badpractice`<br>`performance` |

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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingFindElementByString-off
// BSLLS:UsingFindElementByString-on
```

### Parameter for config

```json
"UsingFindElementByString": false
```
