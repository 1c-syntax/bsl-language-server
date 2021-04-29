# Order of Parameters in method (OrderOfParams)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Major` | `Yes` | `30` | `standard`<br>`design` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Optional parameters (parameters with default values) should follow mandatory parameters (the ones without default values).

## Examples

```bsl
Function CurrencyRateOnDate(Currency, Date = Notdefined) Export
```

## Sources

* [Standard: Parameters of procedures and functions (RU)](https://its.1c.ru/db/v8std#content:640:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:OrderOfParams-off
// BSLLS:OrderOfParams-on
```

### Parameter for config

```json
"OrderOfParams": false
```
