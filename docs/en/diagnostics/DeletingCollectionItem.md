# Deleting an item when iterating through collection using the operator "For each ... In ... Do"

Don't delete elements of collection %s when iterating through collection using the 
operator **For each ... In ... Do**. Because it change index of next element.

Example:

```bsl
For each Element In Collection Do
   Collection.Delete(Element)
EndDo;
```

Alternatively remove elements from the end:

```bsl
IndexOf = Numbers.UBound();
While IndexOf >= 0 Do
    If Numbers[IndexOf] < 10 Then
        Numbers.Delete(IndexOf);
    EndIf;
    IndexOf = IndexOf – 1;
EndDo;
```

Refference: [1С:Программирование для начинающих. Разработка в системе "1С:Предприятие 8.3" (RU)](https://its.1c.ru/db/pubprogforbeginners#content:88:hdoc)