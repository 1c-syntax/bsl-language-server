# Order of method parameters

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Major` | `Нет` | `30` | `standard`<br/>`design` |


## TODO PARAMS

## Description

# Order of Parameters in method

1. Optional parameters (parameters with default values) should follow mandatory parameters (the ones without default values).

Example:

```bsl
Функция КурсВалютыНаДату(Валюта, Дата = Неопределено) Экспорт
```

Reference: [Standard: Parameters of procedures and functions (RU)](https://its.1c.ru/db/v8std#content:640:hdoc)
