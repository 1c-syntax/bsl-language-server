# Number of parameters in method (NumberOfParams)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is not recommended to declare many parameters in functions (best practice to use not more than seven parameters). In So doing there should not be many parameters with default values set (best practice to have not more than three such parameters). Otherwise code readability decreases. For example it is easy to make a mistake in number of commas passing optional parameters.

If need to pass many parameters to a function, it is recommended to group same-type parameters into one or more composite parameters of type Structure.

## Examples

Incorrect:

```bsl
// Create an item in catalog "Goods"
Procedure CreateSKU(Name, Goods, Units, Weight, Check = True)
// ... 
EndProcedure
```

Correct:  
Group parameters, having goods item properties into Structure Values.

```bsl
// Create item in catalog "Goods"
Procedure CreateNewGoods(Values, Check = True)
EndProcedure
```

## Sources

* [Standard: Parameters of procedures and functions (RU)](https://its.1c.ru/db/v8std#content:640:hdoc)
