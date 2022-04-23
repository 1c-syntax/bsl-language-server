# Redundant access to an object (RedundantAccessToObject)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
In the forms and modules of objects, it is wrong to refer to the attributes through the property ThisObject. In common modules, it is redundant to refer to methods through their name, except for modules with Cashed.

## Examples
In the ObjectModule of the Document with the attribute `Countractor`, it is wrong to use
```bsl
ThisObject.Contractor = GetContractor();
```

correctly use the props directly
```bsl
Contractor = GetContractor();
```

In the common module `Commons`, the following method call will be incorrect
```bsl
Commons.SendMessage("en = 'Hi!'");
```

correct
```bsl
SendMessage("en = 'Hi!'");
```
