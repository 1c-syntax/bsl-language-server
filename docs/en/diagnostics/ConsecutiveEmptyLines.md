# Consecutive empty lines (ConsecutiveEmptyLines)

|      Type      |    Scope    |     Severity     |    Activated<br>by default    |    Minutes<br>to fix    |     Tags      |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:-------------:|
| `Code smell` |         `BSL`<br>`OS`         | `Info` |              `Yes`              |                 `1`                 | `badpractice` |

## Parameters


|           Name            |   Type   |                     Description                      |    Default value    |
|:------------------------:|:-------:|:-------------------------------------------------:|:------------------------------:|
| `allowedEmptyLinesCount` | `Integer` | `Maximum allowed consecutive empty lines` |              `1`               |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

To separate blocks of code among themselves, insert an empty line.

Inserting 2 or more empty lines does not carry this value and leads to a meaningless increase in the length of the method or module.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ConsecutiveEmptyLines-off
// BSLLS:ConsecutiveEmptyLines-on
```

### Parameter for config

```json
"ConsecutiveEmptyLines": {
    "allowedEmptyLinesCount": 1
}
```
