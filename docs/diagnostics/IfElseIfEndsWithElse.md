# Использование синтаксической конструкции Если...Тогда...ИначеЕсли... (IfElseIfEndsWithElse)

|      Тип      |    Поддерживаются<br>языки    | Важность |    Включена<br>по умолчанию    |    Время на<br>исправление (мин)    |     Теги      |
|:-------------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:-------------:|
| `Дефект кода` |         `BSL`<br>`OS`         | `Важный` |              `Да`              |                `10`                 | `badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Правило применяется всякий раз, когда условный оператор **Если Тогда ИначеЕсли** содержит одну или несколько конструкций **ИначеЕсли**.
За последней конструкцией **ИначеЕсли** должна следовать конструкция **Иначе**.

Требование к заключительной конструкции **Иначе** - это защитное программирование.
Такие конструкции устойчивы к возможным изменениям и не маскируют возможные ошибки.

Конструкция **Иначе** должна либо принимать соответствующие меры, либо содержать подходящий комментарий относительно того, почему не предпринимается никаких действий. 


## Примеры

Неправильно:

```bsl
Если ТипЗнч(ВходящийПараметр) = Тип("Структура") Тогда
	Результат = ЗаполнитьПоСтруктуре(ВходящийПараметр);
ИначеЕсли ТипЗнч(ВходящийПараметр) = Тип("Документ.Ссылка.ВажныйДокумент") Тогда
	Результат = ЗаполнитьПоДокументу(ВходящийПараметр);
КонецЕсли;
```

Правильно:

```bsl
Если ТипЗнч(ВходящийПараметр) = Тип("Структура") Тогда
	Результат = ЗаполнитьПоСтруктуре(ВходящийПараметр);
ИначеЕсли ТипЗнч(ВходящийПараметр) = Тип("Документ.Ссылка.ВажныйДокумент") Тогда
	Результат = ЗаполнитьПоДокументу(ВходящийПараметр);
Иначе
	ВызватьИсключение "Передан параметр неверного типа";
КонецЕсли;
```

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:IfElseIfEndsWithElse-off
// BSLLS:IfElseIfEndsWithElse-on
```

### Параметр конфигурационного файла

```json
"IfElseIfEndsWithElse": false
```
