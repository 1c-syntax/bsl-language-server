# Double negatives (DoubleNegatives)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Using double negatives complicates the understanding of the code and can lead to errors when instead of truth the developer "in his mind" calculated False, or vice versa. It is recommended to replace double negatives with conditions that directly express the author's intentions.

## Examples

### Wrong

```bsl
If Not ValueTable.Find(ValueToSearch, "Column") <> Undefined Тогда
    // Act
EndIf;
```

### Correct

```bsl
If ValueTable.Find(ValueToSearch, "Column") = Undefined Тогда
    // Act
EndIf;
```

## Sources

* Источник: [Remove double negative](https://www.refactoring.com/catalog/removeDoubleNegative.html)