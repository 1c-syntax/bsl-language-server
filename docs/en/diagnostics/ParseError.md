# Source code parse error (ParseError)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The error occurs if the source code of the module is written in violation of the language syntax or if the preprocessor instructions are used incorrectly.

Separate grammatical constructions, expressions, as well as declarations and places for calling procedures and functions, should not be split by preprocessor instructions and regions.

## Examples

Incorrect:

```bsl
Procedure Example1()
  a = 1
#Region RegionName
    + 2;
#EndRegion // statement split
EndProcedure

#Region RegionName
Procedure Example2()
    // ...
#EndRegion // procedure split
EndProcedure

If <...> Then
    // ...
#If webClient Then // If-Then block split
Else
    // ...
#EndIf
EndIf;

Result = Example4(Parameter1,
#If Client Then
  Parameter2, // incorrect function call
#EndIf
  Parameter3);
```

## Sources

* [Standard: Use of compilation and preprocessor directives (RU)](https://its.1c.ru/db/v8std#content:439:hdoc)
