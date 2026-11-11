# Diagnostic suppression without explanation (MissingSuppressionComment)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

A diagnostic suppression comment (`// BSLLS:DiagnosticName-off` or `// BSLLS-off`) lacks an explanation. Future developers won't understand why the diagnostic was disabled or whether it can be re-enabled.

Both standalone comments and comments at the end of a code line are checked.

## Examples

### Incorrect

```bsl
// BSLLS:DeprecatedMethodCall-off
Procedure OldCode()

Message("Text"); // BSLLS:DeprecatedMethodCall-off
```

### Correct

```bsl
// BSLLS:DeprecatedMethodCall-off — legacy, do not touch until v3.0
Procedure OldCode()

Message("Text"); // BSLLS:DeprecatedMethodCall-off — legacy, do not touch until v3.0
```

## Notes

- Any non-empty text after the `-off`/`-выкл` directive is treated as an explanation; `-on`/`-вкл` comments are not checked.
- Supports both Russian (`выкл`) and English (`off`) forms.
- Covers both BSL and OneScript documents.
- This diagnostic is **not suppressable**: its metadata declares `suppressible = false`, so `// BSLLS:MissingSuppressionComment-off` and `// BSLLS-off` comments do not hide its findings.
