# Cast to number of try catch block

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Major` | `Yes` | `2` | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is incorrect to use exceptions for cast value to type. For such operations use object ОписаниеТипов.

## Examples

Incorrect:

```bsl
Попытка
 КоличествоДнейРазрешения = Число(Значение);
Исключение
 КоличествоДнейРазрешения = 0; // значение по умолчанию
КонецПопытки;
```

Correct:

```bsl
ОписаниеТипа = Новый ОписаниеТипов("Число");
КоличествоДнейРазрешения = ОписаниеТипа.ПривестиЗначение(Значение);
```

## Sources

* [Standard: Catch exceptions in code](https://its.1c.ru/db/v8std#content:499:hdoc)
