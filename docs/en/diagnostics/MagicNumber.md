# Magic numbers (MagicNumber)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Magic numbers are any number in your code that does not immediately become apparent without being immersed in context.

## Examples

Bad

```bsl
Function GetsTheInterval(Duration)

     Return Duration < 10 * 60 * 60;

End Function
```

Good

```bsl
Function GetsTheInterval (Duration in Seconds)

    MinutesHour     = 60;
    SecondsMinute   = 60;
    SecondsHour     = SecondsMinute * MinutesHour;
    HoursIninterval = 10;
    Return Duration < HoursWininterval * SecondsHour;

End Function
```

## Exceptions

Magic numbers used in structures and correspondences are not considered errors, as they are used as keys or values in data structures where the context is clear:

```bsl
// Structure insert - no error
Structure = New Structure;
Structure.Insert("MyVariable", 20); // No error
Structure.Insert("AnotherVariable", 42); // No error

// Structure constructor - no error
Structure2 = New Structure("Field1, Field2", 5, 15); // No error

// Direct structure property assignment - no error
StructureWithFields = New Structure("MyVariable, AnotherField");
StructureWithFields.MyVariable = 20; // No error
StructureWithFields.AnotherField = 50; // No error

// Fixed structure - no error
FixedStructure = New FixedStructure("Value", 200); // No error

// Correspondence - no error (both key and value)
Correspondence = New Correspondence;
Correspondence.Insert("Code", 123); // No error
Correspondence.Insert(1980, "Olympics in Moscow"); // No error
```
