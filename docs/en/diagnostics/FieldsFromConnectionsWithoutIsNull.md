# Inaccurate use of fields from tables of left/right connections, without checking for NULL or casting to NULL (FieldsFromConnectionsWithoutIsNull)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Error` | `BSL`<br>`OS` | `Critical` | `Yes` | `2` | `sql`<br>`suspicious`<br>`unpredictable` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Использование функции ЕСТЬNULL() - Стандарт](https://its.1c.ru/db/metod8dev/content/2653/hdoc)
* [Понятие "пустых" значений - Методические рекомендации 1С](https://its.1c.ru/db/metod8dev/content/2614/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
  * [Чем отличается значение типа Неопределено и значение типа Null? - Методические рекомендации 1С](https://its.1c.ru/db/metod8dev#content:2516:hdoc)
* [Особенности связи с виртуальной таблицей остатков - Методические рекомендации 1С](https://its.1c.ru/db/metod8dev/content/2657/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Сортировка по полю запроса, которое может потенциально содержать NULL - статья "Упорядочивание результатов запроса" - Стандарт](https://its.1c.ru/db/v8std/content/412/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Поля иерархического справочника могут содержать NULL - Методические рекомендации 1С](https://its.1c.ru/db/metod8dev/content/2649/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
  * [Как получить записи иерархической таблицы и расположить их в порядке иерархии - Методические рекомендации 1С](https://its.1c.ru/db/pubqlang/content/27/hdoc/_top/%D0%B5%D1%81%D1%82%D1%8C%20null)
* [Как получить данные из разных таблиц для одного и того же поля - онлайн-книга "Язык запросов 1С:Предприятия"](https://its.1c.ru/db/pubqlang#content:43:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FieldsFromConnectionsWithoutIsNull-off
// BSLLS:FieldsFromConnectionsWithoutIsNull-on
```

### Parameter for config

```json
"FieldsFromConnectionsWithoutIsNull": false
```
