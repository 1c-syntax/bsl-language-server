# Typo (Typo)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Yes` | `1` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `userWordsToIgnore` | `String` | ```Dictionary for excluding words``` | `````` |
| `minWordLength` | `Integer` | ```Minimum length for checked words``` | ```3``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Spellchecking is using LanguageTool. Strings are divided by camelCase
and checked in the built-in dictionary.

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [American English Dictionary](https://dictionary.cambridge.org/dictionary/essential-american-english/)
* [Source](https://languagetool.org/en/)
<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:Typo-off
// BSLLS:Typo-on
```

### Parameter for config

```json
"Typo": {
    "userWordsToIgnore": "",
    "minWordLength": 3
}
```

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
    "userWordsToIgnore": "",
    "minWordLength": 3
}
```
