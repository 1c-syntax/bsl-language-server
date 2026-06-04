# Variables declaration in common module (CommonModuleVariables)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In the 1C:Enterprise platform, common modules operate as libraries of procedures and functions and do not retain state between calls. Declaring module-level variables using the "Var" operator causes the variable to be reinitialized upon every call to the module.

To manage state, context must be passed explicitly via procedure and function parameters, data structures, or platform-specific caching mechanisms.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect code:
```bsl
Var CacheValues; // Variable loses its value after the call ends

Function GetCache() Export
    Return CacheValues;
EndFunction
```
Correct code:
```bsl
Function GetCache(CurrentCache) Export // State is passed explicitly via parameters
    Return CurrentCache;
EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
Source: [What variables, procedures and functions can be used in common modules?](https://its.1c.ru/db/metod8dev/content/2392/hdoc)
<!-- Примеры источников
* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
