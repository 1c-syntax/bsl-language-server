# Code out of region (CodeOutOfRegion)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Info` | `Yes` | `1` | `standard`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The code of module should be structured and divided into regions.
 The requirement to structure code by regions is to improve code readability and maintainability and development by group of authors and in finalizing application solutions on specific implementations.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
#Область <ИмяОбласти>
```

Не смотря на то что в стандарте описано всего 10 имён, имена вложенных областей не проверяются.

Правильно:

```bsl
#Область СлужебныеПроцедурыИФункции
#Область Печать
// Код процедур и функций
#КонецОбласти
#Область Прочее
// Код процедур и функций
#КонецОбласти
#КонецОбласти
```

Таблица соответствия английских имён (полный список в [исходном коде](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/utils/Keywords.java#L255)):

русск. | англ.
--- | ---
ПрограммныйИнтерфейс | Public
СлужебныйПрограммныйИнтерфейс | Internal
СлужебныеПроцедурыИФункции | Private
ОбработчикиСобытий | EventHandlers
ОбработчикиСобытийФормы | FormEventHandlers
ОбработчикиСобытийЭлементовШапкиФормы | FormHeaderItemsEventHandlers
ОбработчикиКомандФормы | FormCommandsEventHandlers
ОписаниеПеременных | Variables
Инициализация | Initialize
ОбработчикиСобытийЭлементовТаблицыФормы | FormTableItemsEventHandlers

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- Reference [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:CodeOutOfRegion-off
// BSLLS:CodeOutOfRegion-on
```

### Parameter for config

```json
"CodeOutOfRegion": false
```
