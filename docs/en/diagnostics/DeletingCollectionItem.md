# Deleting an item when iterating through collection using the operator "For each ... In ... Do" (DeletingCollectionItem)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Don't delete elements of collection when iterating through collection using the operator **For each ... In ... Do**. Because it change index of next element.

Example:

```bsl
For each Element In Collection Do
   Collection.Delete(Element)
EndDo;
```

Alternatively, remove elements from the end:

```bsl
IndexOf = Numbers.UBound();
While IndexOf >= 0 Do
    If Numbers[IndexOf] < 10 Then
        Numbers.Delete(IndexOf);
    EndIf;
    IndexOf = IndexOf – 1;
EndDo;
```

## Sources

* [1C: Programming for Beginners. Development in the system "1C: Enterprise 8.3" (RU)](https://its.1c.ru/db/pubprogforbeginners#content:88:hdoc)
