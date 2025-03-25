# "goto" statement should not be used (UsingGoto)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

`goto` is an unstructured control flow statement. It makes code less readable and maintainable.  
Structured control flow statements such as `if`, `for`, `while`, `continue` or `break` should be used instead.

## Examples

Bad:

```bsl
I = 0;
 ~loop: Message(StrTemplate("i = %1", i));
 i = i + 1;

 If i < 10 Then

     Goto ~Loop;

 EndIf;
```

Good:

```bsl
For Counter = 0 To 10 Do

   Message(StrTemplate("Counter = %1", Counter))

EndDo;
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Standard: Using Goto (RU)](https://its.1c.ru/db/v8std/content/547/hdoc/_top/)
