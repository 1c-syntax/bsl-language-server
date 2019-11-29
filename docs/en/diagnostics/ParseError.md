# Source code parse error (ParseError)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Critical` | `Yes` | `5` | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The error occurs if the source code of the module is written in violation of the language syntax or if the preprocessor instructions are used incorrectly.

Separate grammatical constructions, expressions, as well as declarations and places for calling procedures and functions, should not be split by preprocessor instructions and regions.

## Examples

Incorrect:

```bsl
Процедура Пример1()
  а = 1
#Область ИмяОбласти
    + 2;
#КонецОбласти // statement split
КонецПроцедуры

#Область ИмяОбласти
Процедура Пример2()
    // ...
#КонецОбласти // procedure split
КонецПроцедуры

Если <...> Тогда
    // ...
#Если ВебКлиент Тогда // If-Then block split
Иначе
    // ...
#КонецЕсли
КонецЕсли;

Результат = Пример4(Параметр1, 
#Если Клиент Тогда
  Параметр2, // incorrect function call
#КонецЕсли
  Параметр3);
```

## Reference

- [Standard: #439 Use of compilation and preprocessor directives](https://its.1c.ru/db/v8std#content:439:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ParseError-off
// BSLLS:ParseError-on
```

### Parameter for config

```json
"ParseError": false
```
