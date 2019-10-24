# Useless For Each loop

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `Нет` | `2` | `clumsy` |


## <TODO PARAMS>

## Description

The absence of an iterator in the loop body indicates either a useless iteration of the collection or an error in the loop body.

## Examples

Incorrect:

```bsl

Для Каждого Итератор Из Коллекция Цикл

    ыполнитьДействиеНадЭлементом(Коллекция);
    
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
