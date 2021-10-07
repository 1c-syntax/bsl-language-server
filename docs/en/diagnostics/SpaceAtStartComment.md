# Space at the beginning of the comment (SpaceAtStartComment)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` |    `BSL`<br>`OS`    |  `Info`  |             `Yes`             |           `1`           | `standard` |

## Parameters


|         Name          |   Type    |                                        Description                                         |  Default value  |
|:---------------------:|:---------:|:------------------------------------------------------------------------------------------:|:---------------:|
| `commentsAnnotation`  | `String`  | `Skip comments-annotations staring with given substrings. List, values separated by comma` | `//@,//(c),//©` |
| `useStrictValidation` | `Boolean` |               `Use strict validation against double comments `//// Comment``               |     `true`      |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Between comment symbols "//" and comment text has to be a space.

Exception from the rule is _**comments-annotations**_, comments starting with special symbols sequence.

## Sources

* [Standard: Modules text, Item 7.3](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SpaceAtStartComment-off
// BSLLS:SpaceAtStartComment-on
```

### Parameter for config

```json
"SpaceAtStartComment": {
    "commentsAnnotation": "//@,//(c),//©",
    "useStrictValidation": true
}
```
