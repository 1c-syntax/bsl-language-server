# Incorrect expression line break (IncorrectLineBreak)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Long arithmetic expressions are carried as follows: one entry can contain more than one operand; when wrapping, operation characters are written at the beginning of the line (and not at the end of the previous line); operands on a new line are preceded by standard indentation, or they are aligned to the beginning of the first operand, regardless of the operation signs.

If necessary, parameters of procedures, functions and methods should be transferred as follows:

* parameters are either aligned to the beginning of the first parameter, or preceded by standard indentation;
* closing parenthesis and operator separator ";" are written on the same line as the last parameter;
* the formatting method that offers the auto-formatting function in the configurator is also acceptable

Complex logical conditions in If ... ElseIf ... EndIf should be carried as follows:

* The basis for the newline if the line length is limited to 120 characters;
* logical operators AND, OR are placed at the beginning of a line, and not at the end of the previous line;
* all conditions are preceded by the standard first indent, or they are aligned at the start of work without taking into account the logical operator (it is recommended to use spaces to align expressions relative to the first line).

**Examples of configuring exclusions:**

- If your design standard requires a closing brace and statement separator ";" were written *after* the line containing the last parameter, then you need to change the `listOfIncorrectFirstSymbol` parameter
  - instead of the substring `|\);` (at the end of the setting) you need to write the substring `|\)\s*;\s*\S+`
  - final version `\)|;|,\s*\S+|\)s*;\s*\S+`
  - code example is listed in the examples section

Without the specified setting, the rule will issue notes on the closing bracket and the operator separator ";", located on a separate line

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
AmountDocument = AmountWithoutDiscount +
                 AmountManualDiscounts +
                 AmountAutomaticDiscount;
```

Correct:

```bsl
AmountDocument = AmountWithoutDiscount 
    + AmountManualDiscounts 
    + AmountAutomaticDiscount;
```

or

```bsl
AmountDocument = AmountWithoutDiscount 
                 + AmountManualDiscounts 
                 + AmountAutomaticDiscount;
```

An example of a possible arrangement of parameters and a closing bracket with the operator separator ";"

```bsl
Names = New ValueList;
Names.Add(Name, 
                         Synonym);
```

An example of a possible location of the closing bracket with the operator separator ";" on a separate line:
- without changing the `listOfIncorrectFirstSymbol` parameter (see above), the diagnostics will generate a issue for such expression wrapping.

```bsl
Names = New ValueList;
Names.Add(
    Name, 
    Synonym
);
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Standard: [Wrap expressions (RU)](https://its.1c.ru/db/v8std#content:444:hdoc)
