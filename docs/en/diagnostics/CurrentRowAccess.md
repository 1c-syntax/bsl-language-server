# Accessing data via CurrentRow of dynamic list (CurrentRowAccess)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostic description

Accessing properties or methods via `CurrentRow` of a dynamic list triggers a database object read per row. Use `CurrentData` instead, which works with already-fetched row data.

## Examples

### Incorrect

```bsl
Value = List.CurrentRow.Name;
List.CurrentRow.Write();
```

### Correct

```bsl
Value = List.CurrentData.Name;
```

## See also

- [Dynamic list documentation](https://its.1c.ru/db/metod8dev/content/2812/hdoc)
