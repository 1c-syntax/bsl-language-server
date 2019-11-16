# One statement per line

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `Yes` | `2` | `standard`<br/>`design` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Code should follow principles "one statement per line". Several statements are acceptable in case of same-type assignment operators.
For example:

`StartIndex = 0; Index = 0; Result = 0;`

## Sources

* [Standard: Modules texts(RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
