# Unary Plus sign in string concatenation (UnaryPlusInConcatenation)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Error` | `BSL`<br>`OS` | `Blocker` | `Yes` | `1` | `suspicious`<br>`brainoverload` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When concatenating strings, a developer may mistakenly write code String + + String2, i.e. the second plus, the platform recognizes as unary and tries to cast to a number, which in most cases will lead to a runtime exception

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnaryPlusInConcatenation-off
// BSLLS:UnaryPlusInConcatenation-on
```

### Parameter for config

```json
"UnaryPlusInConcatenation": false
```
