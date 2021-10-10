# Crazy multiline literals (CrazyMultilineString)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                             Tags                             |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------------------------------------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |           `1`           |       `badpractice`<br>`suspicious`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

In source text, multi-line constants can be initialized in two ways:
- 'classic', which uses line feed and string concatenation
- 'crazy' where lines are separated by whitespace

The second method complicates the perception; when using it, it is easy to make and miss a mistake.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CrazyMultilineString-off
// BSLLS:CrazyMultilineString-on
```

### Parameter for config

```json
"CrazyMultilineString": false
```
