# Function returned values description is missing (MissingReturnedValueDescription)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |               Tags                |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `5`           | `standard`<br>`badpractice` |

## Параметры


|                 Имя                 |   Тип    |                      Описание                       | Значение<br>по умолчанию |
|:-----------------------------------:|:--------:|:---------------------------------------------------:|:------------------------------:|
| `allowShortDescriptionReturnValues` | `Булево` | `Разрешить краткое описание возвращаемого значения` |             `true`             |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The description of a method (procedure or function) should be formatted correctly to help programmers use the functionality correctly.

The function description must contain a description of the return value in the block of the same name. You must provide a description for all possible return types.

Diagnostics detects typical errors:

- No return value description
- Return value description for procedure
- Некачественное описание возвращаемого значения: когда имя типа присутствует в описании, но не указано его описание
  - Для активации этой, более строгой проверки, необходимо снять разрешение краткой формы записи параметром диагностики

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Standart: [Procedures and functions description](https://its.1c.ru/db/v8std#content:453:hdoc)

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingReturnedValueDescription-off
// BSLLS:MissingReturnedValueDescription-on
```

### Parameter for config

```json
"MissingReturnedValueDescription": {
    "allowShortDescriptionReturnValues": true
}
```
