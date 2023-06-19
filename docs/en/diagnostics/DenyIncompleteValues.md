# Deny incomplete values for dimensions (DenyIncompleteValues)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Often when designing a metadata structure, it is required that the dimensions of a register must always be filled with values (should not be empty).

Checking for completeness of the dimension value should be done using the "Deny Incomplete Values" flag for the register dimension; additional software control of measurement filling is not required.
It is assumed that records with an empty dimension value do not make sense in the infobase.
The absence of a set flag can lead to potential problems if the application developers have not provided a value validation algorithm.

The current rule may give a lot of false positives, use at your own risk.

The rule applies to the following registers:
- information register
- accumulation register
- accounting register
- calculation register

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
- [Development of the interface for applied solutions on the "1C:Enterprise" platform (RU).  Ch "Fill check and check on write"](https://its.1c.ru/db/pubv8devui#content:225:1)
- [Developer's Guide - Properties of a dimension (resource, attribute) of the information register (RU)](https://its.1c.ru/db/v8323doc#bookmark:dev:TI000000349)
- [Developer's Guide - Properties of a dimension (resource, attribute) of the accumulation register (RU)](https://its.1c.ru/db/v8323doc#bookmark:dev:TI000000363)
