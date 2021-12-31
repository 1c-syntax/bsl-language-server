# Control flow statements should not be nested too deep (NestedStatements)

|     Type     |        Scope        |  Severity  | Activated<br>by default | Minutes<br>to fix |                  Tags                  |
|:------------:|:-------------------:|:----------:|:-----------------------------:|:-----------------------:|:--------------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Critical` |             `Yes`             |          `30`           | `badpractice`<br>`brainoverload` |

## Parameters


|       Name        |   Type    |    Description     | Default value |
|:-----------------:|:---------:|:------------------:|:-------------:|
| `maxAllowedLevel` | `Integer` | `Max nested level` |      `4`      |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Nested "If, "For", "For Each", "While" and "Try" operators are the key ingredients for creating so-called "spaghetti code".

Such code is hard for reading, refactoring and support.

## Examples

Wrong

```bsl

If Something Then                  // Allowed - level = 1
  /* ... */
  If Some Then             // Allowed - level = 2
    /* ... */
    For Num = 0 To 10 Do          // Allowed - level = 3
      /* ... */
      If OneMoreCondition Then     // Acceptable - level = 4, limit is reached, not yet breached
        If SomethingElse Then        // Level = 5, Limit is breached
          /* ... */
        EndIf;
        Return;
      EndIf;
    EndDo;
  EndIf;
EndIf;

```

## Sources

* [RSPEC-134](https://rules.sonarsource.com/java/RSPEC-134)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:NestedStatements-off
// BSLLS:NestedStatements-on
```

### Parameter for config

```json
"NestedStatements": {
    "maxAllowedLevel": 4
}
```
