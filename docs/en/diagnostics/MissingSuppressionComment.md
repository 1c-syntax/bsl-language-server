# Diagnostic suppression without explanation (MissingSuppressionComment)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

A diagnostic suppression comment (`// BSLLS:DiagnosticName-off` or `// BSLLS-off`) lacks an explanation. Future developers won't understand why the diagnostic was disabled or whether it can be re-enabled.

## Examples

### Incorrect

```bsl
// BSLLS:DeprecatedMethodCall-off
Procedure OldCode()
```

### Correct

```bsl
// BSLLS:DeprecatedMethodCall-off — legacy, do not touch until v3.0
Procedure OldCode()
```

## Notes

- This diagnostic is **not suppressable** via `// BSLLS:MissingSuppressionComment-off`
- Supports both Russian (`выкл`) and English (`off`) forms
