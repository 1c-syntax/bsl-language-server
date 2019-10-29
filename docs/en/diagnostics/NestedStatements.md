# Control flow statements should not be nested too deep

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Critical` | `Нет` | `30` | `badpractice`<br/>`brainoverload` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `maxAllowedLevel` | `int` | Максимальный уровень вложенности конструкций | `4` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Nested "If", "For", "ForEach", "While" and "Try" operators are key ingredients for so called "spaghetti-code".

Such code is hard for reading, refactoring and support.

## Examples

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

## Sources

* [RSPEC-134](https://rules.sonarsource.com/java/RSPEC-134)
