# Useless collection iteration (UseLessForEach)

|  Type   |        Scope        |  Severity  |    Activated<br>by default    |    Minutes<br>to fix    |   Tags   |
|:-------:|:-------------------:|:----------:|:-----------------------------:|:-----------------------:|:--------:|
| `Error` |    `BSL`<br>`OS`    | `Critical` |             `Yes`             |           `2`           | `clumsy` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The absence of an iterator in the loop body indicates either a useless iteration of the collection or an error in the loop body.

## Examples

Incorrect:

```Bsl

For Each Iterator From Collection Loop

    ProcessElement(Collection);

EndLoop;

```

Correct:

```Bsl

For Each Iterator From Collection Loop

    ProcessElement(Iterator);

EndLoop;

```

```bsl

ProcessCollection(Collection);

```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UseLessForEach-off
// BSLLS:UseLessForEach-on
```

### Parameter for config

```json
"UseLessForEach": false
```
