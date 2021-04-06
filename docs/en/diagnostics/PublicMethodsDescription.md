# All public methods must have a description (PublicMethodsDescription)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |                            Tags                            |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------------------------------------------------------:|
 | `Code smell` | `BSL`<br>`OS` |  `Info`  |             `Yes`             |           `1`           | `standard`<br>`brainoverload`<br>`badpractice` |

## Parameters

 |       Name       |   Type    | Description                                                          | Default value |
 |:----------------:|:---------:|:-------------------------------------------------------------------- |:-------------:|
 | `checkAllRegion` | `Boolean` | `Test methods without regard to the areas in which they are located` |    `false`    | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
All public methods located inside regions must have a description.

## Sources
* ["Procedure and function defenition" standard. Paragraph 2 (RU)](https://its.1c.ru/db/v8std#content:453:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:PublicMethodsDescription-off
// BSLLS:PublicMethodsDescription-on
```

### Parameter for config

```json
"PublicMethodsDescription": {
    "checkAllRegion": false
}
```
