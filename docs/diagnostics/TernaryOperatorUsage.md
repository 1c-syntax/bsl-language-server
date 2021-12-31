# Использование тернарного оператора (TernaryOperatorUsage)

|      Тип      |    Поддерживаются<br>языки    |     Важность     |    Включена<br>по умолчанию    |    Время на<br>исправление (мин)    |      Теги       |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:---------------:|
| `Дефект кода` |         `BSL`<br>`OS`         | `Незначительный` |             `Нет`              |                 `3`                 | `brainoverload` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Вместо тернарного оператора стоит использовать конструкцию "Если-Иначе".

## Примеры

Плохо:

```bsl
Результат  =  ?(x % 15 <> 0, ?( x % 5 <> 0, ?( x % 3 <> 0, x, "Fizz"), "Buzz"), "FizzBuzz"); 
```

Хорошо:

```bsl
Если x % 15 = 0 Тогда
	Результат = "FizzBuzz";
ИначеЕсли x % 3 = 0 Тогда
	Результат = "Fizz";
ИначеЕсли x % 5 = 0 Тогда
	Результат = "Buzz";
Иначе
	Результат = x;
КонецЕсли;
```

Плохо:

```bsl
Если ?(Стр.Emp_emptype = Null, 0, Стр.Emp_emptype) = 0 Тогда
      Статус = "Готово";
КонецЕсли;
```
Хорошо:

```bsl
Если Стр.Emp_emptype = Null ИЛИ Стр.Emp_emptype = 0 Тогда
      Статус = "Готово";
КонецЕсли;
```

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:TernaryOperatorUsage-off
// BSLLS:TernaryOperatorUsage-on
```

### Параметр конфигурационного файла

```json
"TernaryOperatorUsage": false
```