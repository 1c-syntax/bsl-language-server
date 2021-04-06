# "goto" statement should not be used (UsingGoto)

 |     Type     |        Scope        |  Severity  | Activated<br>by default | Minutes<br>to fix |               Tags                |
 |:------------:|:-------------------:|:----------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
 | `Code smell` | `BSL`<br>`OS` | `Critical` |             `Yes`             |           `5`           | `standard`<br>`badpractice` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

`goto` is an unstructured control flow statement. It makes code less readable and maintainable. Structured control flow statements such as `if`, `for`, `while`, `continue` or `break` should be used instead.

## Examples

Bad

```bsl
i = 0;
 ~loop: Message("i = " + i);
 i = i + 1;

 If i < 10 Then

     Goto ~Loop;

 EndIf;
```

Good

```bsl
For Counter = 0 To 10 Do

   Message(StrTemplate("Counter = %1", Counter))

EndDo;
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Стандарт: Использование перейти](https://its.1c.ru/db/v8std/content/547/hdoc/_top/)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingGoto-off
// BSLLS:UsingGoto-on
```

### Parameter for config

```json
"UsingGoto": false
```
