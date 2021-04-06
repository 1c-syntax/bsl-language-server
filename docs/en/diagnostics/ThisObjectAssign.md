# ThisObject assign (ThisObjectAssign)

 |  Type   | Scope | Severity  | Activated<br>by default | Minutes<br>to fix |  Tags   |
 |:-------:|:-----:|:---------:|:-----------------------------:|:-----------------------:|:-------:|
 | `Error` | `BSL` | `Blocker` |             `Yes`             |           `1`           | `error` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
In managed form modules and common modules, there should not be a variable named "ThisObject".

Often this error appears when updating the platform version: the "ThisObject" property of managed forms and common modules appeared in version 8.3.3, which could previously be used as a variable name. И могло быть использовано как переменная.

## Examples

Incorrect:
```bsl

ThisObject = FormAttributeToValue("Object");

```

## Sources

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ThisObjectAssign-off
// BSLLS:ThisObjectAssign-on
```

### Parameter for config

```json
"ThisObjectAssign": false
```
