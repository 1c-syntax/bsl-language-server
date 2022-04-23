# All execution paths of a function must have a Return statement (AllFunctionPathMustHaveReturn)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Functions should not have an implicit return. If control reaches the EndFunction line function returns an Undefined value.

As a rule, this is not a normal operation; the programmer must explicitly describe all return values of the function. However, it is quite easy to overlook a situation in which control reaches the EndFunction line and returns an unexpected Undefined value.

This diagnostics checks that all possible paths of the function execution have an explicit Return statement and the function does not return unexpected values.

## Examples

### Incorrect

```bsl
// if the rate is full, but not Tax10 not Tax10 - returns Undefined
// this could be error or planned behavior.
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
Function DiscountAmount(Val OrderBasket)
    If OrderBasket.Rows.Count() > 10 Then
        Return Discounts.DiscountOnBigBasket(OrderBasket);
    ElsIf OrderBasket.IsCustomerCard Then
        // function returns an unintended value is Undefined
        Discounts.DiscountByCustomerCard(OrderBasket);
    Else 
        Return 0;
    EndIf;
EndFunction
```
