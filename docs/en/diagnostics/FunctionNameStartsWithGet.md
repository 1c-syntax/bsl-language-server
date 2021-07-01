# Function name shouldn't start with "Получить" (FunctionNameStartsWithGet)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Теги    |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL`<br>`OS` |  `Info`  |             `No`              |           `3`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In the name of the function, the word get superfluous since function by definition returns a value.

## Examples
```bsl
// Not correct: 
Function GetNameByCode()

// Correct: 
Function NameByCode()
```


## Sources
* Source: [Standard: Names of procedures and functions c 6.1](https://its.1c.ru/db/v8std#content:647:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FunctionNameStartsWithGet-off
// BSLLS:FunctionNameStartsWithGet-on
```

### Parameter for config

```json
"FunctionNameStartsWithGet": false
```
