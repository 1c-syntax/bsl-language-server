# Using deprecated property "ThisForm" (UsingThisForm)

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |               Tags               |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------:|
 | `Code smell` | `BSL` | `Minor`  |             `Yes`             |           `1`           | `standard`<br>`deprecated` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In the version of the platform 1C Enterprise 8.3.3, the property of the form `ThisForm` was deprecated. Instead, use the property `ThisObject`

## Sources

* [Transfer of configurations to the 1C: Enterprise 8.3 platform without compatibility mode with version 8.2](https://its.1c.ru/db/metod8dev#content:5293:hdoc:_top:thisform)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingThisForm-off
// BSLLS:UsingThisForm-on
```

### Parameter for config

```json
"UsingThisForm": false
```
