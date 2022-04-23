# Unused parameter (UnusedParameters)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Methods shouldn't contain unused parameters.

## Examples

```bsl
Function AddTwoNumbers(Val FirstValue, Val SecondValue, Val UnusedParameter)

    Return FirstValue + SecondValue;

EndFunction
```
