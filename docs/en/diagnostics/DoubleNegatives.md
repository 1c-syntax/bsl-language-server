# Double negatives (DoubleNegatives)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Using double negatives makes the code harder to understand and can lead to errors when the developer mentally computes False instead of True, or vice versa.
It is recommended to replace double negatives with conditional expressions that directly express the author's intentions.

## Examples

### Incorrect

```bsl
If Not ValueTable.Find(SearchValue, "Column") <> Undefined Then
    // Do the action
EndIf;
```

### Correct

```bsl
If ValueTable.Find(LookupValue, "Column") = Undefined Then
    // Perform action
EndIf;
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Remove double negative](https://www.refactoring.com/catalog/removeDoubleNegative.html)
