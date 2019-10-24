# Restriction on the use of "FindByDescription" and "FindByCode" methods

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Major` | `Нет` | `2` | `standard`<br/>`badpractice`<br/>`performance` |


## <TODO PARAMS>

## Description

The diagnostic finds use of methods FindByName and FindByCode with hardcoded values.

## Examples

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
