# Limit on the number of property values passed to the structure constructor (NumberOfValuesInStructureConstructor)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When creating an object of type Structure it is not recommended to pass more than 3 property values to the constructor. Instead, it is recommended to use the Insert method or assign values to properties explicitly

## Examples

Incorrect:

```bsl
Parameters  = New Structure(
   "UseParam1,
   |UseParam2,
   |UseParam3,
   |UseParam4,
   |UseParam5,
   |DataAddress,
   |SettingsAddress,
   |UUID,
   |Description",
   True,
   True,
   True,
   True,
   True,
   Current.DataAddress,
   ?(Current.DataAddress <> Undefined,
        Current.DataAddress,
        EmptyAddress()),
   UUID,
   Description));
```

Correct:

```bsl
Parameters  = New Structure;

Parameters.Insert("UseParam1", True);
Parameters.Insert("UseParam2", True);
Parameters.Insert("UseParam3", True);
Parameters.Insert("UseParam4", True);
Parameters.Insert("UseParam5", True);
Parameters.Insert("DataAddress", Current.DataAddress);
Parameters.Insert("SettingsAddress", ?(Current.DataAddress <> Undefined,
                                                                                                                         Current.DataAddress,
                                                                                                                         EmptyAddress));
Parameters.Insert("UUID ", UUID);
Parameters.Insert("Description", Description);
```

## Sources

* [Standard: Using objects of type Structure (RU)](https://its.1c.ru/db/v8std#content:693:hdoc)
