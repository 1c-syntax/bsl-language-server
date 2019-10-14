# Cast to number of try catch block

It is incorrect to use exceptions for cast value to type. For such operations use object ОписаниеТипов.

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

Reference: [Standard: Catch exceptions in code](https://its.1c.ru/db/v8std#content:499:hdoc)
