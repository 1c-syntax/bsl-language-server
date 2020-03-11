# Function name must't start with Get (FunctionNameStartWithGet)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Yes` | `3` | `standard` |

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
* Source: [Standard: Names of procedures and functions](its.1c.ru/db/v8std#content:647:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FunctionNameStartWithGet-off
// BSLLS:FunctionNameStartWithGet-on
```

### Parameter for config

```json
"FunctionNameStartWithGet": false
```
