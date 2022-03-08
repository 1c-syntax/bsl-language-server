# Order of Parameters in method (OrderOfParams)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Optional parameters (parameters with default values) should follow mandatory parameters (the ones without default values).

## Examples

```bsl
Function CurrencyRateOnDate(Currency, Date = Notdefined) Export
```

## Sources

* [Standard: Parameters of procedures and functions (RU)](https://its.1c.ru/db/v8std#content:640:hdoc)
