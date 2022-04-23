# Using parameter "Cancel" (UsingCancelParameter)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In event handlers of object's modules, record sets, forms and etc. using parameter "Cancel" (for example BeforeWrite and etc.) it should not be assigned value "false".  
This is due to the fact, that in code of event handlers the parameter "Cancel" can be set in several consecutive checks (or in several subscriptions on the same event). In this case, by the time the next check is performed, the Cancel parameter may already contain the True value, and you can erroneously reset it back to False.  
In addition, with configuration improvements, the number of these checks may increase.

## Examples

### Incorrect

```bsl
Procedure BeforeWrite(Cancel)
  ...
  Cancel = CheckName();
  ...
EndProcedure
```

### Correct

```bsl
Procedure BeforeWrite(Cancel)
  ...
  If CheckName() Then
   Cancel = True;
  EndIf;
  ...
EndProcedure
```

or

```bsl
Cancel = Cancel or CheckName();
```

## Sources

* [Standart: Working with the "Cancel" option in event handlers (RU)](https://its.1c.ru/db/v8std#content:686:hdoc)
