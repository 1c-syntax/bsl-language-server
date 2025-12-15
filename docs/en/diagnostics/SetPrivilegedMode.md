# Using privileged mode (SetPrivilegedMode)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Diagnostic finds Privileged mode setup code.
For external code, such as code from external reports/data processors, this action may not be safe.

The found sections of the code must be analyzed, a manual audit of the code must be performed for its correctness and safety.

Правило находит вызовы метода The diagnostic finds calls to the `SetPrivilegedMode` method
call to `SetPrivilegedMode(False)` is ignored

Any export procedures and functions that perform any actions on the server with the privileged mode set unconditionally beforehand are potentially dangerous, as this disables checking the access rights of the current user. The export procedures and functions of the client API of the 1C:Enterprise server require special attention.

For example, wrong:

```bsl
Procedure ChangeData(...) Export

SetPrivilegedMode(True); // Disable permission check
// Change data in privileged mode
...
EndProcedure
```

Correct:

```bsl
Procedure ChangeData(...) Export

// Changing data
// (at the same time, if the user does not have enough rights to perform an operation on the data, an exception will be raised)
...
EndProcedure
```

The exception is when the action performed by the procedure must be allowed (or the return value of the function must be available) to absolutely all categories of users.

If you still need to use privileged mode within a method, you must use manual access control using the `VerifyAccessRights` method.

An example of pre-checking before performing actions in privileged mode:

```bsl
Procedure ChangeData(...) Export

VerifyAccessRights(...); // If the user has insufficient rights, an exception will be thrown
SetPrivilegedMode(True); // Disable permission check

// Change data in privileged mode
...
EndProcedure
```

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
    УстановитьПривилегированныйРежим(Истина); // есть замечание

    Значение = Истина;
    УстановитьПривилегированныйРежим(Значение); // есть замечание

    УстановитьПривилегированныйРежим(Ложь); // нет замечания
```

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- Standard: [Using Privileged Mode (RU)](https://its.1c.ru/db/v8std/content/485/hdoc)
- Standard: [Server API Security (RU)](https://its.1c.ru/db/v8std#content:678:hdoc)
- Standard: [Restriction on the execution of "external" code (RU)](https://its.1c.ru/db/v8std/content/669/hdoc)
