# Using service tags

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Yes` | `0` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `serviceTags` | `String` | ```Service tags``` | ```"todo|fixme|!!|mrg|@|отладка|debug|для\\s*отладки|(\\{\\{|\\}\\})КОНСТРУКТОР_|(\\{\\{|\\}\\})MRG"``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic finds use of service tags in comments. Tags list:

- TODO
- FIXME
- !!
- @
- MRG
- ОТЛАДКА
- ДЛЯ ОТЛАДКИ
- КОНСТРУКТОР_ЗАПРОСА_С_ОБРАБОТКОЙ_РЕЗУЛЬТАТА
- КОНСТРУКТОР_ДВИЖЕНИЙ_РЕГИСТРОВ
- КОНСТРУКТОР_ПЕЧАТИ
- КОНСТРУКТОР_ВВОДА_НА_ОСНОВАНИИ

Tags list can be extended via options.
