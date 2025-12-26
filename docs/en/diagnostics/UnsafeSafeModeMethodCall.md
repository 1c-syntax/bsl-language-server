# Unsafe SafeMode method call (UnsafeSafeModeMethodCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In "1C: Enterprise 8.3" the global context method SafeMode() returns the type String, if safe mode was set with the name of the security profile.

Using the SafeMode() method, in which the result is implicitly converted to a Boolean type is unsafe, must be corrected for the code with an explicit comparison of the result with the value False. Thus, with the installed security profile, the code will be executed in the same way as in the safe mode.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Wrong:
```bsl
If SafeMode() Then
     // some logic in safe mode...
EndIf;

If No SafeMode() Then
     // some logic in unsafe mode...
EndIf;
```
Correct:
```bsl
If SafeMode() <> False Then
   // some code
EndIf;
EndIf
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [SafeMode method](https://its.1c.ru/db/metod8dev#content:5293:hdoc:izmenenie_bezopasnyjrezhim)
