# Empty code block (EmptyCodeBlock)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |                Tags                 |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-----------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `5`           | `badpractice`<br>`suspicious` |

## Parameters


|      Name       |   Type    |    Description    | Значение<br>по умолчанию |
|:---------------:|:---------:|:-----------------:|:------------------------------:|
| `commentAsCode` | `Boolean` | `Comment as code` |            `false`             |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Empty blocks are a sign of a possible error:

- Forgot to implement
- Deleted content

Empty blocks of code must be filled or removed.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:EmptyCodeBlock-off
// BSLLS:EmptyCodeBlock-on
```

### Parameter for config

```json
"EmptyCodeBlock": {
    "commentAsCode": false
}
```
