# Reserved parameter names (ReservedParameterNames)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

If a parameter name matches one of a system enumeration's name, then all values of that enumeration will not be available in the local context.
Module code's syntax checking will not detect an error. To prevent this situation, a parameter name should not match all names of system enumerations.
The list of reserved words is set by a regular expression.
Поиск производится без учета регистра символов.

**For example:**

"ВидГруппыФормы|ВидПоляФормы"

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- Source: [Standard: Procedure and Function Parameters (RU)](https://its.1c.ru/db/v8std/content/640/hdoc)
- Source: [Standard: Rules for generating variable names (RU)](https://its.1c.ru/db/v8std#content:454:hdoc)
