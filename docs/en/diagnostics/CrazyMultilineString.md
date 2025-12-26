# Crazy multiline literals (CrazyMultilineString)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

In source text, multi-line constants can be initialized in two ways:

- 'classic', which uses line feed and string concatenation
- 'crazy' where lines are separated by whitespace

The second method complicates the perception; when using it, it is easy to make and miss a mistake.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Difficulty understanding:

```bsl
Строка = "ВВВ" "СС"
"Ф";
```

Classic variant:

```bsl
String = "BBB" + "CC"
         + "F";
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
