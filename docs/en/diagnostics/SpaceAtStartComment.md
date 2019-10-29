# Space at the beginning of the comment

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Нет` | `1` | `standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `commentsAnnotation` | `Pattern` | Пропускать комментарии-аннотации, начинающиеся с указанных подстрок. Список через запятую. Например: //@,//(c) | `"//@,//(c),//©"` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Between comment symbols "//" and comment text has to be a space.

Exception from the rule is ***comments-annotations***, comments starting with special symbols sequence.

## Sources

* [Standard: Modules text, Item 7.3](https://its.1c.ru/db/v8std#content:456:hdoc)
