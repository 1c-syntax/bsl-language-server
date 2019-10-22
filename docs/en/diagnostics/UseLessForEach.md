# Useless collection iteration

The absence of an iterator in the loop body indicates either a useless iteration of the collection or an error in the loop body.

Incorrect:

```Bsl

Для Каждого Итератор Из Коллекция Цикл

    ВыполнитьДействиеНадЭлементом(Коллекция);
    
КонецЦикла;

```

Correct:

```bsl

Для Каждого Итератор Из Коллекция Цикл

    ВыполнитьДействиеНадЭлементом(Итератор);
    
КонецЦикла;

```

```bsl

ВыполнитьДействиеНадКоллекцией(Коллекция);

```
