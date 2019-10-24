# Unary Plus sign in string concatenation

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Blocker` | `Нет` | `1` | `suspicious`<br/>`brainoverload` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When concatenating string developer may accidentally write something like "String1 + + String2" in which platform will recognize second "+" as unary and try to convert string to number - and in most cases it will lead to runtime error.
