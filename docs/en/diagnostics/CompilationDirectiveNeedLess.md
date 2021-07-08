# Needless compilation directive (CompilationDirectiveNeedLess)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                         Tags                          |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:-----------------------------------------------------:|
| `Code smell` | `BSL` | `Major`  |             `Yes`             |           `1`           |       `clumsy`<br>`standard`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Compilation directives:

```bsl
&AtClient (&НаКлиенте)
&AtServer (&НаСервере)
&AtServerNoContext (&НаСервереБезКонтекста)
```

Must be used only in the code of managed form modules and in the code of command modules. In other modules, we recommend use instructions to the preprocessor.

In server or client common modules, the execution context is obvious, so there is no sense in compilation directives. In common modules with client and server attributes, using compilation directives makes it difficult to understand which ones are procedures (functions) are available eventually.

## Sources
* Source: [The use of compilation directives and preprocessor instructions(RUS)](https://its.1c.ru/db/v8std#content:439:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CompilationDirectiveNeedLess-off
// BSLLS:CompilationDirectiveNeedLess-on
```

### Parameter for config

```json
"CompilationDirectiveNeedLess": false
```
