# Control flow statements should not be nested too deep (NestedStatements)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL`<br>`OS` | `Critical` | `Yes` | `30` | `badpractice`<br>`brainoverload` 

## Parameters 

 Name | Type | Description | Default value 
 :-: | :-: | :-- | :-: 
 `maxAllowedLevel` | `Integer` | ```Max nested level``` | ```4``` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Nested operators "If", "For", "ForEach", "While" and "Try" are key ingredients for so called "spaghetti-code".

Such code is hard for reading, refactoring and support.

## Examples

Incorrect:

```bsl

If Something Then                  // Acceptable - level = 1
  /* ... */
  If SomethingElse Then             // Acceptable - level = 2
    /* ... */
    For Nom = 0 to 10 Loop          // Acceptable - level = 3
      /* ... */
      If OneMoreCondition Then     // Acceptable - level = 4, limit is reached, not yet breached
        If SomethingElse Then        // Level = 5, Limit is breached
          /* ... */
        EndIf;
        Return;
      EndIf;
    EndLoop;
  EndIf;
EndIf;
```

## Sources

- [RSPEC-134](https://rules.sonarsource.com/java/RSPEC-134)

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
