# Magic dates (MagicDate)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Magic date is any date in your code that does not immediately become apparent without being immersed in context.

## Examples

Bad

```bsl
If now < '20151021' Then
    HoverBoardIsReal = Undefined;
EndIf;
```

Good

```bsl
PredictedDate = '20151021'; 
If now < PredictedDate Then
    HoverBoardIsReal = Undefined;
EndIf;
```

Also, a good solution is to use a special method with "telling name" that returns
constant

```bsl
Function DateInventionHover()
    Return '20151021';
EndFunction

If CurrentDate < DateInventionHover() Then
    HoverBoardWillBeInvented = Undefined;
EndIf;
```

## Exceptions

Magic dates used in structures and correspondences are not considered errors, as they are used as keys or values in data structures where the context is clear:

```bsl
// Structure insert - no error
Structure = New Structure;
Structure.Insert("StartDate", '20250101'); // No error
Structure.Insert("EndDate", '20251231'); // No error
Structure.Insert("MaxDate", '39991231235959'); // No error

// Structure constructor - no error
Structure2 = New Structure("StartDate, EndDate", '20250101', '20251231'); // No error

// Direct structure property assignment - no error
StructureWithFields = New Structure("StartDate, EndDate");
StructureWithFields.StartDate = '20250101'; // No error
StructureWithFields.EndDate = '20251231'; // No error

// Fixed structure - no error
FixedStructure = New FixedStructure("Value", '20240101'); // No error

// Correspondence - no error (both key and value)
Correspondence = New Correspondence;
Correspondence.Insert("Code", '20230101'); // No error
Correspondence.Insert('19800101', "Olympics in Moscow"); // No error
```
