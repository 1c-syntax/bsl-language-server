# One statement per line (OneStatementPerLine)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Minor` | `Yes` | `2` | `standard`<br>`design` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Module texts are formatted according to the "one statement in one line" principle. Several statements are acceptable in case of same-type assignment operators. For example:

`StartIndex = 0; Index = 0; Result = 0;`

## Sources

* [Standard: Modules texts(RU)](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:OneStatementPerLine-off
// BSLLS:OneStatementPerLine-on
```

### Parameter for config

```json
"OneStatementPerLine": false
```
