# Cast to number of try catch block (TryNumber)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is incorrect to use exceptions to cast a value to a type. For such operations, it is necessary to use the capabilities of the TypeDescription object.

## Examples

Wrong:

```bsl
Try
 NumberDaysAllowance = Number(Value);
Raise
 NumberDaysAllowance = 0; // default value
EndTry;
```

Correct:

```bsl
TypeDescription = New TypeDescription("Number");
NumberDaysAllowance = TypeDescription.CastValue(Value);
```

## Sources

* [Standard: Catch exceptions in code](https://its.1c.ru/db/v8std#content:499:hdoc)
