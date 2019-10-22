# Using parameter «Cancel»

1. In event handlers of object's modules, record sets, forms and etc. using parameter "Cancel" (ПриЗаписи, ОбработкаПроверкиЗаполнения, ТоварыПередНачаломДобавления and etc.) it should not be assigned value "false".
    This is due to the fact, that in code of event handlers the parameter "Cancel" can be set in several consecutive checks (or in several subscriptions on the same event).In this case, by the time the next check is performed, the parameter "Cancel" can already be set to True, and you can set it to False by mistake.In addition when modifying configuration the number of such checks can increase.

#### Incorrect:

```bsl
Процедура ОбработкаПроверкиЗаполнения(Cancel, ПроверяемыеРеквизиты)
  ...
  Cancel = ЕстьОшибкиЗаполнения();
  ...
КонецПроцедуры
```

#### Correct:

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

Reference: [Standart: Working with the "Cancel" option in event handlers(RU)](https://its.1c.ru/db/v8std#content:686:hdoc)
