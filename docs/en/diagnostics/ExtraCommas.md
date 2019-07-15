# Commas without a parameter at the end of a method call

Do not include a comma at the end of a method call without specifying a parameter. It is difficult to understand and does not carry important information.

Bad:

```bsl
Result = Action (P1, P2 ,,);
```

Good:

```bsl
Result = Action (P1, P2);
```

Base:
Required parameters fall under the recommendation of ["Code-writing conventions. Parameters of procedures and functions. Item 7"](https://its.1c.ru/db/v8std#content:640:hdoc).
Not required parameters under the principle of Occam's Razor "Do not multiply entities without need", since the "hanging" comma is not very informative.
