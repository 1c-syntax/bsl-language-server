# Lost variable value (LostVariable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

A value is lost when no path after the assignment reads it. Typical cases include repeated assignments, overwrites in every branch and a final assignment after the last read. The analysis follows loop back edges, `Continue` and exception handlers. The companion `UnusedLocalVariable` diagnostic covers a single assignment to an otherwise unused local.

The diagnostic also highlights a loop iterator that reuses a module variable, an enclosing iterator or the variable holding the collection being iterated. The location points to the conflicting loop header.

The default profile enables `LostVariable`. You can disable the diagnostic in your profile settings.

The optional `checkFormShadowing` setting checks method parameters and explicit local `Var` declarations in form modules against properties resolved for the owning form. It is disabled by default.

## Examples

```bsl
StartDate = Order.StartDate; // this value is lost
StartDate = Order.EndDate;
FillPeriod(StartDate, EndDate);
```

Assign the second value to `EndDate`. When fixing a lost assignment, preserve any required side effects of evaluating its right-hand side.

```bsl
For Each Rows In Rows Do // overwrites Rows with an element
    Process(Rows);
EndDo;

For Each Row In Rows Do
    Process(Row);
EndDo;
```

Assignments to form attributes or object properties update their owner. The analysis tracks variable bindings. In modules with loaded metadata the symbol tree separates implicit local variables from properties of their owner.

## Sources

* [Use cases and limitations](https://github.com/1c-syntax/bsl-language-server/issues/1088)
