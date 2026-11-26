# Suspicious use of &ChangeAndValidate (SuspiciousChangeAndValidate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

A method annotated with `&ChangeAndValidate` contains both `#Delete` / `#EndDelete` and `#Insert` / `#EndInsert` directives, indicating full method body replacement that hides changes from reviewers.

`&ChangeAndValidate` is designed for targeted modifications. For full replacement, use `&Instead`.

## Examples

### Incorrect

```bsl
&ChangeAndValidate("ConfigurationMethod")
Procedure prefConfigurationMethod()

#Delete
    old code
#EndDelete
#Insert
    new code
#EndInsert

EndProcedure
```

### Correct

```bsl
// Targeted changes
&ChangeAndValidate("ConfigurationMethod")
Procedure prefConfigurationMethod()
    ... // original code

#Delete
    problematic line
#EndDelete
#Insert
    fixed line
#EndInsert

    ... // rest of code
EndProcedure

// Or full replacement via &Instead
&Instead("ConfigurationMethod")
Procedure prefConfigurationMethod()
    new code
EndProcedure
```
