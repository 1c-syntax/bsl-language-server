# Nested constructors with parameters in structure declaration (NestedConstructorsInStructureDeclaration)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is not recommended to use constructors of other objects in the structure constructor if these constructors accept parameters. In particular, in the constructor of one structure it is not recommended to create other structures with the declaration of property values.

## Examples

Incorrect

```bsl
GoodsServer.MakeGoods(
  Object.Products,
  New Structure(
  "CharacteristicsUsed,
  |Type, Variant",
   New Structure("Good", "CharacteristicsUsed"),
   New Structure("Good", "Type"),
   New Structure("Good", "Variant")
  )
 );
```

Correct

```bsl
Parameters = New Structure;
Parameters.Вставить("CharacteristicsUsed", New Structure("Good", "CharacteristicsUsed"));
Parameters.Вставить("Type",                New Structure("Good", "Type"));
Parameters.Вставить("Variant",             New Structure("Good", "Variant"));
GoodsServer.MakeGoods(Object.Products,     Parameters);
```
