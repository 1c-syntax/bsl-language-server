# Приведение к числу в попытке (TryNumber)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Неправильно использовать исключения для приведения значения к типу. Для таких операций необходимо использовать возможности объекта ОписаниеТипов.

## Примеры

Неправильно:

```bsl
Попытка
 КоличествоДнейРазрешения = Число(Значение);
Исключение
 КоличествоДнейРазрешения = 0; // значение по умолчанию
КонецПопытки;
```

Правильно:

```bsl
ОписаниеТипа = Новый ОписаниеТипов("Число");
КоличествоДнейРазрешения = ОписаниеТипа.ПривестиЗначение(Значение);
```

## Источники

* [Стандарт: Перехват исключений в коде](https://its.1c.ru/db/v8std#content:499:hdoc)
