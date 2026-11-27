# Unused local variable (UnusedLocalVariable)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic highlights the name of a variable with no known reads. Passing the variable to a call, returning it, indexing it or accessing a member through it counts as a read. Another assignment alone does not use the previous value. Once a variable has been read, this rule does not check its final assignment or the order of initialization.

The rule skips a variable assigned directly from the global function `Eval` (`Вычислить`). Evaluating the expression can raise an exception even when the result is not needed. This function cannot be called as a standalone statement.

In form, object and record set modules, the rule checks variables local to procedures and functions. Module variables may have external readers, including form bindings. [UnusedParameters](UnusedParameters.md) checks method parameters.

* A method with form context can access form attributes and properties by name. In a method marked `&AtServerNoContext` or `&AtClientAtServerNoContext`, assignment can introduce a local variable with the same name as a form attribute, property or module variable.
* Object modules can access object attributes, standard properties and tabular sections. Record set modules can access properties of the set itself. Dimensions, resources and row attributes belong to individual records.
* An explicit local declaration (`Var`) can shadow an attribute. Reading `Form.Name` or `Row.Name` does not read a separate local variable named `Name`.
* A numeric loop reads its counter, including a variable declared or assigned before the loop. The rule checks whether a `For Each` item is read within the method.

Resolving an unqualified assignment in a method with context requires the owner's metadata from a Designer export or EDT project and the selected platform's syntax helper. Forms also require their content definition. With incomplete inputs, the rule checks cases with definite local scope, such as explicit declarations and assignments in form methods without context. The type of the assigned value can be unknown.

In a partial export, the rule checks the metadata collection directory and the module's location. Standalone BSL files outside an export use the existing checks for unknown module types.

## Example

```bsl
Procedure Test()
    Value = 1; // Diagnostic on Value.
EndProcedure

Procedure UseValue()
    Value = 1;
    Message(value);
EndProcedure

Procedure CheckExpression(Expression)
    Result = Eval(Expression); // No diagnostic.
EndProcedure
```

## Configuration

The rule is enabled by default in `ON` mode. Enable it explicitly in `ONLY` mode. In `EXCEPT` mode it runs unless listed. `ALL` enables every rule and `OFF` disables all rules. The same scope applies in each mode.
