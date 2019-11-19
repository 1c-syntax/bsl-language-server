# Using FindByName and FindByCode

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Major` | `Yes` | `2` | `standard`<br/>`badpractice`<br/>`performance` |

<!-- Блоки выше заполняются автоматически, не трогать -->
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
