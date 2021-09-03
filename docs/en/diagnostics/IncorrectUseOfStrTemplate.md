# Incorrect use of "StrTemplate" (IncorrectUseOfStrTemplate)

|  Type   |        Scope        | Severity  | Activated<br>by default | Minutes<br>to fix |                              Tags                              |
|:-------:|:-------------------:|:---------:|:-----------------------------:|:-----------------------:|:--------------------------------------------------------------:|
| `Error` | `BSL`<br>`OS` | `Blocker` |             `Yes`             |           `1`           | `brainoverload`<br>`suspicious`<br>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When using the `StrTemplate` method, you must carefully compose the template string and pass the correct number of parameters. So, it is quite easy to make mistakes when passing values for `StrTemplate`.

It is important to remember that
- `StrTemplate` only accepts parameters from `%1` to `%10`
- if you want to pass a number immediately after the template, you need to add parentheses - `"%(1)45"`

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Вариант 1 - количество переданных после шаблонной строки значений не равно (меньше или больше) максимальному номеру из строки вида %N внутри шаблонной строки

  - `СтрШаблон("Наименование (версия %1)"); // не передан необходимый параметр для %1`
  - `СтрШаблон("%1 (версия %2)", Наименование); // не передан необходимый параметр для %2`

Вариант 2 - не передается вообще никаких значений, кроме форматированной строки из-за большого количества скобок внутри несложного выражения с `НСтр` и `СтрШаблон`:

  - `СтрШаблон(НСтр("ru='Наименование (версия %1)'", Версия()));`

Здесь ошибочно не закрыта скобка для `НСтр`. В итоге выражение после вычисления `НСтр` становится пустым. Выявить подобную ошибку чтением кода довольно сложно из-за наличия скобок. И можно поймать только в рантайме, получив исключение.

Правильный вариант
  - `СтрШаблон(НСтр("ru='Наименование (версия %1)'"), Версия());`

Вариант 3 - правильный пример передачи цифр сразу после шаблонного значения
  - `СтрШаблон("Наименование %(1)2"), Наименование); // если передать значение "МояСтрока", то результат будет "МояСтрока2"`

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Требования по локализации - Стандарт](https://its.1c.ru/db/v8std/content/763/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IncorrectUseOfStrTemplate-off
// BSLLS:IncorrectUseOfStrTemplate-on
```

### Parameter for config

```json
"IncorrectUseOfStrTemplate": false
```
