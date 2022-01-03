# Line Length limit (LineLength)

|      Type      |    Scope    |     Severity     |    Activated<br>by default    |    Minutes<br>to fix    |               Tags                |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:---------------------------------:|
| `Code smell` |         `BSL`<br>`OS`         | `Minor` |              `Yes`              |                 `1`                 |    `standard`<br>`badpractice`    |

## Parameters


|           Name            |   Type    |                    Description                    |    Default value    |
|:------------------------:|:--------:|:----------------------------------------------:|:------------------------------:|
|     `maxLineLength`      | `Integer`  |     `Max length of string in characters`     |             `120`              |
| `checkMethodDescription` | `Boolean` | `Check length of strings in method descriptions` |             `true`             |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

If the line length is grater than 120 characters you should you line break. It is not recommended to have lines longer than 120 characters, except when line break is impossible (example, in code we have a string constant which is displayed without line breaks in message window using object MessageToUser).

## Sources

* [Standard: Modules texts(RU)](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:LineLength-off
// BSLLS:LineLength-on
```

### Parameter for config

```json
"LineLength": {
    "maxLineLength": 120,
    "checkMethodDescription": true
}
```
