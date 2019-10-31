# Number of parameters in method

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `No` | `30` | `standard`<br/>`brainoverload` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `maxParamsCount` | `int` | Допустимое количество параметров метода | `7` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is not recommended to declare many parameters in functions (best practice to use not more than seven parameters). In So doing there should not be many parameters with default values set (best practice to have not more than three such parameters). Otherwise code readability decreases. For example it is easy to make a mistake in number of commas passing optional parameters.

If need to pass many parameters to a function, it is recommended to group same-type parameters into one or more composite parameters of type Structure.

## Examples

Incorrect:

```bsl
// Create item in catalog "Goods"
Процедура СоздатьЭлементНоменклатуры(Наименование, ТоварУслуга, ЕдиницаИзмерения, ВесНетто, ПроверятьУникальность = Истина)

КонецПроцедуры
```

Correct:

Group parameters, having goods item properties into Structure ЗначенияРеквизитов.

```bsl
// Create item in catalog "Goods"
Процедура СоздатьЭлементНоменклатуры(ЗначенияРеквизитов, ПроверятьУникальность = Истина)
КонецПроцедуры
```

## Sources

* [Стандарт: Параметры процедур и функций](https://its.1c.ru/db/v8std#content:640:hdoc)
