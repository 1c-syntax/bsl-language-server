# Using 'LIKE' in query (UsingLikeInQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

In most algorithms, it is possible to do without using the operator `LIKE`, and in the rest, you must use it carefully. The result in some situations can be very different from the expected, for example, when using different DBMS.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

### Diagnostic ignorance in code

```bsl
Property LIKE "123%"
```

### Parameter for config

```bsl
Property LIKE Table.Template
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Standard. Features of use in operator requests LIKE](https://its.1c.ru/db/v8std#content:726:hdoc)
- [Developers guide. Pattern-like string validation operator](https://its.1c.ru/db/v8318doc#bookmark:dev:TI000000506)
