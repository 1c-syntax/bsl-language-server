# Deleting an item when iterating through collection using the operator "For each ... In ... Do" (DeletingCollectionItem)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Error` | `BSL`<br>`OS` | `Major` | `Yes` | `5` | `standard`<br>`error` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Don't delete elements of collection %s when iterating through collection using the operator **For each ... In ... Do**. Because it change index of next element.

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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeletingCollectionItem-off
// BSLLS:DeletingCollectionItem-on
```

### Parameter for config

```json
"DeletingCollectionItem": false
```
