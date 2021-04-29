# Empty statement (EmptyStatement)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Info` | `Yes` | `1` | `badpractice` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

An empty statement is an operator consisting only of a semicolon (";"). Usually appears when:

- refactoring, when the developer deleted a part of the code, but forgot to delete the last ";"
- "copy paste", when the developer pasted the copied code containing the final character ";"
- inattentive, when the developer twice (or even more) times clicked the symbol ";"

An empty statement does not lead to code errors, but clutters it, reducing perception.

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:EmptyStatement-off
// BSLLS:EmptyStatement-on
```

### Parameter for config

```json
"EmptyStatement": false
```
