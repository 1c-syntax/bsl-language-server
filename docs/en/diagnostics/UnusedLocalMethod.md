# Unused local method (UnusedLocalMethod)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Major` | `Yes` | `1` | `standard`<br>`suspicious` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Modules should not have unused procedures and functions.

## Sources

- [Standard: Modules](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnusedLocalMethod-off
// BSLLS:UnusedLocalMethod-on
```

### Parameter for config

```json
"UnusedLocalMethod": false
```
