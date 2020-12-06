# Typo (Typo)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Info` | `Yes` | `1` | `badpractice` 

## Parameters 

 Name | Type | Description | Default value 
 :-: | :-: | :-- | :-: 
 `minWordLength` | `Integer` | ```Minimum length for checked words``` | ```3``` 
 `userWordsToIgnore` | `String` | ```Dictionary for excluding words (comma separated)``` | `````` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Spell checking is done with [LanguageTool](https://languagetool.org/ru/). The strings are split into camelCase chunks and checked against a built-in dictionary.

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [American English Dictionary](https://dictionary.cambridge.org/dictionary/essential-american-english/)
- [LanguageTool page](https://languagetool.org/ru/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:Typo-off
// BSLLS:Typo-on
```

### Parameter for config

```json
"Typo": {
    "minWordLength": 3,
    "userWordsToIgnore": ""
}
```
