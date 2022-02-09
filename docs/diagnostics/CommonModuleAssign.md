# Присвоение общему модулю (CommonModuleAssign)

|   Тип    |    Поддерживаются<br>языки    |   Важность    |    Включена<br>по умолчанию    |    Время на<br>исправление (мин)    |  Теги   |
|:--------:|:-----------------------------:|:-------------:|:------------------------------:|:-----------------------------------:|:-------:|
| `Ошибка` |         `BSL`<br>`OS`         | `Блокирующий` |              `Да`              |                 `2`                 | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

При присвоении значения общему модулю будет вызвано исключение. 
Такая ситуация возможна когда в конфигурацию добавляется общий модуль с 
именем, которое уже задействовано для переменной. 

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:CommonModuleAssign-off
// BSLLS:CommonModuleAssign-on
```

### Параметр конфигурационного файла

```json
"CommonModuleAssign": false
```