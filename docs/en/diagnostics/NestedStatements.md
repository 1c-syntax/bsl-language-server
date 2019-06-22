# Control flow statements should not be nested too deep

Nested "If", "For", "ForEach", "While" and "Try" operators are key ingredients for so called "spaghetti-code".

Such code is hard for reading, refactoring and support.

Incorrect:

```bsl

Если Чтото Тогда                  // Допустимо - уровень = 1
  /* ... */
  Если ЧтоТоЕще Тогда             // Допустимо - уровень = 2
    /* ... */
    Для Ном = 0 По 10 Цикл          // Допустимо - уровень = 3
      /* ... */
      Если ОпятьУсловие Тогда       // Допустимо - уровень = 4, лимит достигнут, но не превышен
        Если ЕщеЧтото Тогда        // Уровень = 5, Превышен лимит
          /* ... */
        КонецЕсли;
        Возврат;
      КонецЕсли;
    КонецЦикла;
  КонецЕсли;
КонецЕсли;

```

## Parameters

- `maxAllowedLevel` - `Integer` - Max nesting level for statements. By deafult - 4.

Reference: [RSPEC-134](https://rules.sonarsource.com/java/RSPEC-134)
