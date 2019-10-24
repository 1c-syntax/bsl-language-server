# Violating transaction rules for the 'BeginTransaction' method

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Нет` | `10` | `standard` |


## TODO PARAMS

## Description

# Violating transaction rules for the 'BeginTransaction' method

The Start Transaction method must be outside the Attempt-Exclusion block immediately before the Attempt operator;

Source: [Транзакции: правила использования (RU)](https://its.1c.ru/db/v8std/content/783/hdoc/_top/)
