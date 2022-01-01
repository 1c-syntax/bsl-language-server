# Prohibited words (BadWords)

|     Type     |        Scope        | Severity | Activated by default | Minutes<br> to fix |   Tags   |
|:------------:|:-------------------:|:--------:|:--------------------:|:------------------------:|:--------:|
| `Code smell` | `BSL`<br>`OS` | `Важный` |         `No`         |           `1`            | `design` |

## Parameters


|    Name    |   Type   |                Description                | Default<br>value |
|:----------:|:--------:|:-----------------------------------------:|:----------------------:|
| `badWords` | `String` | `Regular expression for exclusion words.` |          ``          |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
There should be no forbidden words in the text of the modules. The list of forbidden words is given by a regular expression. The search is made case-insensitive.

**Sample setup:**

"редиска|лопух|экзистенциальность"

"ло(х|шара|шпед)"

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
