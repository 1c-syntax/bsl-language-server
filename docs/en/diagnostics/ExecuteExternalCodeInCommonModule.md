# Executing of external code in a common module on the server (ExecuteExternalCodeInCommonModule)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Security Hotspot` | `BSL` | `Critical` | `Yes` | `15` | `badpractice`<br>`standard`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

При разработке решений следует учитывать, что опасно использование не только непосредственного выполнения кода, написанного в режиме Предприятие, но и алгоритмов, где методами `Выполнить` или `Вычислить` исполняется код в серверных функциях и процедурах.
 Если выполнение произвольного кода необходимо, то такой код должен предварительно располагаться в общем модуле и пройти аудит.

**The restriction does not apply to code executed on the client.**

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [Restrictions on the use of Execute and Eval on the server (RU)](https://its.1c.ru/db/v8std#content:770:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:ExecuteExternalCodeInCommonModule-off // BSLLS:ExecuteExternalCodeInCommonModule-on
```

### Parameter for config

```json
"ExecuteExternalCodeInCommonModule": false
```
