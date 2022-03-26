# There are identical sub-expressions to the left and to the right of the "foo" operator (IdenticalExpressions)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The analyzer found a code fragment that most probably has a logic error. There is an operator (, <=, >=, =, <>, AND, OR, -, /) in the program text to the left and to the right of which there are identical subexpressions.

## Examples

```bsl
If Summ <> 0 AND Summ <> 0 Then

    // TODO

EndIf;
```

In this case, the `AND` operator is surrounded by identical subexpressions `Summ <> 0` and it allows us to detect an error made through inattention. The correct code that will not look suspicious to the analyzer looks in the following way:

```bsl
If Summ <> 0 AND SummNDS <> 0 Then

    // TODO

EndIf;
```

OR

```bsl
If Summ <> 0 Then

    // TODO

EndIf;
```
