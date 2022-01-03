# Unknown preprocessor symbol (UnknownPreprocessorSymbol)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |            Tags             |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:---------------------------:|
| `Error` |         `BSL`<br>`OS`         | `Critical` |              `Yes`              |                 `5`                 |    `standard`<br>`error`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The use of unknown preprocessor symbols is unacceptable, this can lead to various errors, including logical ones, when, for some reason, the platform will miss an error by simply ignoring the written code.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnknownPreprocessorSymbol-off
// BSLLS:UnknownPreprocessorSymbol-on
```

### Parameter for config

```json
"UnknownPreprocessorSymbol": false
```
