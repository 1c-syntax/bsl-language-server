# Insert a collection into itself (SelfInsertion)

|   Type    |    Scope    | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                            Tags                            |
|:--------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:----------------------------------------------------------:|
| `Error` |         `BSL`<br>`OS`         | `Major` |              `Yes`              |                `10`                 |       `standard`<br>`unpredictable`<br>`performance`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Inserting a collection into itself results in circular references.

## Sources

* [Search for circular links (RU)](https://its.1c.ru/db/metod8dev#content:5859:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SelfInsertion-off
// BSLLS:SelfInsertion-on
```

### Parameter for config

```json
"SelfInsertion": false
```
