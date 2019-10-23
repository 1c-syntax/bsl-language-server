# Number of parameters in method

1. It is not recommended to declare many parameters in functions (best practice to use not more than seven parameters). In So doing there should not be many parameters with default values set (best practice to have not more than three such parameters). Otherwise code readability decreases. For example it is easy to make a mistake in number of commas passing optional parameters.

If need to pass many parameters to a function, it is recommended to group same-type parameters into one or more composite parameters of type Structure.

#### Example

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

Reference: [Стандарт: Параметры процедур и функций](https://its.1c.ru/db/v8std#content:640:hdoc)

## Parameters

- `maxOptionalParamsCount` - `Integer` - Max number of optional parameters in method. By default set to 3.
