# Using Russian character "yo" ("ё") in code (YoLetterUsage)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Tags    |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
 | `Code smell` | `BSL`<br>`OS` |  `Info`  |             `Yes`             |           `5`           | `standard` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In code it is prohibited to use character "yo" ("ё"). Exception is interface texts, displayed to user in messages, forms and help, where it is applicable.

## Sources

* [Standard: Modules texts (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:YoLetterUsage-off
// BSLLS:YoLetterUsage-on
```

### Parameter for config

```json
"YoLetterUsage": false
```
