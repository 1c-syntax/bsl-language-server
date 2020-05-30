# Cyclomatic complexity (CyclomaticComplexity)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Critical` | `Yes` | `25` | `brainoverload` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `complexityThreshold` | `Integer` | ```Complexity threshold``` | ```20``` |
| `checkModuleBody` | `Boolean` | ```Check module body``` | ```true``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Cyclomatic complexity of the program code is one of the oldest metrics, it was first mentioned by Thomas McCab in 1976. Cyclomatic complexity shows the minimum number of required tests. The most effective way to reduce cyclomatic complexity is to decompose the code, split the methods into simpler ones, and also optimize logical expressions.

Cyclomatic complexity increases by 1 for each of following constructions:

- `For ... To .. Do`
- `For each ... Of ... Do`
- `If ... Then`
- `ElsIf ... Then`
- `Else`
- `Try ... Except ... EndTry`
- `GoTo ~Label`
- Binary operations `AND ... OR`
- Ternary operator
- `Procedure`
- `Function`

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
Функция СерверныйМодульМенеджера(Имя)                                                   // 1 	ОбъектНайден = Ложь;                                                                // 0                                                                                         // 0 	ЧастиИмени = СтрРазделить(Имя, ".");                                                // 0 	Если ЧастиИмени.Количество() = 2 Тогда                                              // 1                                                                                         // 0 		ИмяВида = ВРег(ЧастиИмени[0]);                                                  // 0 		ИмяОбъекта = ЧастиИмени[1];                                                     // 0                                                                                         // 0 		Если ИмяВида = ВРег("Константы") Тогда                                          // 1 			Если Метаданные.Константы.Найти(ИмяОбъекта) <> Неопределено Тогда           // 1 				ОбъектНайден = Истина;                                                  // 0 			КонецЕсли;                                                                  // 0 		ИначеЕсли ИмяВида = ВРег("РегистрыСведений") Тогда                              // 1 			Если Метаданные.РегистрыСведений.Найти(ИмяОбъекта) <> Неопределено Тогда    // 1 				ОбъектНайден = Истина;                                                  // 0 			КонецЕсли;                                                                  // 0 		Иначе                                                                           // 1 			ОбъектНайден = Ложь;                                                        // 0 		КонецЕсли;                                                                      // 0 	КонецЕсли;                                                                          // 0                                                                                         // 0 	Если Не ОбъектНайден Тогда                                                          // 1 		ВызватьИсключение СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку(      // 0 			НСтр("ru = 'Объект метаданных ""%1"" не найден,                             // 0 			           |либо для него не поддерживается получение модуля менеджера.'"), // 0 			Имя);                                                                       // 0 	КонецЕсли;                                                                          // 0 	УстановитьБезопасныйРежим(Истина);                                                  // 0 	Модуль = Вычислить(Имя);                                                            // 0 	F = ?(Условие, ИСТИНА, НЕОПРЕДЕЛЕНО);                                               // 1 	А = ?(Условие, ИСТИНА, ?(Условие2, ЛОЖЬ, НЕОПРЕДЕЛЕНО));                            // 2 	M = ИСТИНА ИЛИ 7;                                                                   // 1 	Возврат Модуль;                                                                     // 0 КонецФункции                                                                            // итог 12
```

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [Cyclomatic Complexity PHP](https://pdepend.org/documentation/software-metrics/cyclomatic-complexity.html)
- [Cyclomatic Complexity (RU)](https://ru.wikipedia.org/wiki/%D0%A6%D0%B8%D0%BA%D0%BB%D0%BE%D0%BC%D0%B0%D1%82%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%B0%D1%8F_%D1%81%D0%BB%D0%BE%D0%B6%D0%BD%D0%BE%D1%81%D1%82%D1%8C)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CyclomaticComplexity-off
// BSLLS:CyclomaticComplexity-on
```

### Parameter for config

```json
"CyclomaticComplexity": {
    "complexityThreshold": 20,
    "checkModuleBody": true
}
```
