# Style element constructor (StyleElementConstructors)

|  Type   | Scope | Severity | Activated<br>by default | Minutes<br>to fix |               Tags                |
|:-------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Error` | `BSL` | `Minor`  |             `Yes`             |           `5`           | `standard`<br>`badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
You should use style elements to change the appearance, rather than setting specific values directly in the controls. This is required in order for similar controls to look the same in all forms where they occur.

Types of style elements:
* Color (set to RGB value)
* Font (type, size and style are set)
* Frame (set the type and width of the borders)

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
System of standards
* Source: [Standard: Style Elements](https://its.1c.ru/db/v8std#content:667:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:StyleElementConstructors-off
// BSLLS:StyleElementConstructors-on
```

### Parameter for config

```json
"StyleElementConstructors": false
```
