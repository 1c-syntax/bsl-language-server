# Using FindByName and FindByCode

- **Scope:** BSL (BSL only)
- **Type:** CODE_SMELL
- **Criticality:** MAJOR

The diagnostic finds use of methods FindByName and FindByCode with hardcoded values.

Example:

```bsl
Должность = Справочники.Должности.НайтиПоНаименованию("Ведущий бухгалтер");
```

or

```bsl
Должность = Справочники.Должности.НайтиПоКоду("00-0000001");
```

Acceptable use:

```bsl
Справочники.Валюты.НайтиПоКоду(ТекущиеДанные.КодВалютыЦифровой);
```

```bsl
Справочники.КлассификаторБанков.НайтиПоКоду(СведенияОБанке.БИК);
```
