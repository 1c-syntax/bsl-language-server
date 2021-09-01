# Prohibited words (BadWords)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |   Tags   |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |           `1`           | `design` |

## Parameters 


|    Name    |   Type   |                             Description                              | Default value |
|:----------:|:--------:|:--------------------------------------------------------------------:|:-------------:|
| `badWords` | `String` | `Regular expression for prohibited words. Ex.: "badWord1|badWord2".` |      ``       |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Software modules should not contain prohibited words.

For example: "cunt|dick|fuck".

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:BadWords-off
// BSLLS:BadWords-on
```

### Parameter for config

```json
"BadWords": {
    "badWords": ""
}
```
