# All execution paths of a function must have a Return statement (AllFunctionPathMustHaveReturn)

<Metadata>

## <Params>

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Functions should not have an implicit return. All returned values must be shown excplicitly.

## Examples
```bsl
Function CalculadeDiscount(ShoppingCart)
    If ShoppingCart.Rows.Count() > 10 Then
        Return Discounts.DiscountForABigCart(ShoppingCart);
    ElIf ShoppingCart.HasLoyaltyCard Then
        // Return is missed unintentionally
        // causing unexpected return of Undefined
        Discounts.DiscountByLoyaltyCard(ShoppingCart);
    Else 
        Return 0;
    EndIf;
EndFunction
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
"AllFunctionPathMustHaveReturn": <DiagnosticConfig>
```
