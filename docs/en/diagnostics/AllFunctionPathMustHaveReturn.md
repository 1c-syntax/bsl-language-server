# All execution paths of a function must have a Return statement (AllFunctionPathMustHaveReturn)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |                             Tags                             |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:------------------------------------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `1`           | `unpredictable`<br>`badpractice`<br>`suspicious` |

## Parameters


|            Name            |   Type    |                                                     Description                                                     | Default value |
|:--------------------------:|:---------:|:-------------------------------------------------------------------------------------------------------------------:|:-------------:|
| `loopsExecutedAtLeastOnce` | `Boolean` |                                      `Assume loops are executed at least once`                                      |    `true`     |
| `ignoreMissingElseOnExit`  | `Boolean` | `Ignore ElIf clauses which has no Else branch. Disable to detect exits from ElIf condition which results to False.` |    `false`    |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Functions should not have an implicit return. All returned values must be shown excplicitly.

Как правило, это не является штатным функционированием, программист должен явно описать все возвращаемые значения функции. Однако, довольно легко пропустить ситуацию, при которой управление дойдет до строки КонецФункции и вернется непредусмотренное значение Неопределено.

Данная диагностика проверяет, что все возможные пути выполнения функции имеют явный оператор Возврат и функция не возвращает непредвиденных значений.

## Examples

### Incorrect

```bsl
// if the rate is full, but not Tax10 not Tax10 - returns Undefined
// this could be error or planned behavior
Function DefineTaxRate(Val Rate)
    If Rate = Enums.TaxRates.Tax20 Then
        Return 20;
    ElsIf Rate = Enums.TaxRates.Tax10 Then
        Return 10;
    ElsIf Not ValueIsFilled(Rate) Then
        Return Constants.DefaultTaxRate.Get();
    EndIf;

    // implicit return Undefined
EndFunction
```

### Correct

```
// explicitly specify the intention to return the result in the end of the function.
Function DefineTaxRate(Val Rate)
    If Rate = Enums.TaxRates.Tax20 Then
        Return 20;
    ElsIf Rate = Enums.TaxRates.Tax10 Then
        Return 10;
    ElsIf Not ValueIsFilled(Rate) Then
        Return Constants.DefaultTaxRate.Get();
    EndIf;

    // explicit return
    Return Undefined;
EndFunction
```

### Another example of incorrect code:

```bsl
Функция СуммаСкидки(Знач КорзинаЗаказа)
    Если КорзинаЗаказа.Строки.Количество() > 10 Тогда
        Возврат Скидки.СкидкаНаКрупнуюКорзину(КорзинаЗаказа);
    ИначеЕсли КорзинаЗаказа.ЕстьКартаЛояльности Тогда
        // функция возвращает непредусмотренное значение Неопределено
        Скидки.СкидкаПоКартеЛояльности(КорзинаЗаказа);
    Иначе 
        Возврат 0;
    КонецЕсли;
КонецФункции
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:AllFunctionPathMustHaveReturn-off
// BSLLS:AllFunctionPathMustHaveReturn-on
```

### Parameter for config

```json
"AllFunctionPathMustHaveReturn": {
    "loopsExecutedAtLeastOnce": true,
    "ignoreMissingElseOnExit": false
}
```
