# Method definitions must be placed before the module body operators (CodeBlockBeforeSub)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The structure of the software module *(in general form)* is clearly defined:

- first comes the variable definition block
- then definitions of procedures and functions
- then the module code block

Based on the structure described above, the location of the program code before the definition of methods is unacceptable.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Wrong

```bsl
SomeMethod();
Message("Before methods definition");

Procedure SomeMethod()
// Method body
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Module structure](https://its.1c.ru/db/v8std/content/455/hdoc)
