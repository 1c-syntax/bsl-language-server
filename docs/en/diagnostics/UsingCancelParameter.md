# Using parameter "Cancel" (UsingCancelParameter)

 |     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |               Tags                |
 |:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
 | `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |          `10`           | `standard`<br>`badpractice` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In event handlers of object's modules, record sets, forms and etc. using parameter "Cancel" (ПриЗаписи, ОбработкаПроверкиЗаполнения, ТоварыПередНачаломДобавления and etc.) it should not be assigned value "false". This is due to the fact, that in code of event handlers the parameter "Cancel" can be set in several consecutive checks (or in several subscriptions on the same event).In this case, by the time the next check is performed, the parameter "Cancel" can already be set to True, and you can set it to False by mistake. In addition when modifying configuration the number of such checks can increase. В таком случае к моменту выполнения очередной проверки параметр Отказ уже может заранее содержать значение Истина, и можно ошибочно сбросить его обратно в Ложь.  
Кроме того, при доработках конфигурации на внедрении число этих проверок может увеличиться.

## Examples

### Incorrect

```bsl
Процедура ОбработкаПроверкиЗаполнения(Cancel, ПроверяемыеРеквизиты)
  ...
  Cancel = ЕстьОшибкиЗаполнения();
  ...
EndProcedure
```

### Correct

```bsl
Процедура ОбработкаПроверкиЗаполнения(Cancel, ПроверяемыеРеквизиты)
  ...
  Если ЕстьОшибкиЗаполнения() Тогда
   Cancel = True;
  КонецЕсли;
  ...
КонецПроцедуры
```

or

```bsl
Cancel = Cancel Или ЕстьОшибкиЗаполнения();
```

## Sources

* [[Standart: Working with the "Cancel" option in event handlers(RU)](https://its.1c.ru/db/v8std#content:686:hdoc)](https://its.1c.ru/db/v8std#content:686:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UsingCancelParameter-off
// BSLLS:UsingCancelParameter-on
```

### Parameter for config

```json
"UsingCancelParameter": false
```
