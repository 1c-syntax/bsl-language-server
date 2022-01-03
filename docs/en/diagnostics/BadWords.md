# Prohibited words (BadWords)

|      Type      |    Scope    | Severity |    Activated<br>by default    |    Minutes<br>to fix    |   Tags   |
|:-------------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:--------:|
| `Code smell` |         `BSL`<br>`OS`         | `Major` |             `No`              |                 `1`                 | `design` |

## Parameters


|    Name     |   Type    |                  Description                   |    Default value    |
|:----------:|:--------:|:-------------------------------------------:|:------------------------------:|
| `badWords` | `String` | `Regular expression for prohibited words.` |               ``               |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Software modules should not contain prohibited words.
The list of forbidden words is set by a regular expression.
The search is case-insensitive.

**For example:**

"singularity|avada kedavra|Donald"

"transcenden(tal|ce)"

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
