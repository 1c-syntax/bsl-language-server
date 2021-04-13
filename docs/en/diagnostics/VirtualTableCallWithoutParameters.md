# Virtual table call without parameters (VirtualTableCallWithoutParameters)

 |  Type   | Scope |  Severity  | Activated<br>by default | Minutes<br>to fix |                       Tags                       |
 |:-------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:------------------------------------------------:|
 | `Error` | `BSL` | `Critical` |             `Yes`             |           `5`           | `sql`<br>`standard`<br>`performance` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
При использовании виртуальных таблиц в запросах, следует передавать в параметры таблиц все условия, относящиеся к данной виртуальной таблице.

Не рекомендуется обращаться к виртуальным таблицам при помощи условий в секции ГДЕ и т.п.

Такой запрос будет возвращать правильный (с точки зрения функциональности) результат, но СУБД будет намного сложнее выбрать оптимальный план для его выполнения. В некоторых случаях это может привести к ошибкам оптимизатора СУБД и значительному замедлению работы запроса.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Например, следующий запрос использует секцию `ГДЕ` запроса для выборки из виртуальной таблицы:
```bsl
Query.Text = "SELECT
| Good
|FROM
| AccumulationRegister.MyGoods.Turnovers()
|WHERE
| Warehouse = &Warehouse";
```
Возможно, что в результате выполнения этого запроса сначала будут выбраны все записи виртуальной таблицы, а затем из них будет отобрана часть, соответствующая заданному условию.

Рекомендуется ограничивать количество выбираемых записей на самом раннем этапе обработки запроса. Для этого следует передать условия в параметры виртуальной таблицы.

```bsl
Query.Text = "SELECT
| Good
|FROM
| AccumulationRegister.MyGoods.Turnovers(, Warehouse = &Warehouse)";
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Source: [Standard: Using virtual tables (RU)](https://its.1c.ru/db/v8std#content:657:hdoc)
* Source: [Standard: Effective use of the virtual table «Turnovers» (RU)](https://its.1c.ru/db/v8std#content:733:hdoc)
* Источник: [Рекомендация 1С: Использование параметра Условие при обращении к виртуальной таблице](https://its.1c.ru/db/metod8dev/content/5457/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:VirtualTableCallWithoutParameters-off
// BSLLS:VirtualTableCallWithoutParameters-on
```

### Parameter for config

```json
"VirtualTableCallWithoutParameters": false
```
